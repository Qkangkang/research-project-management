-- =============================================================================
-- 科研项目管理系统 — 数据完整性修复
-- 版本：V04
-- 修复：评审问题 #7, #8, #9, #10, #16, #17, #19, #21, #27
-- 说明：所有对象名均与 V01 基线一致；本脚本只新增、不修改 V01
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 问题 #7: 项目基线 EFFECTIVE 唯一约束
-- 修复：同一项目同一时刻只能有一个生效基线（并发审批/重复回调数据库级兜底）
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX uk_project_baseline_effective
    ON project_baseline(project_id)
    WHERE status = 'EFFECTIVE';

COMMENT ON INDEX uk_project_baseline_effective IS
    '同一项目同一时刻只能有一个EFFECTIVE基线,防止并发审批产生多个生效版本';

-- -----------------------------------------------------------------------------
-- 问题 #21: 成员有效期重叠约束
-- 修复：同一项目、同一用户、同一角色的 ACTIVE 记录有效期不得重叠
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE project_member DROP CONSTRAINT IF EXISTS uk_project_member;

ALTER TABLE project_member
    ADD CONSTRAINT exc_project_member_overlap
    EXCLUDE USING gist (
        project_id WITH =,
        user_id WITH =,
        project_role WITH =,
        tstzrange(valid_from, COALESCE(valid_to, 'infinity'::timestamptz), '[)') WITH &&
    )
    WHERE (status = 'ACTIVE');

COMMENT ON CONSTRAINT exc_project_member_overlap ON project_member IS
    '防止同一项目、用户、角色的活动成员有效期重叠';

-- -----------------------------------------------------------------------------
-- 问题 #8: 验收豁免结构化（ACC-01）
-- 修复：未满足项、豁免人、理由和审批可查可追，不能只是快照 JSON
-- -----------------------------------------------------------------------------
CREATE TABLE acceptance_waiver (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    acceptance_id           uuid NOT NULL REFERENCES acceptance(id) ON DELETE RESTRICT,
    acceptance_version_id   uuid REFERENCES acceptance_version(id) ON DELETE RESTRICT,
    condition_code          varchar(64) NOT NULL,
    condition_description   varchar(1024) NOT NULL,
    applicant_id            uuid NOT NULL REFERENCES iam_user(id),
    waiver_reason           text NOT NULL,
    evidence_file_ids       jsonb NOT NULL DEFAULT '[]'::jsonb,
    status                  varchar(16) NOT NULL DEFAULT 'PENDING',
    approval_instance_id    uuid REFERENCES wf_instance(id),
    approved_by             uuid REFERENCES iam_user(id),
    approved_at             timestamptz,
    rejection_reason        text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_acceptance_waiver_status CHECK (status IN
        ('PENDING','APPROVED','REJECTED','WITHDRAWN')),
    CONSTRAINT ck_acceptance_waiver_approved CHECK (
        (status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR status <> 'APPROVED'),
    CONSTRAINT ck_acceptance_waiver_rejected CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL)
        OR status <> 'REJECTED')
);

CREATE INDEX idx_acceptance_waiver_acceptance ON acceptance_waiver(acceptance_id, status);
CREATE INDEX idx_acceptance_waiver_approval ON acceptance_waiver(approval_instance_id);

COMMENT ON TABLE acceptance_waiver IS
    '验收条件豁免申请与审批;提交验收前校验所有未满足条件均有APPROVED豁免';
COMMENT ON COLUMN acceptance_waiver.acceptance_version_id IS
    '提交时绑定的验收版本;申请阶段(PENDING)可空,提交后固化';

-- 提交验收时固化已批准豁免快照（历史可见，修复 ACC-01 追责要求）
ALTER TABLE acceptance_version
    ADD COLUMN approved_waivers jsonb;

COMMENT ON COLUMN acceptance_version.approved_waivers IS
    '提交时已批准的豁免快照:[{waiver_id, condition_code, approved_by, approved_at}]';

-- -----------------------------------------------------------------------------
-- 问题 #9: 归档材料与条目不可变保护（ACC-07）
-- 修复：验收材料、归档条目只追加；归档包接收后不可改/删
-- -----------------------------------------------------------------------------

-- 1. acceptance_material 只追加
CREATE OR REPLACE FUNCTION prevent_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is forbidden', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_acceptance_material_immutable
    BEFORE UPDATE OR DELETE ON acceptance_material
    FOR EACH ROW EXECUTE FUNCTION prevent_append_only();

-- 2. archive_item 只追加
CREATE TRIGGER trg_archive_item_immutable
    BEFORE UPDATE OR DELETE ON archive_item
    FOR EACH ROW EXECUTE FUNCTION prevent_append_only();

