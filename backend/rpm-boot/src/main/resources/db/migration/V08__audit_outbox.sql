-- =============================================================================
-- 科研项目管理系统 — 非关键审计事件本地 Outbox
-- 版本：V08
-- 依据：03-权限工作流与审计设计.md §4.3「业务成功但审计写入失败：关键动作回滚业务；
--       非关键动作写入本地 audit_outbox 后异步补写」；任务拆解 T-AUD-002
-- 说明：V01～V07 未包含本表，经人工确认后新增；只追加，补写任务在后续批次接入
-- =============================================================================

CREATE TABLE audit_outbox (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        uuid NOT NULL DEFAULT gen_random_uuid(),
    payload         jsonb NOT NULL,
    trace_id        varchar(64),
    status          varchar(16) NOT NULL DEFAULT 'NEW',
    attempts        integer NOT NULL DEFAULT 0,
    next_retry_at   timestamptz NOT NULL DEFAULT now(),
    last_error      varchar(2048),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_audit_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_audit_outbox_status CHECK (status IN ('NEW','SENT','FAILED','DEAD')),
    CONSTRAINT ck_audit_outbox_attempts CHECK (attempts >= 0)
);

-- 补写任务按状态 + 下次重试时间领取（与 integration_outbox 的 claim 口径一致）
CREATE INDEX idx_audit_outbox_claim ON audit_outbox(status, next_retry_at)
    WHERE status IN ('NEW','FAILED');

COMMENT ON TABLE audit_outbox IS
    '非关键审计事件本地 outbox；与业务写同一事务，由后续补写任务异步写入 audit_log；只追加';

-- 更新时间触发器（与 V01 业务表同一 set_updated_at 函数）
CREATE TRIGGER trg_audit_outbox_updated_at
    BEFORE UPDATE ON audit_outbox
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- 审计：记录本次迁移
-- -----------------------------------------------------------------------------
INSERT INTO audit_log(id, trace_id, actor_id, action, object_type, object_id, reason, ip)
VALUES (
    gen_random_uuid(),
    'flyway-v08',
    NULL,
    'SCHEMA_MIGRATION',
    'DATABASE',
    NULL,
    'Applied V08__audit_outbox.sql: 新增 audit_outbox（T-AUD-002 非关键审计补偿）',
    '127.0.0.1'
);
