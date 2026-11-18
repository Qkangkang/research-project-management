-- =============================================================================
-- 科研项目管理系统 — Outbox 与并发修复
-- 版本：V05
-- 修复：评审问题 #11, #18, #20, #23, #24, #25
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 问题 #11: Outbox 投递流程修复
-- 修复：外部 HTTP 不进入数据库事务；短事务 claim → 事务外投递 → 短事务写结果
-- -----------------------------------------------------------------------------

-- 1. 状态扩展：增加 PROCESSING 两阶段领取（V01 约束名 ck_outbox_status，保留 DEAD）
ALTER TABLE integration_outbox DROP CONSTRAINT ck_outbox_status;
ALTER TABLE integration_outbox
    ADD CONSTRAINT ck_outbox_status CHECK (status IN
        ('NEW','PROCESSING','SENT','FAILED','DEAD'));

-- 2. 租约字段（attempts/last_error/next_retry_at V01 已有）
ALTER TABLE integration_outbox
    ADD COLUMN processing_worker varchar(256),   -- worker标识 hostname:pid
    ADD COLUMN lease_expires_at timestamptz;     -- 租约过期时间

CREATE INDEX idx_outbox_claim ON integration_outbox(status, next_retry_at)
    WHERE status IN ('NEW','FAILED');
CREATE INDEX idx_outbox_lease_expired ON integration_outbox(lease_expires_at)
    WHERE status = 'PROCESSING';

COMMENT ON COLUMN integration_outbox.processing_worker IS '领取任务的worker标识,格式: hostname:pid';
COMMENT ON COLUMN integration_outbox.lease_expires_at IS '租约过期时间;超时未完成回收为FAILED可重新领取';

-- 3. claim 短事务（原子领取，提交后立即释放锁）
CREATE OR REPLACE FUNCTION claim_outbox_messages(
    p_worker_id varchar,
    p_batch_size integer DEFAULT 100,
    p_lease_seconds integer DEFAULT 300
)
RETURNS SETOF integration_outbox
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    UPDATE integration_outbox
    SET status = 'PROCESSING',
        processing_worker = p_worker_id,
        lease_expires_at = now() + make_interval(secs => p_lease_seconds),
        updated_at = now()
    WHERE id IN (
        SELECT id FROM integration_outbox
        WHERE (status IN ('NEW','FAILED') AND next_retry_at <= now())
           OR (status = 'PROCESSING' AND lease_expires_at < now())
        ORDER BY created_at
        FOR UPDATE SKIP LOCKED
        LIMIT p_batch_size
    )
    RETURNING *;
END;
$$;

