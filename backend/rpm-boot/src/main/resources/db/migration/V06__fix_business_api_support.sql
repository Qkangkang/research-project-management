-- =============================================================================
-- 科研项目管理系统 — 业务接口支撑表
-- 版本：V06
-- 修复：评审问题 #4, #8, #13, #14, #27 及 V04 遗留清理
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0a. application.status 扩展（问题 #22 / APPLY-03）
-- NOT_ACCEPTED = 不予受理终态（区别于 REJECTED 立项不批准）
-- 超期关闭沿用既有 CLOSED（TC-APP-038/039），以关闭原因区分草稿过期/补正超期
-- -----------------------------------------------------------------------------
ALTER TABLE application DROP CONSTRAINT ck_application_status;
ALTER TABLE application
    ADD CONSTRAINT ck_application_status CHECK (status IN
        ('DRAFT','IN_REVIEW','SUPPLEMENT','APPROVED','REJECTED',
         'WITHDRAWN','CLOSED','NOT_ACCEPTED'));

-- -----------------------------------------------------------------------------
-- 0. wf_instance.biz_type 扩展
-- 修复：延期审批(#14)、豁免审批(#8)、特批(#13)、档案更正(ACC-06)需要实例类型
-- -----------------------------------------------------------------------------
ALTER TABLE wf_instance DROP CONSTRAINT IF EXISTS ck_wf_instance_biz_type;
ALTER TABLE wf_instance
    ADD CONSTRAINT ck_wf_instance_biz_type CHECK (biz_type IN
        ('APPLICATION','EXPENSE','CHANGE','ACCEPTANCE','HANDOVER',
         'MILESTONE_DELAY',      -- 里程碑延期审批 (EXEC-02)
         'WAIVER',               -- 验收条件豁免审批 (ACC-01)
         'SPECIAL_APPROVAL',     -- 支出特批审批 (FUND-04)
         'ARCHIVE_CORRECTION')); -- 档案更正审批 (ACC-06)

-- -----------------------------------------------------------------------------
-- 1. V04 遗留清理: 支出主档幂等键合并
-- V04 已新增 creation_idempotency_key,删除旧的 idempotency_key 列
-- -----------------------------------------------------------------------------
ALTER TABLE expense_request DROP COLUMN IF EXISTS idempotency_key;

-- -----------------------------------------------------------------------------
-- 2. 里程碑延期申请 (问题 #14 / EXEC-02)
-- -----------------------------------------------------------------------------
CREATE TABLE milestone_delay_request (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id            uuid NOT NULL REFERENCES milestone(id) ON DELETE RESTRICT,
    project_id              uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    baseline_id             uuid NOT NULL REFERENCES project_baseline(id) ON DELETE RESTRICT,
    original_plan_date      date NOT NULL,  -- 申请时快照,不可变
    requested_plan_date     date NOT NULL,  -- 申请延期到
    delay_days              integer NOT NULL,
    reason                  varchar(2048) NOT NULL,
    evidence_file_ids       jsonb,
    requested_by            uuid NOT NULL REFERENCES iam_user(id),
    status                  varchar(16) NOT NULL DEFAULT 'PENDING',
    wf_instance_id          uuid REFERENCES wf_instance(id),
    decision                varchar(16),
    decision_comment        varchar(2048),
    decided_by              uuid REFERENCES iam_user(id),
    decided_at              timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_delay_days CHECK (delay_days > 0),
    CONSTRAINT ck_delay_dates CHECK (requested_plan_date > original_plan_date),
    CONSTRAINT ck_delay_status CHECK (status IN
        ('PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_delay_decision CHECK (decision IN
        ('APPROVE','REJECT','CANCEL') OR decision IS NULL),
    CONSTRAINT ck_delay_decided CHECK (
        (status IN ('APPROVED','REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
        OR status IN ('PENDING','CANCELLED'))
);

CREATE INDEX idx_milestone_delay_milestone ON milestone_delay_request(milestone_id, status);
CREATE INDEX idx_milestone_delay_project ON milestone_delay_request(project_id, status);

COMMENT ON TABLE milestone_delay_request IS
    '里程碑延期申请;批准后由领域服务在同一事务内更新 milestone.current_plan_date,
     plan_date(原计划)永不变更;仅 EXECUTING 状态的项目可申请';

-- -----------------------------------------------------------------------------
-- 3. 支出特批 (问题 #13 / FUND-04)
-- -----------------------------------------------------------------------------
CREATE TABLE expense_special_approval (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_request_id      uuid NOT NULL REFERENCES expense_request(id) ON DELETE RESTRICT,
    expense_version_id      uuid NOT NULL REFERENCES expense_request_version(id) ON DELETE RESTRICT,
    special_type            varchar(32) NOT NULL,       -- 超预算/无预算/超限额
    budget_version_id       uuid REFERENCES budget_version(id),
    budget_line_id          uuid REFERENCES budget_line(id),
    over_amount             numeric(18,2) NOT NULL,     -- 超出可用额度的金额
    reason                  text NOT NULL,              -- 特批理由,必填
    applicant_id            uuid NOT NULL REFERENCES iam_user(id),
    applicant_name          varchar(128) NOT NULL,
    evidence_file_ids       jsonb,
    status                  varchar(16) NOT NULL DEFAULT 'PENDING',
    wf_instance_id          uuid REFERENCES wf_instance(id),
    approver_id             uuid REFERENCES iam_user(id), -- 特批审批人(独立权限)
    approved_at             timestamptz,
    rejection_reason        text,
    valid_until             date,                        -- 特批有效期
    adjustment_version_id   uuid REFERENCES budget_version(id), -- 特批通过生成的预算调整版本
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_special_type CHECK (special_type IN
        ('OVER_BUDGET','NO_BUDGET','OVER_LIMIT')),
    CONSTRAINT ck_special_amount CHECK (over_amount > 0),
    CONSTRAINT ck_special_status CHECK (status IN
        ('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED')),
    CONSTRAINT ck_special_approved CHECK (
        (status = 'APPROVED' AND approver_id IS NOT NULL AND approved_at IS NOT NULL AND valid_until IS NOT NULL)
        OR status != 'APPROVED'),
    CONSTRAINT ck_special_rejected CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL)
        OR status != 'REJECTED'),
    -- 职责分离: 特批审批人不得是申请人或支出经办人
    CONSTRAINT ck_special_separation CHECK (approver_id IS NULL OR approver_id != applicant_id)
);

CREATE INDEX idx_expense_special_expense ON expense_special_approval(expense_request_id, status);
CREATE INDEX idx_expense_special_wf ON expense_special_approval(wf_instance_id);

COMMENT ON TABLE expense_special_approval IS
    '支出特批申请与审批;使用独立权限点 fund:expense:special-approve;
     PI 与特批审批人不得为同一人;特批通过后在同一事务生成预算调整版本并受控占用';

-- 特批金额上限配置 (存入 system_config,此处注册键名说明)
INSERT INTO system_config(id, config_key, config_value, description)
VALUES (
    gen_random_uuid(),
    'fund.special-approval.max-amount',
    '{"value": 500000.00}'::jsonb,
    '单笔特批金额上限,超过此金额需院领导加签;单位:元'
)
ON CONFLICT (config_key) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. 文件分片上传会话 (问题 #4 / rpm-file 通用文件 API)
-- -----------------------------------------------------------------------------
CREATE TABLE file_upload_session (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name           varchar(512) NOT NULL,
    mime_type           varchar(128) NOT NULL,
    size_bytes          bigint NOT NULL,
    chunk_size          bigint NOT NULL,
    total_chunks        integer NOT NULL,
    uploaded_chunks     integer NOT NULL DEFAULT 0,
    biz_type            varchar(32),        -- 预关联业务类型(可空,完成后绑定)
    purpose             varchar(64),        -- 用途: APPLICATION_ATTACHMENT/EXPENSE_VOUCHER/...
    upload_id           varchar(256) NOT NULL, -- S3 multipart uploadId
    initiated_by        uuid NOT NULL REFERENCES iam_user(id),
    status              varchar(16) NOT NULL DEFAULT 'UPLOADING',
    file_id             uuid REFERENCES file_object(id), -- 完成后生成的文件对象
    completed_at        timestamptz,
    aborted_at          timestamptz,
    expires_at          timestamptz NOT NULL,  -- 会话过期时间,过期未完成自动中止
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_upload_chunk CHECK (chunk_size > 0 AND total_chunks > 0),
    CONSTRAINT ck_upload_uploaded CHECK (uploaded_chunks >= 0 AND uploaded_chunks <= total_chunks),
    CONSTRAINT ck_upload_status CHECK (status IN
        ('UPLOADING','COMPLETING','COMPLETED','ABORTED','EXPIRED')),
    CONSTRAINT ck_upload_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_file_upload_session_user ON file_upload_session(initiated_by, status);
CREATE INDEX idx_file_upload_session_expiry ON file_upload_session(expires_at)
    WHERE status = 'UPLOADING';

COMMENT ON TABLE file_upload_session IS
    '大文件分片上传会话;小文件(<=5MB)可走单次直传接口不经会话;
     会话过期(默认48小时)由 FileSessionCleanupJob 中止并清理';

-- 已上传分片记录(幂等重传依据)
CREATE TABLE file_upload_chunk (
    session_id          uuid NOT NULL REFERENCES file_upload_session(id) ON DELETE CASCADE,
    chunk_no            integer NOT NULL,
    etag                varchar(256) NOT NULL,
    uploaded_at         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, chunk_no)
);

-- 文件下载签名记录(撤销授权后旧签名失效的依据)
CREATE TABLE file_download_grant (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id             uuid NOT NULL REFERENCES file_object(id) ON DELETE RESTRICT,
    granted_to          uuid NOT NULL REFERENCES iam_user(id),
    grant_token         varchar(128) NOT NULL,
    expires_at          timestamptz NOT NULL,      -- 最长5分钟
    revoked_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_file_download_grant UNIQUE (grant_token),
    CONSTRAINT ck_grant_validity CHECK (expires_at > created_at)
);

CREATE INDEX idx_file_download_grant_file ON file_download_grant(file_id, granted_to);

COMMENT ON TABLE file_download_grant IS
    '短期下载签名记录;有效期<=5分钟;业务撤销授权时批量 REVOKE,校验时拒绝已撤销token';

-- -----------------------------------------------------------------------------
-- 5. 档案更正申请 (ACC-06 / 问题 #26 关联)
-- -----------------------------------------------------------------------------
CREATE TABLE archive_correction_request (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    archive_package_id  uuid NOT NULL REFERENCES archive_package(id) ON DELETE RESTRICT,
    archive_item_id     uuid REFERENCES archive_item(id),
    correction_type     varchar(32) NOT NULL,   -- ADD_APPEND(追加)/DESC_CORRECTION(目录更正)
    reason              text NOT NULL,
    detail              jsonb NOT NULL,
    requested_by        uuid NOT NULL REFERENCES iam_user(id),
    status              varchar(16) NOT NULL DEFAULT 'PENDING',
    wf_instance_id      uuid REFERENCES wf_instance(id),
    decided_by          uuid REFERENCES iam_user(id),
    decided_at          timestamptz,
    rejection_reason    text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_archive_correction_type CHECK (correction_type IN
        ('ADD_APPEND','DESC_CORRECTION')),
    CONSTRAINT ck_archive_correction_status CHECK (status IN
        ('PENDING','APPROVED','REJECTED','CANCELLED')),
    -- 只允许追加和目录性更正,禁止替换已归档证据
    CONSTRAINT ck_archive_correction_scope CHECK (
        correction_type = 'ADD_APPEND'
        OR (correction_type = 'DESC_CORRECTION' AND archive_item_id IS NOT NULL))
);

CREATE INDEX idx_archive_correction_package ON archive_correction_request(archive_package_id, status);

COMMENT ON TABLE archive_correction_request IS
    '档案更正申请;仅支持追加条目或目录描述更正,禁止修改/删除已归档证据原文';

-- -----------------------------------------------------------------------------
-- 审计：记录本次迁移
-- -----------------------------------------------------------------------------
INSERT INTO audit_log(id, trace_id, actor_id, action, object_type, object_id, reason, ip)
VALUES (
    gen_random_uuid(),
    'flyway-v06',
    NULL,
    'SCHEMA_MIGRATION',
    'DATABASE',
    NULL,
    'Applied V06__fix_business_api_support.sql: 修复评审问题 #4/#8/#13/#14/ACC-06',
    '127.0.0.1'
);