-- 3. archive_package：GENERATING/READY/FAILED 允许有限更新与状态推进；RECEIVED 完全不可改；禁止 DELETE
CREATE OR REPLACE FUNCTION prevent_archive_package_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'archive_package cannot be deleted; use status transition instead';
    END IF;

    -- 已接收的包完全不可改（含条目，条目另有只追加触发器）
    IF OLD.status = 'RECEIVED' THEN
        RAISE EXCEPTION 'archive_package with status RECEIVED is immutable';
    END IF;

    -- 非 RECEIVED 状态：允许生成/就绪/失败/接收的有限流转
    IF NEW.status NOT IN ('GENERATING','READY','FAILED','RECEIVED') THEN
        RAISE EXCEPTION 'illegal archive_package status: %', NEW.status;
    END IF;

    -- 进入 RECEIVED 后 manifest/manifest_hash/file_count/total_bytes 不得再变（由上一分支兜底）
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_archive_package_immutable
    BEFORE UPDATE OR DELETE ON archive_package
    FOR EACH ROW EXECUTE FUNCTION prevent_archive_package_mutation();

-- 4. RECEIVED 包条目追加也禁止（接收后整包冻结）：通过条目触发器 + 应用层保证
COMMENT ON FUNCTION prevent_append_only() IS
    'acceptance_material / archive_item 只追加保护;归档包RECEIVED后由应用层禁止追加条目';

-- -----------------------------------------------------------------------------
-- 问题 #10: 归档包验收关联可空（ACC-08）
-- 修复：终止/不通过项目需处置归档，但可能从未创建验收单
-- -----------------------------------------------------------------------------
ALTER TABLE archive_package
    ALTER COLUMN acceptance_id DROP NOT NULL,
    ALTER COLUMN acceptance_version_id DROP NOT NULL;

ALTER TABLE archive_package
    ADD COLUMN archive_type varchar(16) NOT NULL DEFAULT 'ACCEPTANCE';

ALTER TABLE archive_package
    ADD CONSTRAINT ck_archive_type CHECK (archive_type IN
        ('ACCEPTANCE','TERMINATION','DISPOSAL','CLOSURE'));

-- 验收归档必须绑定验收版本；处置归档不得绑定
ALTER TABLE archive_package
    ADD CONSTRAINT ck_archive_acceptance_binding CHECK (
        (archive_type = 'ACCEPTANCE'
         AND acceptance_id IS NOT NULL
         AND acceptance_version_id IS NOT NULL)
        OR (archive_type IN ('TERMINATION','DISPOSAL','CLOSURE')
            AND acceptance_id IS NULL AND acceptance_version_id IS NULL)
    );

-- 每个项目至多一个终止/处置归档包
CREATE UNIQUE INDEX uk_archive_package_disposal
    ON archive_package(project_id)
    WHERE archive_type IN ('TERMINATION','DISPOSAL');

COMMENT ON COLUMN archive_package.archive_type IS
    'ACCEPTANCE=验收归档, TERMINATION=终止处置归档, DISPOSAL=不通过处置归档, CLOSURE=其他关闭归档';

-- -----------------------------------------------------------------------------
-- 问题 #16: 异常待办状态
-- 修复：审批人为空或业务处理失败进入 EXCEPTION，但原 DDL 无此状态
-- -----------------------------------------------------------------------------

-- 1. 扩展 wf_task.status（保留原值，追加 EXCEPTION）
ALTER TABLE wf_task DROP CONSTRAINT ck_wf_task_status;
ALTER TABLE wf_task
    ADD CONSTRAINT ck_wf_task_status CHECK (status IN
        ('PENDING','PROCESSING','APPROVED','RETURNED','REJECTED',
         'CANCELLED','TIMEOUT','EXCEPTION'));

-- 2. 扩展 wf_task.action（保留原值，追加 EXCEPTION_RETRY）
ALTER TABLE wf_task DROP CONSTRAINT ck_wf_task_action;
ALTER TABLE wf_task
    ADD CONSTRAINT ck_wf_task_action CHECK (action IS NULL OR action IN
        ('APPROVED','RETURNED','REJECTED','CANCELLED','TIMEOUT','EXCEPTION_RETRY'));

-- 3. 允许异常任务无分配人（审批人为空是异常成因之一）
ALTER TABLE wf_task
    ALTER COLUMN assignee_id DROP NOT NULL;

ALTER TABLE wf_task
    ADD COLUMN exception_reason text,
    ADD COLUMN exception_at timestamptz;

-- 4. 扩展 todo_item.status（V01 约束名 ck_todo_status；保留原值，追加 EXCEPTION）
ALTER TABLE todo_item DROP CONSTRAINT ck_todo_status;
ALTER TABLE todo_item
    ADD CONSTRAINT ck_todo_status CHECK (status IN
        ('OPEN','DONE','CANCELLED','TRANSFERRED','EXCEPTION'));

ALTER TABLE todo_item
    ADD COLUMN exception_reason text,
    ADD COLUMN exception_handler_id uuid REFERENCES iam_user(id),
    ADD COLUMN exception_resolved_at timestamptz;