-- 4. 结果短事务（写 SENT/FAILED；attempts 为 V01 字段）
CREATE OR REPLACE FUNCTION complete_outbox_message(
    p_message_id uuid,
    p_success boolean,
    p_error_message varchar DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_success THEN
        UPDATE integration_outbox
        SET status = 'SENT',
            sent_at = now(),
            updated_at = now(),
            processing_worker = NULL,
            lease_expires_at = NULL
        WHERE id = p_message_id AND status = 'PROCESSING';
    ELSE
        UPDATE integration_outbox
        SET status = CASE WHEN attempts + 1 >= 8 THEN 'DEAD' ELSE 'FAILED' END,
            attempts = attempts + 1,
            next_retry_at = now() + make_interval(secs =>
                (ARRAY[30,120,600,1800,7200,21600])[LEAST(attempts + 1, 6)]),
            last_error = p_error_message,
            updated_at = now(),
            processing_worker = NULL,
            lease_expires_at = NULL
        WHERE id = p_message_id AND status = 'PROCESSING';
    END IF;
END;
$$;

-- 5. 租约超时回收（worker 崩溃恢复）
CREATE OR REPLACE FUNCTION reclaim_expired_outbox()
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
    v_reclaimed integer;
BEGIN
    UPDATE integration_outbox
    SET status = CASE WHEN attempts + 1 >= 8 THEN 'DEAD' ELSE 'FAILED' END,
        attempts = attempts + 1,
        next_retry_at = now() + interval '30 seconds',
        last_error = 'Lease expired without completion (worker crash?)',
        processing_worker = NULL,
        lease_expires_at = NULL,
        updated_at = now()
    WHERE status = 'PROCESSING'
      AND lease_expires_at < now();

    GET DIAGNOSTICS v_reclaimed = ROW_COUNT;
    RETURN v_reclaimed;
END;
$$;

COMMENT ON FUNCTION claim_outbox_messages IS
    '短事务claim:领取与置PROCESSING同事务,提交后释放锁;HTTP投递在事务外执行';

-- -----------------------------------------------------------------------------
-- 问题 #18: 支付回调并发幂等（实现规则，应用层执行）
-- payment_record.external_payment_no / fund_event.idempotency_key V01 已有唯一约束
-- -----------------------------------------------------------------------------
COMMENT ON COLUMN payment_record.external_payment_no IS
    '外部支付单号,全局唯一;事务内 INSERT ... ON CONFLICT (external_payment_no) DO NOTHING RETURNING;
     未返回行=并发重复,同事务查原记录,载荷一致返回首次结果(200),不一致返回 RPM-INT-004';
COMMENT ON COLUMN fund_event.idempotency_key IS
    '事件幂等键,格式: {expenseRequestId}:{action}:{externalPaymentNo};
     唯一冲突捕获后查询原事件返回首次结果;详见 08-评审修复补充接口设计.md §11';

-- -----------------------------------------------------------------------------
-- 问题 #20: 通知与待办幂等
-- 修复：两者各自唯一键 + 同事务原子生成，防止部分成功
-- -----------------------------------------------------------------------------

-- 1. notification 幂等索引（V01 未建；06 §3.1 引用的口径）
CREATE UNIQUE INDEX uk_notification_dedupe
    ON notification(user_id, type, biz_id, biz_date);

-- 2. todo_item 业务幂等键
ALTER TABLE todo_item
    ADD COLUMN business_idempotency_key varchar(256);

CREATE UNIQUE INDEX uk_todo_item_idempotency
    ON todo_item(business_idempotency_key)
    WHERE business_idempotency_key IS NOT NULL;

COMMENT ON COLUMN todo_item.business_idempotency_key IS
    '业务幂等键 {userId}:{bizType}:{bizId}:{bizVersionId|NULL}:{dueAt|NULL};
     与 notification 唯一键配合,重试时两者均幂等 upsert';

-- 3. 原子生成函数（单事务；列名与 V01 一致）
CREATE OR REPLACE FUNCTION create_notification_and_todo(
    p_user_id uuid,
    p_notification_type varchar,
    p_biz_type varchar,
    p_biz_id uuid,
    p_biz_version_id uuid,
    p_title varchar,
    p_content varchar,
    p_due_at timestamptz
)
RETURNS TABLE(notification_id uuid, todo_id uuid)
LANGUAGE plpgsql
AS $$
DECLARE
    v_notification_id uuid;
    v_todo_id uuid;
    v_idem_key varchar;
BEGIN
    -- 通知（唯一键 user_id+type+biz_id+biz_date 命中则幂等返回原记录）
    INSERT INTO notification(id, user_id, type, biz_type, biz_id, title, content)
    VALUES (gen_random_uuid(), p_user_id, p_notification_type,
            p_biz_type, p_biz_id, p_title, p_content)
    ON CONFLICT (user_id, type, biz_id, biz_date) DO UPDATE
        SET updated_at = now()
    RETURNING id INTO v_notification_id;

    -- 待办幂等键
    v_idem_key := p_user_id || ':' || p_biz_type || ':' || p_biz_id || ':'
                  || COALESCE(p_biz_version_id::text, 'NULL') || ':'
                  || COALESCE(p_due_at::text, 'NULL');

    INSERT INTO todo_item(id, user_id, biz_type, biz_id, biz_version_id,
                          title, status, due_at, business_idempotency_key)
    VALUES (gen_random_uuid(), p_user_id, p_biz_type, p_biz_id, p_biz_version_id,
            p_title, 'OPEN', p_due_at, v_idem_key)
    ON CONFLICT (business_idempotency_key) WHERE business_idempotency_key IS NOT NULL
        DO UPDATE SET updated_at = now()
    RETURNING id INTO v_todo_id;

    RETURN QUERY SELECT v_notification_id, v_todo_id;
END;
$$;

COMMENT ON FUNCTION create_notification_and_todo IS
    '同一事务生成通知+待办;部分成功时重试整体幂等(问题 #20)';

-- -----------------------------------------------------------------------------
-- 问题 #23: 报告迟报语义统一
-- 修复：主档状态删除 LATE；迟报事实唯一来源 = 版本 late_flag
-- -----------------------------------------------------------------------------
ALTER TABLE progress_report DROP CONSTRAINT ck_progress_report_status;
ALTER TABLE progress_report
    ADD CONSTRAINT ck_progress_report_status CHECK (status IN
        ('DRAFT','SUBMITTED','RETURNED','APPROVED','CLOSED'));

COMMENT ON COLUMN progress_report.status IS
    '主档状态,不含LATE;迟报通过 progress_report_version.late_flag 标识(修复 #23)';
COMMENT ON COLUMN progress_report_version.late_flag IS
    '是否迟报,提交时按 due_date 判定;查询/筛选/统计统一使用此字段';

-- -----------------------------------------------------------------------------
-- 问题 #24: HTTP 状态码全局约定（登记到受控字典；完整规则见 08 §1）
-- V01 data_dictionary 列: dict_type/dict_code/dict_label/sort_order
-- -----------------------------------------------------------------------------
INSERT INTO data_dictionary(id, dict_type, dict_code, dict_label, sort_order)
VALUES
    (gen_random_uuid(), 'HTTP_STATUS_CONVENTION', '200',
     '同步成功:查询/同步写/幂等重放返回首次结果/业务警告(如RPM-OUT-001)', 1),
    (gen_random_uuid(), 'HTTP_STATUS_CONVENTION', '201',
     '创建成功:同步创建资源(草稿/上传完成)', 2),
    (gen_random_uuid(), 'HTTP_STATUS_CONVENTION', '202',
     '异步受理:申报/支出/验收提交、导出创建;响应体含instanceId+pollUrl', 3),
    (gen_random_uuid(), 'HTTP_STATUS_CONVENTION', '204',
     '成功无返回体:删除草稿/标记已读', 4),
    (gen_random_uuid(), 'HTTP_STATUS_CONVENTION', '409',
     '版本冲突/重复操作;幂等重放首次结果一律200', 5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 问题 #25: 对账差异责任人与忽略规则（FUND-09）
-- 修复：owner_id 必填；IGNORED 改为 CLOSED_WITH_REASON（原因+操作人+时间必填）
-- -----------------------------------------------------------------------------
ALTER TABLE reconciliation_diff
    ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE reconciliation_diff DROP CONSTRAINT ck_recon_diff_status;
ALTER TABLE reconciliation_diff
    ADD CONSTRAINT ck_recon_diff_status CHECK (status IN
        ('OPEN','PROCESSING','RESOLVED','CLOSED_WITH_REASON'));
-- 语义映射:原 CLOSED → RESOLVED;原 IGNORED → CLOSED_WITH_REASON(需原因)

ALTER TABLE reconciliation_diff
    ADD COLUMN close_reason varchar(2048),
    ADD COLUMN approval_required boolean NOT NULL DEFAULT false;

ALTER TABLE reconciliation_diff
    ADD CONSTRAINT ck_recon_diff_closed CHECK (
        (status = 'CLOSED_WITH_REASON'
         AND close_reason IS NOT NULL
         AND closed_by IS NOT NULL
         AND closed_at IS NOT NULL)
        OR status <> 'CLOSED_WITH_REASON'
    );

COMMENT ON COLUMN reconciliation_diff.owner_id IS '责任人,创建时必填';
COMMENT ON COLUMN reconciliation_diff.close_reason IS
    '带理由关闭的原因,CLOSED_WITH_REASON 时必填;处理说明继续使用 resolution 字段';
COMMENT ON COLUMN reconciliation_diff.approval_required IS
    '是否需审批关闭(金额超阈值或特定差异类型),阈值见 system_config[fund.recon.close-threshold]';

-- -----------------------------------------------------------------------------
-- 审计：记录本次迁移
-- -----------------------------------------------------------------------------
INSERT INTO audit_log(id, trace_id, actor_id, action, object_type, object_id, reason, ip)
VALUES (
    gen_random_uuid(),
    'flyway-v05',
    NULL,
    'SCHEMA_MIGRATION',
    'DATABASE',
    NULL,
    'Applied V05__fix_outbox_and_concurrency.sql: 修复评审问题 #11/#18/#20/#23/#24/#25',
    '127.0.0.1'
);