COMMENT ON COLUMN wf_task.exception_reason IS '异常原因:无审批人/规则执行失败/超时等';
COMMENT ON COLUMN todo_item.exception_reason IS '待办异常原因';

-- -----------------------------------------------------------------------------
-- 问题 #19: 支出幂等键位置
-- 修复：幂等键属于"提交动作"，移到版本表；主档仅保留创建幂等键
-- -----------------------------------------------------------------------------

-- 1. 删除主档提交幂等键唯一约束（V01 约束名为 uk_expense_idempotency）
ALTER TABLE expense_request DROP CONSTRAINT uk_expense_idempotency;
ALTER TABLE expense_request ALTER COLUMN idempotency_key DROP NOT NULL;
COMMENT ON COLUMN expense_request.idempotency_key IS
    '废弃字段:保留列兼容历史读取,提交幂等键见 expense_request_version.submit_idempotency_key(V06删除本列)';

-- 2. 版本表增加提交幂等键（V01 约束名为 uk_expense_version_idempotency → V05 无此名,此处创建）
ALTER TABLE expense_request_version
    ADD COLUMN submit_idempotency_key varchar(191);

CREATE UNIQUE INDEX uk_expense_version_submit_idem
    ON expense_request_version(submit_idempotency_key)
    WHERE submit_idempotency_key IS NOT NULL;

COMMENT ON COLUMN expense_request_version.submit_idempotency_key IS
    '提交动作幂等键;每次提交/补正产生新版本和新幂等键,重放返回首次结果';

-- 3. 主档创建幂等键（防重复创建，可空）
ALTER TABLE expense_request
    ADD COLUMN creation_idempotency_key varchar(191);

CREATE UNIQUE INDEX uk_expense_creation_idem
    ON expense_request(creation_idempotency_key)
    WHERE creation_idempotency_key IS NOT NULL;

COMMENT ON COLUMN expense_request.creation_idempotency_key IS
    '创建支出请求幂等键,防止重复创建主档';

-- -----------------------------------------------------------------------------
-- 问题 #27: 审计字段统一
-- 修复：补齐缺失的 created_at/updated_at/created_by/updated_by/version
-- -----------------------------------------------------------------------------

ALTER TABLE application_member
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid,
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;

ALTER TABLE application_review
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid,
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;

ALTER TABLE acceptance_review
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid,
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;

ALTER TABLE archive_item
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid,
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;

ALTER TABLE notification
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid,
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;

ALTER TABLE output_review
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid,
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;

ALTER TABLE acceptance_material
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid,
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;

-- updated_at 自动维护触发器（只追加表不需要：acceptance_material/archive_item）
CREATE TRIGGER trg_application_member_updated_at BEFORE UPDATE ON application_member
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_application_review_updated_at BEFORE UPDATE ON application_review
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_acceptance_review_updated_at BEFORE UPDATE ON acceptance_review
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_notification_updated_at BEFORE UPDATE ON notification
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_output_review_updated_at BEFORE UPDATE ON output_review
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 明确例外清单（不补审计字段，原因如下）：
--   budget_balance : 可重建投影表，来源=fund_event 重放，审计走 fund_event 事件流
--   fund_event     : 只追加事件表，自带 created_at/created_by，禁止 UPDATE 故无需 updated_*
--   audit_log      : append-only 审计日志本身，有 occurred_at/created_at
--   integration_outbox / integration_sync_log : 集成管道表，生命周期由投递任务管理

-- -----------------------------------------------------------------------------
-- 问题 #17: 资金事件投影矩阵（DDL 侧）
-- V01 ck_fund_event_type 已包含全部 9 类事件（RESERVE/RELEASE/APPROVE/PAY/PARTIAL_PAY/
-- CANCEL/REVERSE/REFUND/ADJUST），无需改约束；此处固化口径注释，矩阵见 08 §10
-- -----------------------------------------------------------------------------
COMMENT ON CONSTRAINT ck_fund_event_type ON fund_event IS
    '事件→reserved/approved_unpaid/paid 投影矩阵见 08-评审修复补充接口设计.md §10;
     全量重放与实时投影误差必须为0(TC-FUND-111)';

-- -----------------------------------------------------------------------------
-- 审计：记录本次迁移（audit_log 实际列：trace_id/actor_id/action/object_type/object_id/reason/ip）
-- -----------------------------------------------------------------------------
INSERT INTO audit_log(id, trace_id, actor_id, action, object_type, object_id, reason, ip)
VALUES (
    gen_random_uuid(),
    'flyway-v04',
    NULL,
    'SCHEMA_MIGRATION',
    'DATABASE',
    NULL,
    'Applied V04__fix_data_integrity.sql: 修复评审问题 #7/#8/#9/#10/#16/#17/#19/#21/#27',
    '127.0.0.1'
);
