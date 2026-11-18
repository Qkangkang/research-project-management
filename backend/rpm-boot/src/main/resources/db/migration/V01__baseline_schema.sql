-- =============================================================================
-- 科研项目管理系统 — 基线数据库结构
-- 数据库：PostgreSQL 16.x
-- 迁移工具：Flyway
-- 版本：V01
-- 说明：
--   1. 本脚本是 02-数据模型与数据库设计.md 的可执行基线。
--   2. 金额统一 numeric(18,2)，时间点统一 timestamptz，业务日期统一 date。
--   3. 版本化对象的内容列禁止覆盖；资金事件与审计日志只追加。
--   4. 业务编号由应用层 NumberGenerator 使用序列生成，数据库只保证唯一。
--   5. 若院方 PostgreSQL 不允许 pg_trgm，可移除本文件末尾的 3 个 trgm 索引。
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SEQUENCE IF NOT EXISTS seq_application_no START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_project_no START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_expense_request_no START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_fund_event_no START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_payment_import_batch_no START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_reconciliation_batch_no START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- -----------------------------------------------------------------------------
-- 1. 组织、身份、权限
-- -----------------------------------------------------------------------------

CREATE TABLE org_unit (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id       uuid REFERENCES org_unit(id) ON DELETE RESTRICT,
    code            varchar(64) NOT NULL,
    name            varchar(256) NOT NULL,
    unit_type       varchar(32) NOT NULL,
    path            varchar(1024) NOT NULL DEFAULT '',
    sort_order      integer NOT NULL DEFAULT 0,
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_org_unit_code UNIQUE (code),
    CONSTRAINT ck_org_unit_type CHECK (unit_type IN
        ('UNIVERSITY','DEPARTMENT','INSTITUTE','GROUP','OTHER')),
    CONSTRAINT ck_org_unit_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE iam_user (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_no           varchar(64) NOT NULL,
    display_name          varchar(128) NOT NULL,
    dept_id               uuid NOT NULL REFERENCES org_unit(id) ON DELETE RESTRICT,
    email                 varchar(256),
    mobile_masked         varchar(32),
    mobile_encrypted      text,
    id_card_masked        varchar(32),
    id_card_encrypted     text,
    sso_subject           varchar(191),
    status                varchar(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_at         timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    version               integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_user_employee_no UNIQUE (employee_no),
    CONSTRAINT uk_iam_user_sso_subject UNIQUE (sso_subject),
    CONSTRAINT ck_iam_user_status CHECK (status IN
        ('ACTIVE','LEAVED','DISABLED','LOCKED'))
);

CREATE TABLE iam_role (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            varchar(64) NOT NULL,
    name            varchar(128) NOT NULL,
    scope_type      varchar(32) NOT NULL,
    description     varchar(512),
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_role_code UNIQUE (code),
    CONSTRAINT ck_iam_role_scope CHECK (scope_type IN
        ('SELF','PROJECT','DEPT','DEPT_TREE','ALL','CUSTOM')),
    CONSTRAINT ck_iam_role_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE iam_permission (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            varchar(128) NOT NULL,
    name            varchar(128) NOT NULL,
    module          varchar(64) NOT NULL,
    description     varchar(512),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_iam_permission_code UNIQUE (code)
);

CREATE TABLE iam_role_permission (
    role_id         uuid NOT NULL REFERENCES iam_role(id) ON DELETE CASCADE,
    permission_id   uuid NOT NULL REFERENCES iam_permission(id) ON DELETE CASCADE,
    granted_by      uuid REFERENCES iam_user(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE iam_user_role (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
    role_id         uuid NOT NULL REFERENCES iam_role(id) ON DELETE RESTRICT,
    org_id          uuid REFERENCES org_unit(id) ON DELETE RESTRICT,
    valid_from      timestamptz NOT NULL DEFAULT now(),
    valid_to        timestamptz,
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_iam_user_role_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
    CONSTRAINT ck_iam_user_role_valid CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE TABLE iam_temp_grant (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    grantee_id      uuid NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
    resource_type   varchar(32) NOT NULL,
    resource_id     uuid NOT NULL,
    permission_code varchar(128) NOT NULL,
    reason          varchar(1024) NOT NULL,
    valid_from      timestamptz NOT NULL,
    valid_to        timestamptz NOT NULL,
    granted_by      uuid NOT NULL REFERENCES iam_user(id),
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_temp_grant_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
    CONSTRAINT ck_temp_grant_valid CHECK (valid_to > valid_from)
);

CREATE TABLE org_membership (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
    org_id          uuid NOT NULL REFERENCES org_unit(id) ON DELETE RESTRICT,
    position_name   varchar(128),
    is_primary      boolean NOT NULL DEFAULT false,
    valid_from      timestamptz NOT NULL DEFAULT now(),
    valid_to        timestamptz,
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_org_membership_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_org_membership_valid CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE UNIQUE INDEX uk_org_membership_primary
    ON org_membership(user_id)
    WHERE is_primary = true AND status = 'ACTIVE';

CREATE TABLE coi_declaration (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    biz_type            varchar(32) NOT NULL,
    biz_id              uuid NOT NULL,
    expert_id           uuid NOT NULL REFERENCES iam_user(id),
    related_user_id     uuid NOT NULL REFERENCES iam_user(id),
    relation_type       varchar(32) NOT NULL,
    description         varchar(1024),
    status              varchar(16) NOT NULL DEFAULT 'DECLARED',
    declared_at         timestamptz NOT NULL DEFAULT now(),
    reviewed_by         uuid REFERENCES iam_user(id),
    reviewed_at         timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_coi_biz_expert UNIQUE (biz_type, biz_id, expert_id, related_user_id),
    CONSTRAINT ck_coi_relation_type CHECK (relation_type IN
        ('DEPT','COLLABORATION','FAMILY','FINANCIAL','OTHER')),
    CONSTRAINT ck_coi_status CHECK (status IN
        ('DECLARED','CONFIRMED','EXEMPTED','CLOSED'))
);

-- -----------------------------------------------------------------------------
-- 2. 文件与系统配置
-- -----------------------------------------------------------------------------

CREATE TABLE file_object (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket          varchar(128) NOT NULL,
    object_key      varchar(512) NOT NULL,
    original_name   varchar(512) NOT NULL,
    mime_type       varchar(128) NOT NULL,
    size_bytes      bigint NOT NULL DEFAULT 0,
    sha256          char(64),
    status          varchar(16) NOT NULL DEFAULT 'UPLOADING',
    scan_status     varchar(16) NOT NULL DEFAULT 'PENDING',
    uploaded_by     uuid REFERENCES iam_user(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_file_object_key UNIQUE (object_key),
    CONSTRAINT ck_file_object_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_file_object_sha CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_file_object_status CHECK (status IN
        ('UPLOADING','QUARANTINED','AVAILABLE','REJECTED','DELETED')),
    CONSTRAINT ck_file_object_scan CHECK (scan_status IN
        ('PENDING','PASSED','FAILED','SKIPPED'))
);

CREATE TABLE file_ref (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id         uuid NOT NULL REFERENCES file_object(id) ON DELETE RESTRICT,
    biz_type        varchar(32) NOT NULL,
    biz_id          uuid NOT NULL,
    purpose         varchar(64) NOT NULL,
    access_level    varchar(16) NOT NULL DEFAULT 'PROJECT',
    created_by      uuid REFERENCES iam_user(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_file_ref_biz UNIQUE (file_id, biz_type, biz_id, purpose),
    CONSTRAINT ck_file_ref_access CHECK (access_level IN
        ('INTERNAL','PROJECT','RESTRICTED','PUBLIC'))
);

CREATE TABLE system_config (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key      varchar(128) NOT NULL,
    config_value    jsonb NOT NULL DEFAULT '{}'::jsonb,
    value_type      varchar(32) NOT NULL DEFAULT 'JSON',
    description     varchar(512),
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_system_config_key UNIQUE (config_key),
    CONSTRAINT ck_system_config_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE data_dictionary (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    dict_type       varchar(64) NOT NULL,
    dict_code       varchar(64) NOT NULL,
    dict_label      varchar(128) NOT NULL,
    sort_order      integer NOT NULL DEFAULT 0,
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_data_dictionary_code UNIQUE (dict_type, dict_code),
    CONSTRAINT ck_data_dictionary_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE number_rule (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code       varchar(64) NOT NULL,
    prefix          varchar(32) NOT NULL,
    date_pattern    varchar(32) NOT NULL DEFAULT 'yyyy',
    sequence_name   varchar(128) NOT NULL,
    padding         integer NOT NULL DEFAULT 6,
    reset_cycle     varchar(16) NOT NULL DEFAULT 'YEAR',
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_number_rule_code UNIQUE (rule_code),
    CONSTRAINT ck_number_rule_padding CHECK (padding BETWEEN 1 AND 12),
    CONSTRAINT ck_number_rule_reset CHECK (reset_cycle IN ('NEVER','YEAR','MONTH','DAY')),
    CONSTRAINT ck_number_rule_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE report_period_rule (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_type        varchar(64) NOT NULL,
    period_type         varchar(16) NOT NULL,
    due_offset_days     integer NOT NULL DEFAULT 5,
    late_grace_days     integer NOT NULL DEFAULT 0,
    status              varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_report_period_rule UNIQUE (project_type, period_type),
    CONSTRAINT ck_report_period_type CHECK (period_type IN ('MONTH','QUARTER')),
    CONSTRAINT ck_report_period_days CHECK (due_offset_days >= 0 AND late_grace_days >= 0),
    CONSTRAINT ck_report_period_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE budget_subject_template (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_type        varchar(64) NOT NULL,
    subject_code        varchar(64) NOT NULL,
    subject_name        varchar(128) NOT NULL,
    default_amount      numeric(18,2) NOT NULL DEFAULT 0,
    control_level       varchar(16) NOT NULL DEFAULT 'BLOCK',
    sort_order          integer NOT NULL DEFAULT 0,
    status              varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_budget_subject_template UNIQUE (project_type, subject_code),
    CONSTRAINT ck_budget_subject_amount CHECK (default_amount >= 0),
    CONSTRAINT ck_budget_subject_control CHECK (control_level IN ('BLOCK','WARN','APPROVAL')),
    CONSTRAINT ck_budget_subject_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE notification_rule (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code           varchar(64) NOT NULL,
    event_type          varchar(64) NOT NULL,
    channels            jsonb NOT NULL DEFAULT '["IN_APP"]'::jsonb,
    recipient_rule      jsonb NOT NULL DEFAULT '{}'::jsonb,
    template_code       varchar(64),
    enabled             boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_notification_rule_code UNIQUE (rule_code)
);

-- -----------------------------------------------------------------------------
-- 3. 申报、表单与外部项目登记
-- -----------------------------------------------------------------------------

CREATE TABLE apply_batch (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_no        varchar(64) NOT NULL,
    year            integer NOT NULL,
    name            varchar(256) NOT NULL,
    start_at        timestamptz NOT NULL,
    end_at          timestamptz NOT NULL,
    status          varchar(16) NOT NULL DEFAULT 'DRAFT',
    rules           jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_apply_batch_no UNIQUE (batch_no),
    CONSTRAINT ck_apply_batch_status CHECK (status IN
        ('DRAFT','OPEN','CLOSED','ARCHIVED')),
    CONSTRAINT ck_apply_batch_time CHECK (end_at > start_at)
);

CREATE TABLE apply_form_template (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_type    varchar(64) NOT NULL,
    version_no      integer NOT NULL,
    form_schema     jsonb NOT NULL,
    status          varchar(16) NOT NULL DEFAULT 'DRAFT',
    effective_from  timestamptz,
    effective_to    timestamptz,
    published_by    uuid REFERENCES iam_user(id),
    published_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_apply_form_type_version UNIQUE (project_type, version_no),
    CONSTRAINT ck_apply_form_version CHECK (version_no >= 1),
    CONSTRAINT ck_apply_form_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    CONSTRAINT ck_apply_form_effective CHECK (
        effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from)
);

CREATE TABLE project_type_config (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_type            varchar(64) NOT NULL,
    name                    varchar(128) NOT NULL,
    apply_form_template_id  uuid REFERENCES apply_form_template(id),
    default_budget_template varchar(64),
    report_period_rule_id   uuid REFERENCES report_period_rule(id),
    default_wf_template_id  uuid,
    acceptance_rule         jsonb NOT NULL DEFAULT '{}'::jsonb,
    status                  varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_project_type_config UNIQUE (project_type),
    CONSTRAINT ck_project_type_config_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE application (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_no      varchar(64) NOT NULL,
    batch_id            uuid NOT NULL REFERENCES apply_batch(id) ON DELETE RESTRICT,
    project_type        varchar(64) NOT NULL,
    applicant_id        uuid NOT NULL REFERENCES iam_user(id),
    dept_id             uuid NOT NULL REFERENCES org_unit(id),
    title               varchar(512) NOT NULL,
    source_type         varchar(16) NOT NULL DEFAULT 'INTERNAL',
    status              varchar(32) NOT NULL DEFAULT 'DRAFT',
    current_version_id  uuid,
    version             integer NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    CONSTRAINT uk_application_no UNIQUE (application_no),
    CONSTRAINT ck_application_source CHECK (source_type IN ('INTERNAL','EXTERNAL')),
    CONSTRAINT ck_application_status CHECK (status IN
        ('DRAFT','IN_REVIEW','SUPPLEMENT','APPROVED','REJECTED','WITHDRAWN','CLOSED'))
);

CREATE TABLE application_version (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id          uuid NOT NULL REFERENCES application(id) ON DELETE RESTRICT,
    version_no              integer NOT NULL,
    form_data               jsonb NOT NULL,
    content_hash            char(64) NOT NULL,
    status                  varchar(16) NOT NULL DEFAULT 'SUBMITTED',
    submitted_by            uuid NOT NULL REFERENCES iam_user(id),
    submitted_at            timestamptz NOT NULL DEFAULT now(),
    submit_idempotency_key  varchar(191),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_application_version_no UNIQUE (application_id, version_no),
    CONSTRAINT uk_application_version_idem UNIQUE (submit_idempotency_key),
    CONSTRAINT ck_application_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_application_version_hash CHECK (content_hash ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_application_version_status CHECK (status IN
        ('DRAFT','SUBMITTED','APPROVED','REJECTED','SUPERSEDED','WITHDRAWN'))
);

CREATE TABLE application_member (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_version_id  uuid NOT NULL REFERENCES application_version(id) ON DELETE RESTRICT,
    user_id                 uuid NOT NULL REFERENCES iam_user(id),
    member_role             varchar(32) NOT NULL,
    contribution_desc       varchar(1024),
    sort_order              integer NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_application_member UNIQUE (application_version_id, user_id, member_role)
);

CREATE TABLE application_review (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_version_id  uuid NOT NULL REFERENCES application_version(id) ON DELETE RESTRICT,
    review_stage            varchar(16) NOT NULL,
    reviewer_id             uuid NOT NULL REFERENCES iam_user(id),
    decision                varchar(16) NOT NULL,
    issues                  jsonb NOT NULL DEFAULT '[]'::jsonb,
    comment                 varchar(2048),
    reviewed_at             timestamptz NOT NULL DEFAULT now(),
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_application_review_stage CHECK (review_stage IN
        ('FORMAL','EXPERT','DECISION')),
    CONSTRAINT ck_application_review_decision CHECK (decision IN
        ('PASS','RETURN','REJECT','NOT_ACCEPT','APPROVE'))
);

CREATE TABLE external_project_registration (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      uuid NOT NULL,
    approval_no         varchar(128),
    contract_no         varchar(128),
    funder_name         varchar(256) NOT NULL,
    approved_amount     numeric(18,2),
    verified_by         uuid NOT NULL REFERENCES iam_user(id),
    verified_at         timestamptz NOT NULL DEFAULT now(),
    evidence_summary    jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_external_registration_application UNIQUE (application_id),
    CONSTRAINT ck_external_registration_no CHECK (approval_no IS NOT NULL OR contract_no IS NOT NULL),
    CONSTRAINT ck_external_registration_amount CHECK (
        approved_amount IS NULL OR approved_amount >= 0)
);

-- -----------------------------------------------------------------------------
-- 4. 审批模板、实例与任务
-- -----------------------------------------------------------------------------

CREATE TABLE wf_template (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(64) NOT NULL,
    name                varchar(256) NOT NULL,
    project_type        varchar(64),
    condition_schema    jsonb NOT NULL DEFAULT '{}'::jsonb,
    status              varchar(16) NOT NULL DEFAULT 'DRAFT',
    current_version_id  uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_wf_template_code UNIQUE (code),
    CONSTRAINT ck_wf_template_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED'))
);

CREATE TABLE wf_template_version (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     uuid NOT NULL REFERENCES wf_template(id) ON DELETE RESTRICT,
    version_no      integer NOT NULL,
    definition      jsonb NOT NULL,
    condition_expr  jsonb NOT NULL DEFAULT '{}'::jsonb,
    effective_from  timestamptz NOT NULL,
    effective_to    timestamptz,
    status          varchar(16) NOT NULL DEFAULT 'DRAFT',
    published_by    uuid REFERENCES iam_user(id),
    published_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_wf_template_version_no UNIQUE (template_id, version_no),
    CONSTRAINT ck_wf_template_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_wf_template_version_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    CONSTRAINT ck_wf_template_version_effective CHECK (
        effective_to IS NULL OR effective_to > effective_from)
);

CREATE TABLE wf_instance (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    biz_type                varchar(32) NOT NULL,
    biz_id                  uuid NOT NULL,
    biz_version_id          uuid NOT NULL,
    template_version_id     uuid NOT NULL REFERENCES wf_template_version(id),
    flowable_instance_id    varchar(128),
    status                  varchar(16) NOT NULL DEFAULT 'RUNNING',
    initiator_id            uuid NOT NULL REFERENCES iam_user(id),
    started_at              timestamptz NOT NULL DEFAULT now(),
    ended_at                timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_wf_instance_flowable UNIQUE (flowable_instance_id),
    CONSTRAINT ck_wf_instance_biz_type CHECK (biz_type IN
        ('APPLICATION','EXPENSE','CHANGE','ACCEPTANCE','HANDOVER')),
    CONSTRAINT ck_wf_instance_status CHECK (status IN
        ('RUNNING','APPROVED','REJECTED','RETURNED','CANCELLED','FAILED'))
);

CREATE TABLE wf_task (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id         uuid NOT NULL REFERENCES wf_instance(id) ON DELETE RESTRICT,
    node_key            varchar(64) NOT NULL,
    node_name           varchar(128) NOT NULL,
    assignee_id         uuid NOT NULL REFERENCES iam_user(id),
    delegated_from      uuid REFERENCES iam_user(id),
    status              varchar(16) NOT NULL DEFAULT 'PENDING',
    due_at              timestamptz,
    claimed_at          timestamptz,
    completed_at        timestamptz,
    action              varchar(16),
    comment             varchar(2048),
    action_request_id   varchar(64),
    round_no            integer NOT NULL DEFAULT 1,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_wf_task_status CHECK (status IN
        ('PENDING','PROCESSING','APPROVED','RETURNED','REJECTED','CANCELLED','TIMEOUT')),
    CONSTRAINT ck_wf_task_action CHECK (action IS NULL OR action IN
        ('APPROVED','RETURNED','REJECTED','CANCELLED','TIMEOUT')),
    CONSTRAINT ck_wf_task_round CHECK (round_no >= 1)
);

CREATE TABLE wf_delegation (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    delegator_id    uuid NOT NULL REFERENCES iam_user(id),
    delegate_id     uuid NOT NULL REFERENCES iam_user(id),
    scope           jsonb NOT NULL DEFAULT '{}'::jsonb,
    valid_from      timestamptz NOT NULL,
    valid_to        timestamptz NOT NULL,
    status          varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_wf_delegation_self CHECK (delegator_id <> delegate_id),
    CONSTRAINT ck_wf_delegation_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
    CONSTRAINT ck_wf_delegation_valid CHECK (valid_to > valid_from)
);

CREATE TABLE wf_callback_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id     uuid NOT NULL REFERENCES wf_instance(id) ON DELETE RESTRICT,
    task_id         uuid REFERENCES wf_task(id),
    request_id      varchar(64) NOT NULL,
    action          varchar(16) NOT NULL,
    payload         jsonb NOT NULL,
    status          varchar(16) NOT NULL DEFAULT 'PROCESSED',
    processed_at    timestamptz NOT NULL DEFAULT now(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_wf_callback_request UNIQUE (request_id)
);

-- -----------------------------------------------------------------------------
-- 5. 项目、基线、执行、风险、变更与交接
-- -----------------------------------------------------------------------------

CREATE TABLE project (
    id                              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_no                      varchar(64) NOT NULL,
    title                           varchar(512) NOT NULL,
    project_type                    varchar(64) NOT NULL,
    dept_id                         uuid NOT NULL REFERENCES org_unit(id),
    pi_id                           uuid NOT NULL REFERENCES iam_user(id),
    status                          varchar(32) NOT NULL,
    source_type                     varchar(16) NOT NULL DEFAULT 'INTERNAL',
    source_application_version_id   uuid,
    current_baseline_id             uuid,
    approved_at                     timestamptz,
    start_date                      date,
    end_date                        date,
    financial_project_no            varchar(128),
    external_approval_no            varchar(128),
    external_contract_no            varchar(128),
    frozen_at                       timestamptz,
    frozen_reason                   varchar(512),
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now(),
    created_by                      uuid,
    updated_by                      uuid,
    version                         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_project_no UNIQUE (project_no),
    CONSTRAINT uk_project_source_version UNIQUE (source_application_version_id),
    CONSTRAINT ck_project_status CHECK (status IN
        ('PENDING_START','EXECUTING','SUSPENDED','ACCEPTING','COMPLETED','TERMINATED')),
    CONSTRAINT ck_project_source CHECK (source_type IN ('INTERNAL','EXTERNAL')),
    CONSTRAINT ck_project_dates CHECK (
        start_date IS NULL OR end_date IS NULL OR start_date <= end_date)
);

CREATE TABLE project_member (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    user_id             uuid NOT NULL REFERENCES iam_user(id),
    project_role        varchar(32) NOT NULL,
    contribution_desc   varchar(1024),
    valid_from          timestamptz NOT NULL DEFAULT now(),
    valid_to            timestamptz,
    status              varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_project_member UNIQUE (project_id, user_id, project_role, valid_from),
    CONSTRAINT ck_project_member_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_project_member_valid CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE TABLE project_baseline (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    version_no      integer NOT NULL,
    snapshot        jsonb NOT NULL,
    snapshot_hash   char(64) NOT NULL,
    effective_at    timestamptz NOT NULL DEFAULT now(),
    status          varchar(16) NOT NULL DEFAULT 'EFFECTIVE',
    source_change_id uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_project_baseline_no UNIQUE (project_id, version_no),
    CONSTRAINT ck_project_baseline_no CHECK (version_no >= 1),
    CONSTRAINT ck_project_baseline_hash CHECK (snapshot_hash ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_project_baseline_status CHECK (status IN
        ('DRAFT','EFFECTIVE','SUPERSEDED'))
);

CREATE TABLE milestone (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    baseline_id             uuid NOT NULL REFERENCES project_baseline(id) ON DELETE RESTRICT,
    code                    varchar(64) NOT NULL,
    title                   varchar(512) NOT NULL,
    plan_date               date NOT NULL,
    current_plan_date       date NOT NULL,
    actual_date             date,
    owner_id                uuid REFERENCES iam_user(id),
    completion_criteria     text,
    status                  varchar(16) NOT NULL DEFAULT 'PLANNED',
    delay_wf_instance_id    uuid REFERENCES wf_instance(id),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_milestone_baseline_code UNIQUE (baseline_id, code),
    CONSTRAINT ck_milestone_status CHECK (status IN
        ('PLANNED','IN_PROGRESS','COMPLETED','DELAYED','CANCELLED'))
);

CREATE TABLE progress_report (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    period_type         varchar(16) NOT NULL,
    period_key          varchar(16) NOT NULL,
    due_date            date NOT NULL,
    status              varchar(16) NOT NULL DEFAULT 'DRAFT',
    current_version_id  uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_progress_report_period UNIQUE (project_id, period_type, period_key),
    CONSTRAINT ck_progress_report_type CHECK (period_type IN ('MONTH','QUARTER')),
    CONSTRAINT ck_progress_report_status CHECK (status IN
        ('DRAFT','SUBMITTED','RETURNED','APPROVED','LATE','CLOSED'))
);

CREATE TABLE progress_report_version (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id           uuid NOT NULL REFERENCES progress_report(id) ON DELETE RESTRICT,
    version_no          integer NOT NULL,
    content             jsonb NOT NULL,
    content_hash        char(64) NOT NULL,
    submitted_by        uuid NOT NULL REFERENCES iam_user(id),
    submitted_at        timestamptz NOT NULL DEFAULT now(),
    decision            varchar(16) NOT NULL DEFAULT 'PENDING',
    review_comment      varchar(2048),
    late_flag           boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_progress_report_version_no UNIQUE (report_id, version_no),
    CONSTRAINT ck_progress_report_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_progress_report_version_hash CHECK (content_hash ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_progress_report_decision CHECK (decision IN
        ('PENDING','PASS','RETURN','REJECT'))
);

CREATE TABLE deliverable (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    milestone_id        uuid REFERENCES milestone(id) ON DELETE SET NULL,
    report_version_id   uuid REFERENCES progress_report_version(id) ON DELETE SET NULL,
    file_id             uuid NOT NULL REFERENCES file_object(id) ON DELETE RESTRICT,
    title               varchar(512) NOT NULL,
    sha256              char(64),
    status              varchar(16) NOT NULL DEFAULT 'ACTIVE',
    uploaded_by         uuid NOT NULL REFERENCES iam_user(id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_deliverable_status CHECK (status IN ('ACTIVE','REVOKED')),
    CONSTRAINT ck_deliverable_sha CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE project_risk (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    source_rule         varchar(64),
    risk_type           varchar(32) NOT NULL,
    title               varchar(512) NOT NULL,
    description         text,
    risk_level          varchar(16) NOT NULL DEFAULT 'MEDIUM',
    owner_id            uuid NOT NULL REFERENCES iam_user(id),
    due_date            date,
    status              varchar(16) NOT NULL DEFAULT 'OPEN',
    resolution          text,
    evidence_file_id    uuid REFERENCES file_object(id),
    idempotency_key     varchar(191),
    closed_at           timestamptz,
    closed_by           uuid REFERENCES iam_user(id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_project_risk_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_project_risk_level CHECK (risk_level IN
        ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_project_risk_status CHECK (status IN
        ('OPEN','PROCESSING','ESCALATED','CLOSED','CANCELLED'))
);

CREATE TABLE change_request (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    change_type         varchar(32) NOT NULL,
    status              varchar(16) NOT NULL DEFAULT 'DRAFT',
    current_version_id  uuid,
    effective_date      date,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_change_request_type CHECK (change_type IN
        ('PI','MEMBER','PERIOD','GOAL','MILESTONE','BUDGET','PARTNER','OTHER')),
    CONSTRAINT ck_change_request_status CHECK (status IN
        ('DRAFT','IN_REVIEW','APPROVED','REJECTED','RETURNED','CANCELLED'))
);

CREATE TABLE change_request_version (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    change_request_id       uuid NOT NULL REFERENCES change_request(id) ON DELETE RESTRICT,
    version_no              integer NOT NULL,
    payload                 jsonb NOT NULL,
    old_baseline_id         uuid REFERENCES project_baseline(id),
    new_baseline_id         uuid REFERENCES project_baseline(id),
    reason                  varchar(2048) NOT NULL,
    submit_idempotency_key  varchar(191),
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_change_request_version_no UNIQUE (change_request_id, version_no),
    CONSTRAINT uk_change_request_version_idem UNIQUE (submit_idempotency_key),
    CONSTRAINT ck_change_request_version_no CHECK (version_no >= 1)
);

CREATE TABLE project_handover (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    from_user_id        uuid NOT NULL REFERENCES iam_user(id),
    to_user_id          uuid NOT NULL REFERENCES iam_user(id),
    effective_at        timestamptz NOT NULL,
    status              varchar(16) NOT NULL DEFAULT 'DRAFT',
    pending_items       jsonb NOT NULL DEFAULT '[]'::jsonb,
    reason              varchar(2048) NOT NULL,
    completed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_project_handover_users CHECK (from_user_id <> to_user_id),
    CONSTRAINT ck_project_handover_status CHECK (status IN
        ('DRAFT','IN_REVIEW','APPROVED','COMPLETED','CANCELLED'))
);

-- -----------------------------------------------------------------------------
-- 6. 预算、支出、资金事件、支付、导入与对账
-- -----------------------------------------------------------------------------

CREATE TABLE budget_version (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    version_no          integer NOT NULL,
    total_amount        numeric(18,2) NOT NULL,
    source_type         varchar(16) NOT NULL DEFAULT 'GRANT',
    control_level       varchar(16) NOT NULL DEFAULT 'BLOCK',
    status              varchar(16) NOT NULL DEFAULT 'DRAFT',
    effective_from      date,
    effective_to        date,
    change_request_id   uuid REFERENCES change_request(id),
    approved_by         uuid REFERENCES iam_user(id),
    approved_at         timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_budget_version_no UNIQUE (project_id, version_no),
    CONSTRAINT ck_budget_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_budget_version_amount CHECK (total_amount > 0),
    CONSTRAINT ck_budget_version_source CHECK (source_type IN
        ('GRANT','CONTRACT','MATCHING','ADJUSTMENT','MIGRATION')),
    CONSTRAINT ck_budget_version_control CHECK (control_level IN
        ('BLOCK','WARN','APPROVAL')),
    CONSTRAINT ck_budget_version_status CHECK (status IN
        ('DRAFT','IN_REVIEW','EFFECTIVE','SUPERSEDED','REJECTED')),
    CONSTRAINT ck_budget_version_effective CHECK (
        effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);

CREATE TABLE budget_line (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_version_id   uuid NOT NULL REFERENCES budget_version(id) ON DELETE CASCADE,
    subject_code        varchar(64) NOT NULL,
    subject_name        varchar(128) NOT NULL,
    amount              numeric(18,2) NOT NULL,
    control_level       varchar(16) NOT NULL DEFAULT 'BLOCK',
    description         varchar(1024),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_budget_line_subject UNIQUE (budget_version_id, subject_code),
    CONSTRAINT ck_budget_line_amount CHECK (amount > 0),
    CONSTRAINT ck_budget_line_control CHECK (control_level IN
        ('BLOCK','WARN','APPROVAL'))
);

CREATE TABLE budget_balance (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_line_id          uuid NOT NULL REFERENCES budget_line(id) ON DELETE CASCADE,
    approved_amount         numeric(18,2) NOT NULL DEFAULT 0,
    reserved_amount         numeric(18,2) NOT NULL DEFAULT 0,
    approved_unpaid_amount  numeric(18,2) NOT NULL DEFAULT 0,
    paid_amount             numeric(18,2) NOT NULL DEFAULT 0,
    version                 integer NOT NULL DEFAULT 0,
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_budget_balance_line UNIQUE (budget_line_id),
    CONSTRAINT ck_budget_balance_non_negative CHECK (
        approved_amount >= 0
        AND reserved_amount >= 0
        AND approved_unpaid_amount >= 0
        AND paid_amount >= 0),
    CONSTRAINT ck_budget_balance_available CHECK (
        reserved_amount + approved_unpaid_amount + paid_amount <= approved_amount)
);

CREATE TABLE expense_request (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_no                  varchar(64) NOT NULL,
    project_id                  uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    budget_version_id           uuid NOT NULL REFERENCES budget_version(id),
    budget_line_id              uuid NOT NULL REFERENCES budget_line(id),
    amount                      numeric(18,2) NOT NULL,
    purpose                     varchar(2048) NOT NULL,
    payee_name                  varchar(256),
    payee_bank_account_masked   varchar(32),
    payee_bank_account_encrypted text,
    payee_bank_name             varchar(256),
    status                      varchar(32) NOT NULL DEFAULT 'DRAFT',
    current_version_id          uuid,
    idempotency_key             varchar(191) NOT NULL,
    submitted_by                uuid REFERENCES iam_user(id),
    submitted_at                timestamptz,
    approved_at                 timestamptz,
    paid_at                     timestamptz,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    created_by                  uuid,
    updated_by                  uuid,
    version                     integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_expense_request_no UNIQUE (request_no),
    CONSTRAINT uk_expense_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_expense_amount CHECK (amount > 0),
    CONSTRAINT ck_expense_status CHECK (status IN
        ('DRAFT','IN_REVIEW','SUPPLEMENT','APPROVED_UNPAID','PARTIALLY_PAID',
         'PAID','CANCELLED','REJECTED','REVERSED'))
);

CREATE TABLE expense_request_version (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_request_id  uuid NOT NULL REFERENCES expense_request(id) ON DELETE RESTRICT,
    version_no          integer NOT NULL,
    payload             jsonb NOT NULL,
    content_hash        char(64) NOT NULL,
    submitted_by        uuid NOT NULL REFERENCES iam_user(id),
    submitted_at        timestamptz NOT NULL DEFAULT now(),
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_expense_version_no UNIQUE (expense_request_id, version_no),
    CONSTRAINT ck_expense_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_expense_version_hash CHECK (content_hash ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE fund_event (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_no                varchar(64) NOT NULL,
    project_id              uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    expense_request_id      uuid REFERENCES expense_request(id) ON DELETE RESTRICT,
    budget_version_id       uuid NOT NULL REFERENCES budget_version(id),
    budget_line_id          uuid NOT NULL REFERENCES budget_line(id),
    event_type              varchar(32) NOT NULL,
    amount                  numeric(18,2) NOT NULL,
    occurred_at             timestamptz NOT NULL DEFAULT now(),
    external_no             varchar(128),
    idempotency_key         varchar(191) NOT NULL,
    reversal_of_event_id    uuid REFERENCES fund_event(id),
    payload                 jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid REFERENCES iam_user(id),
    CONSTRAINT uk_fund_event_no UNIQUE (event_no),
    CONSTRAINT uk_fund_event_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_fund_event_type CHECK (event_type IN
        ('RESERVE','RELEASE','APPROVE','PAY','PARTIAL_PAY','CANCEL','REVERSE','REFUND','ADJUST')),
    CONSTRAINT ck_fund_event_amount CHECK (amount > 0)
);

CREATE TABLE payment_record (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    expense_request_id      uuid REFERENCES expense_request(id) ON DELETE RESTRICT,
    external_payment_no     varchar(128) NOT NULL,
    external_event_id       varchar(128),
    amount                  numeric(18,2) NOT NULL,
    currency                char(3) NOT NULL DEFAULT 'CNY',
    paid_at                 timestamptz NOT NULL,
    voucher_no              varchar(128),
    status                  varchar(16) NOT NULL DEFAULT 'RECEIVED',
    raw_payload             jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_external_no UNIQUE (external_payment_no),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_payment_status CHECK (status IN
        ('RECEIVED','APPLIED','REVERSED','DIFF'))
);

CREATE TABLE payment_import_batch (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_no        varchar(64) NOT NULL,
    source_type     varchar(16) NOT NULL DEFAULT 'EXCEL',
    file_id         uuid NOT NULL REFERENCES file_object(id),
    status          varchar(16) NOT NULL DEFAULT 'UPLOADED',
    total_rows      integer NOT NULL DEFAULT 0,
    success_rows    integer NOT NULL DEFAULT 0,
    error_rows      integer NOT NULL DEFAULT 0,
    uploaded_by     uuid NOT NULL REFERENCES iam_user(id),
    verified_by     uuid REFERENCES iam_user(id),
    applied_by      uuid REFERENCES iam_user(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_import_batch_no UNIQUE (batch_no),
    CONSTRAINT ck_payment_import_source CHECK (source_type IN ('EXCEL','CSV')),
    CONSTRAINT ck_payment_import_status CHECK (status IN
        ('UPLOADED','VALIDATED','READY','APPLYING','APPLIED','FAILED')),
    CONSTRAINT ck_payment_import_counts CHECK (
        total_rows >= 0 AND success_rows >= 0 AND error_rows >= 0
        AND success_rows + error_rows <= total_rows),
    CONSTRAINT ck_payment_import_verifier CHECK (
        verified_by IS NULL OR verified_by <> uploaded_by)
);

CREATE TABLE payment_import_row (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id                uuid NOT NULL REFERENCES payment_import_batch(id) ON DELETE CASCADE,
    row_no                  integer NOT NULL,
    raw_data                jsonb NOT NULL,
    matched_expense_id      uuid REFERENCES expense_request(id),
    external_payment_no     varchar(128),
    amount                  numeric(18,2),
    status                  varchar(16) NOT NULL DEFAULT 'PENDING',
    error_code              varchar(64),
    error_message           varchar(1024),
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_payment_import_row UNIQUE (batch_id, row_no),
    CONSTRAINT ck_payment_import_row_no CHECK (row_no >= 1),
    CONSTRAINT ck_payment_import_row_amount CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT ck_payment_import_row_status CHECK (status IN
        ('PENDING','VALID','INVALID','APPLIED','SKIPPED'))
);

CREATE TABLE reconciliation_batch (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_no                varchar(64) NOT NULL,
    period_start            date NOT NULL,
    period_end              date NOT NULL,
    source_batch_no         varchar(128),
    status                  varchar(16) NOT NULL DEFAULT 'RUNNING',
    total_finance_amount    numeric(18,2) NOT NULL DEFAULT 0,
    total_system_amount     numeric(18,2) NOT NULL DEFAULT 0,
    diff_count              integer NOT NULL DEFAULT 0,
    started_at              timestamptz NOT NULL DEFAULT now(),
    finished_at             timestamptz,
    created_by              uuid REFERENCES iam_user(id),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_reconciliation_batch_no UNIQUE (batch_no),
    CONSTRAINT ck_reconciliation_period CHECK (period_end >= period_start),
    CONSTRAINT ck_reconciliation_status CHECK (status IN
        ('RUNNING','COMPLETED','FAILED')),
    CONSTRAINT ck_reconciliation_diff_count CHECK (diff_count >= 0)
);

CREATE TABLE reconciliation_diff (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id                uuid NOT NULL REFERENCES reconciliation_batch(id) ON DELETE CASCADE,
    project_id              uuid REFERENCES project(id),
    payment_record_id       uuid REFERENCES payment_record(id),
    diff_type               varchar(32) NOT NULL,
    external_payment_no     varchar(128),
    system_amount           numeric(18,2),
    finance_amount          numeric(18,2),
    owner_id                uuid REFERENCES iam_user(id),
    status                  varchar(16) NOT NULL DEFAULT 'OPEN',
    resolution              varchar(2048),
    evidence_file_ids       jsonb NOT NULL DEFAULT '[]'::jsonb,
    closed_by               uuid REFERENCES iam_user(id),
    closed_at               timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_recon_diff_type CHECK (diff_type IN
        ('MISSING_PAYMENT','AMOUNT_MISMATCH','DUPLICATE','STATUS_LAG')),
    CONSTRAINT ck_recon_diff_status CHECK (status IN
        ('OPEN','PROCESSING','CLOSED','IGNORED'))
);

-- -----------------------------------------------------------------------------
-- 7. 成果
-- -----------------------------------------------------------------------------

CREATE TABLE research_output (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    output_type         varchar(32) NOT NULL,
    title               varchar(512) NOT NULL,
    doi                 varchar(256),
    patent_no           varchar(128),
    owner_id            uuid NOT NULL REFERENCES iam_user(id),
    dept_id             uuid NOT NULL REFERENCES org_unit(id),
    completed_at        date,
    status              varchar(16) NOT NULL DEFAULT 'DRAFT',
    current_version_id  uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_research_output_type CHECK (output_type IN
        ('PAPER','PATENT','SOFTWARE','STANDARD','AWARD','REPORT','OTHER')),
    CONSTRAINT ck_research_output_status CHECK (status IN
        ('DRAFT','SUBMITTED','APPROVED','RETURNED','REJECTED','REVOKED'))
);

CREATE TABLE output_version (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    output_id       uuid NOT NULL REFERENCES research_output(id) ON DELETE RESTRICT,
    version_no      integer NOT NULL,
    metadata        jsonb NOT NULL,
    content_hash    char(64) NOT NULL,
    created_by      uuid NOT NULL REFERENCES iam_user(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_output_version_no UNIQUE (output_id, version_no),
    CONSTRAINT ck_output_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_output_version_hash CHECK (content_hash ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE output_evidence (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    output_version_id   uuid NOT NULL REFERENCES output_version(id) ON DELETE RESTRICT,
    file_id             uuid NOT NULL REFERENCES file_object(id) ON DELETE RESTRICT,
    evidence_type       varchar(64) NOT NULL,
    sha256              char(64),
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_output_evidence_sha CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE output_project_relation (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    output_id           uuid NOT NULL REFERENCES research_output(id) ON DELETE RESTRICT,
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    contribution_desc   varchar(1024) NOT NULL,
    attribution_ratio   numeric(5,4),
    status              varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by          uuid REFERENCES iam_user(id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    revoked_at          timestamptz,
    revoked_by          uuid REFERENCES iam_user(id),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_output_project_relation UNIQUE (output_id, project_id),
    CONSTRAINT ck_output_relation_contribution CHECK (length(btrim(contribution_desc)) > 0),
    CONSTRAINT ck_output_relation_ratio CHECK (
        attribution_ratio IS NULL OR (attribution_ratio >= 0 AND attribution_ratio <= 1)),
    CONSTRAINT ck_output_relation_status CHECK (status IN ('ACTIVE','REVOKED'))
);

CREATE TABLE output_review (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    output_version_id   uuid NOT NULL REFERENCES output_version(id) ON DELETE RESTRICT,
    reviewer_id         uuid NOT NULL REFERENCES iam_user(id),
    decision            varchar(16) NOT NULL,
    comment             varchar(2048),
    reviewed_at         timestamptz NOT NULL DEFAULT now(),
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_output_review_decision CHECK (decision IN
        ('APPROVED','RETURNED','REJECTED'))
);

CREATE TABLE output_duplicate_candidate (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    output_id               uuid NOT NULL REFERENCES research_output(id) ON DELETE CASCADE,
    candidate_output_id     uuid NOT NULL REFERENCES research_output(id) ON DELETE CASCADE,
    match_type              varchar(16) NOT NULL,
    score                   numeric(5,4) NOT NULL DEFAULT 0,
    status                  varchar(16) NOT NULL DEFAULT 'OPEN',
    reviewed_by             uuid REFERENCES iam_user(id),
    reviewed_at             timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_output_duplicate UNIQUE (output_id, candidate_output_id, match_type),
    CONSTRAINT ck_output_duplicate_self CHECK (output_id <> candidate_output_id),
    CONSTRAINT ck_output_duplicate_type CHECK (match_type IN
        ('DOI','PATENT','TITLE','OWNER')),
    CONSTRAINT ck_output_duplicate_score CHECK (score >= 0 AND score <= 1),
    CONSTRAINT ck_output_duplicate_status CHECK (status IN
        ('OPEN','CONFIRMED','DISMISSED'))
);

-- -----------------------------------------------------------------------------
-- 8. 验收、整改与归档
-- -----------------------------------------------------------------------------

CREATE TABLE acceptance (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    status              varchar(16) NOT NULL DEFAULT 'PREPARING',
    current_version_id  uuid,
    initiated_by        uuid NOT NULL REFERENCES iam_user(id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_acceptance_status CHECK (status IN
        ('PREPARING','IN_REVIEW','RECTIFYING','APPROVED','REJECTED','TERMINATED'))
);

CREATE TABLE acceptance_version (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    acceptance_id   uuid NOT NULL REFERENCES acceptance(id) ON DELETE RESTRICT,
    version_no      integer NOT NULL,
    snapshot        jsonb NOT NULL,
    snapshot_hash   char(64) NOT NULL,
    submitted_by    uuid NOT NULL REFERENCES iam_user(id),
    submitted_at    timestamptz NOT NULL DEFAULT now(),
    status          varchar(16) NOT NULL DEFAULT 'SUBMITTED',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_acceptance_version_no UNIQUE (acceptance_id, version_no),
    CONSTRAINT ck_acceptance_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_acceptance_version_hash CHECK (snapshot_hash ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_acceptance_version_status CHECK (status IN
        ('SUBMITTED','APPROVED','RETURNED','SUPERSEDED'))
);

CREATE TABLE acceptance_material (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    acceptance_version_id   uuid NOT NULL REFERENCES acceptance_version(id) ON DELETE RESTRICT,
    material_type           varchar(64) NOT NULL,
    source_type             varchar(32) NOT NULL,
    source_id               uuid,
    file_id                 uuid REFERENCES file_object(id),
    sha256                  char(64),
    metadata                jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_acceptance_material_sha CHECK (
        sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE acceptance_review (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    acceptance_id           uuid NOT NULL REFERENCES acceptance(id) ON DELETE RESTRICT,
    acceptance_version_id   uuid NOT NULL REFERENCES acceptance_version(id) ON DELETE RESTRICT,
    review_role             varchar(16) NOT NULL,
    reviewer_id             uuid NOT NULL REFERENCES iam_user(id),
    decision                varchar(16) NOT NULL,
    comment                 varchar(2048),
    reviewed_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_acceptance_review_role CHECK (review_role IN
        ('EXPERT','FINANCE','ADMIN','LEADER')),
    CONSTRAINT ck_acceptance_review_decision CHECK (decision IN
        ('PASS','RECTIFY','REJECT'))
);

CREATE TABLE acceptance_rectification (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    acceptance_id           uuid NOT NULL REFERENCES acceptance(id) ON DELETE RESTRICT,
    acceptance_version_id   uuid NOT NULL REFERENCES acceptance_version(id) ON DELETE RESTRICT,
    issue_no                integer NOT NULL,
    description             varchar(2048) NOT NULL,
    owner_id                uuid NOT NULL REFERENCES iam_user(id),
    due_date                date NOT NULL,
    status                  varchar(16) NOT NULL DEFAULT 'OPEN',
    evidence_file_ids       jsonb NOT NULL DEFAULT '[]'::jsonb,
    submitted_at            timestamptz,
    closed_at               timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_rectification_issue UNIQUE (acceptance_id, issue_no),
    CONSTRAINT ck_rectification_issue_no CHECK (issue_no >= 1),
    CONSTRAINT ck_rectification_status CHECK (status IN
        ('OPEN','SUBMITTED','ACCEPTED','REJECTED'))
);

CREATE TABLE archive_package (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    acceptance_id           uuid NOT NULL REFERENCES acceptance(id) ON DELETE RESTRICT,
    acceptance_version_id   uuid NOT NULL REFERENCES acceptance_version(id) ON DELETE RESTRICT,
    status                  varchar(16) NOT NULL DEFAULT 'GENERATING',
    manifest                jsonb,
    manifest_hash           char(64),
    file_count              integer NOT NULL DEFAULT 0,
    total_bytes             bigint NOT NULL DEFAULT 0,
    generated_at            timestamptz,
    received_by             uuid REFERENCES iam_user(id),
    received_at             timestamptz,
    receipt_file_id         uuid REFERENCES file_object(id),
    failure_reason          varchar(1024),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_archive_project_acceptance UNIQUE (project_id, acceptance_version_id),
    CONSTRAINT ck_archive_status CHECK (status IN
        ('GENERATING','READY','FAILED','RECEIVED')),
    CONSTRAINT ck_archive_hash CHECK (
        manifest_hash IS NULL OR manifest_hash ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_archive_counts CHECK (file_count >= 0 AND total_bytes >= 0)
);

CREATE TABLE archive_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id      uuid NOT NULL REFERENCES archive_package(id) ON DELETE CASCADE,
    item_type       varchar(32) NOT NULL,
    item_id         uuid,
    file_id         uuid REFERENCES file_object(id),
    version_no      integer,
    sha256          char(64),
    path            varchar(1024) NOT NULL,
    size_bytes      bigint NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_archive_item_path UNIQUE (package_id, path),
    CONSTRAINT ck_archive_item_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_archive_item_sha CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$')
);

-- -----------------------------------------------------------------------------
-- 9. 审计、通知、待办、导出与集成
-- -----------------------------------------------------------------------------

CREATE TABLE audit_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    trace_id        varchar(64) NOT NULL,
    actor_id        uuid REFERENCES iam_user(id),
    on_behalf_of    uuid REFERENCES iam_user(id),
    action          varchar(64) NOT NULL,
    object_type     varchar(64) NOT NULL,
    object_id       uuid,
    object_version  integer,
    before_digest   char(64),
    after_digest    char(64),
    reason          varchar(2048),
    ip              inet,
    user_agent      varchar(512),
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_before_digest CHECK (
        before_digest IS NULL OR before_digest ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_audit_after_digest CHECK (
        after_digest IS NULL OR after_digest ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE notification (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
    type            varchar(32) NOT NULL,
    biz_type        varchar(32) NOT NULL,
    biz_id          uuid NOT NULL,
    biz_date        date NOT NULL DEFAULT CURRENT_DATE,
    title           varchar(512) NOT NULL,
    content         varchar(2048),
    read_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE todo_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES iam_user(id) ON DELETE CASCADE,
    biz_type        varchar(32) NOT NULL,
    biz_id          uuid NOT NULL,
    biz_version_id  uuid,
    title           varchar(512) NOT NULL,
    status          varchar(16) NOT NULL DEFAULT 'OPEN',
    due_at          timestamptz,
    assigned_by     uuid REFERENCES iam_user(id),
    completed_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_todo_status CHECK (status IN
        ('OPEN','DONE','CANCELLED','TRANSFERRED'))
);

CREATE TABLE export_task (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES iam_user(id),
    export_type     varchar(64) NOT NULL,
    query_snapshot  jsonb NOT NULL,
    scope_snapshot  jsonb NOT NULL,
    fields          jsonb NOT NULL DEFAULT '[]'::jsonb,
    status          varchar(16) NOT NULL DEFAULT 'PENDING',
    file_id         uuid REFERENCES file_object(id),
    row_count       integer,
    expires_at      timestamptz,
    error_message   varchar(1024),
    created_at      timestamptz NOT NULL DEFAULT now(),
    started_at      timestamptz,
    finished_at     timestamptz,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_export_status CHECK (status IN
        ('PENDING','RUNNING','SUCCESS','FAILED','EXPIRED')),
    CONSTRAINT ck_export_row_count CHECK (row_count IS NULL OR row_count >= 0)
);

CREATE TABLE integration_outbox (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            uuid NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      varchar(64) NOT NULL,
    aggregate_id        uuid NOT NULL,
    event_type          varchar(64) NOT NULL,
    payload             jsonb NOT NULL,
    trace_id            varchar(64),
    status              varchar(16) NOT NULL DEFAULT 'NEW',
    attempts            integer NOT NULL DEFAULT 0,
    idempotency_key     varchar(191) NOT NULL,
    next_retry_at       timestamptz NOT NULL DEFAULT now(),
    sent_at             timestamptz,
    last_error          varchar(2048),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id),
    CONSTRAINT uk_outbox_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_outbox_status CHECK (status IN ('NEW','SENT','FAILED','DEAD')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0)
);

CREATE TABLE integration_sync_log (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    system_code         varchar(64) NOT NULL,
    direction           varchar(16) NOT NULL,
    biz_type            varchar(32),
    biz_id              uuid,
    external_key        varchar(128),
    external_event_id   varchar(128),
    request_digest      char(64),
    response_digest     char(64),
    status              varchar(32) NOT NULL,
    error_code          varchar(64),
    error_message       varchar(2048),
    trace_id            varchar(64),
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sync_log_direction CHECK (direction IN ('OUTBOUND','INBOUND')),
    CONSTRAINT ck_sync_log_status CHECK (status IN
        ('SUCCESS','FAILED','ACCEPTED_WITH_DIFF','RETRYING'))
);

CREATE TABLE job_run (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name        varchar(128) NOT NULL,
    shard_key       varchar(64) NOT NULL DEFAULT '0',
    status          varchar(16) NOT NULL DEFAULT 'RUNNING',
    started_at      timestamptz NOT NULL DEFAULT now(),
    finished_at     timestamptz,
    total_count     integer NOT NULL DEFAULT 0,
    success_count   integer NOT NULL DEFAULT 0,
    failed_count    integer NOT NULL DEFAULT 0,
    error_summary   varchar(2048),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_job_run_status CHECK (status IN
        ('RUNNING','SUCCESS','FAILED','TIMEOUT')),
    CONSTRAINT ck_job_run_counts CHECK (
        total_count >= 0 AND success_count >= 0 AND failed_count >= 0)
);

CREATE TABLE job_failed_detail (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_run_id      uuid NOT NULL REFERENCES job_run(id) ON DELETE CASCADE,
    biz_type        varchar(32),
    biz_id          uuid,
    error_code      varchar(64),
    error_message   varchar(2048) NOT NULL,
    retryable       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- 10. 循环依赖外键补齐
-- -----------------------------------------------------------------------------

ALTER TABLE application
    ADD CONSTRAINT fk_application_current_version
    FOREIGN KEY (current_version_id) REFERENCES application_version(id) ON DELETE RESTRICT;

ALTER TABLE external_project_registration
    ADD CONSTRAINT fk_external_registration_application
    FOREIGN KEY (application_id) REFERENCES application(id) ON DELETE RESTRICT;

ALTER TABLE wf_template
    ADD CONSTRAINT fk_wf_template_current_version
    FOREIGN KEY (current_version_id) REFERENCES wf_template_version(id) ON DELETE RESTRICT;

ALTER TABLE project_type_config
    ADD CONSTRAINT fk_project_type_wf_template
    FOREIGN KEY (default_wf_template_id) REFERENCES wf_template(id) ON DELETE RESTRICT;

ALTER TABLE project
    ADD CONSTRAINT fk_project_source_version
    FOREIGN KEY (source_application_version_id) REFERENCES application_version(id) ON DELETE RESTRICT;

ALTER TABLE project
    ADD CONSTRAINT fk_project_current_baseline
    FOREIGN KEY (current_baseline_id) REFERENCES project_baseline(id) ON DELETE RESTRICT;

ALTER TABLE project_baseline
    ADD CONSTRAINT fk_project_baseline_source_change
    FOREIGN KEY (source_change_id) REFERENCES change_request_version(id) ON DELETE RESTRICT;

ALTER TABLE progress_report
    ADD CONSTRAINT fk_progress_report_current_version
    FOREIGN KEY (current_version_id) REFERENCES progress_report_version(id) ON DELETE RESTRICT;

ALTER TABLE change_request
    ADD CONSTRAINT fk_change_request_current_version
    FOREIGN KEY (current_version_id) REFERENCES change_request_version(id) ON DELETE RESTRICT;

ALTER TABLE expense_request
    ADD CONSTRAINT fk_expense_current_version
    FOREIGN KEY (current_version_id) REFERENCES expense_request_version(id) ON DELETE RESTRICT;

ALTER TABLE research_output
    ADD CONSTRAINT fk_output_current_version
    FOREIGN KEY (current_version_id) REFERENCES output_version(id) ON DELETE RESTRICT;

ALTER TABLE acceptance
    ADD CONSTRAINT fk_acceptance_current_version
    FOREIGN KEY (current_version_id) REFERENCES acceptance_version(id) ON DELETE RESTRICT;

-- -----------------------------------------------------------------------------
-- 11. 部分唯一索引
-- -----------------------------------------------------------------------------

CREATE UNIQUE INDEX uk_apply_form_published
    ON apply_form_template(project_type)
    WHERE status = 'PUBLISHED';

CREATE UNIQUE INDEX uk_external_registration_approval
    ON external_project_registration(approval_no)
    WHERE approval_no IS NOT NULL;

CREATE UNIQUE INDEX uk_external_registration_contract
    ON external_project_registration(contract_no)
    WHERE contract_no IS NOT NULL;

CREATE UNIQUE INDEX uk_wf_instance_running
    ON wf_instance(biz_type, biz_id, biz_version_id)
    WHERE status = 'RUNNING';

CREATE UNIQUE INDEX uk_wf_task_action_request
    ON wf_task(action_request_id)
    WHERE action_request_id IS NOT NULL;

CREATE UNIQUE INDEX uk_budget_version_effective
    ON budget_version(project_id)
    WHERE status = 'EFFECTIVE';

CREATE UNIQUE INDEX uk_acceptance_project_open
    ON acceptance(project_id)
    WHERE status IN ('PREPARING','IN_REVIEW','RECTIFYING');

CREATE UNIQUE INDEX uk_notification_biz
    ON notification(user_id, type, biz_id, biz_date);

CREATE UNIQUE INDEX uk_sync_log_external_event
    ON integration_sync_log(system_code, direction, external_event_id)
    WHERE external_event_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 12. 查询索引
-- -----------------------------------------------------------------------------

CREATE INDEX idx_org_unit_parent ON org_unit(parent_id);
CREATE INDEX idx_iam_user_dept_status ON iam_user(dept_id, status);
CREATE INDEX idx_iam_user_role_user ON iam_user_role(user_id, status);
CREATE INDEX idx_iam_user_role_valid ON iam_user_role(valid_from, valid_to);
CREATE INDEX idx_temp_grant_grantee_valid ON iam_temp_grant(grantee_id, valid_from, valid_to);
CREATE INDEX idx_org_membership_user ON org_membership(user_id, status);
CREATE INDEX idx_org_membership_org ON org_membership(org_id, status);
CREATE INDEX idx_coi_biz_expert ON coi_declaration(biz_type, biz_id, expert_id);
CREATE INDEX idx_file_object_sha256 ON file_object(sha256);
CREATE INDEX idx_file_object_status ON file_object(status, created_at DESC);
CREATE INDEX idx_file_ref_biz ON file_ref(biz_type, biz_id);
CREATE INDEX idx_apply_batch_year_status ON apply_batch(year, status);
CREATE INDEX idx_application_applicant_status ON application(applicant_id, status, updated_at DESC);
CREATE INDEX idx_application_batch_status ON application(batch_id, status);
CREATE INDEX idx_application_dept_status ON application(dept_id, status);
CREATE INDEX idx_application_version_status ON application_version(status, submitted_at);
CREATE INDEX idx_application_member_version ON application_member(application_version_id);
CREATE INDEX idx_application_review_version_stage ON application_review(application_version_id, review_stage);
CREATE INDEX idx_wf_instance_biz ON wf_instance(biz_type, biz_id, started_at DESC);
CREATE INDEX idx_wf_task_assignee_status ON wf_task(assignee_id, status, due_at);
CREATE INDEX idx_wf_delegation_delegate_valid ON wf_delegation(delegate_id, valid_from, valid_to);
CREATE INDEX idx_project_pi_status ON project(pi_id, status, updated_at DESC);
CREATE INDEX idx_project_dept_status ON project(dept_id, status, updated_at DESC);
CREATE INDEX idx_project_type_status ON project(project_type, status);
CREATE INDEX idx_project_start_date ON project(start_date);
CREATE INDEX idx_project_member_project ON project_member(project_id, status);
CREATE INDEX idx_project_member_user ON project_member(user_id, status);
CREATE INDEX idx_project_baseline_effective ON project_baseline(project_id, effective_at DESC);
CREATE INDEX idx_milestone_project_plan ON milestone(project_id, plan_date);
CREATE INDEX idx_milestone_owner_status ON milestone(owner_id, status, current_plan_date);
CREATE INDEX idx_progress_report_due_status ON progress_report(due_date, status);
CREATE INDEX idx_deliverable_project ON deliverable(project_id, created_at DESC);
CREATE INDEX idx_deliverable_milestone ON deliverable(milestone_id);
CREATE INDEX idx_risk_project_status ON project_risk(project_id, status, risk_level);
CREATE INDEX idx_risk_owner_due ON project_risk(owner_id, status, due_date);
CREATE INDEX idx_change_project_status ON change_request(project_id, status, created_at DESC);
CREATE INDEX idx_handover_project_status ON project_handover(project_id, status, effective_at);
CREATE INDEX idx_budget_project_status ON budget_version(project_id, status, version_no DESC);
CREATE INDEX idx_expense_project_status ON expense_request(project_id, status, created_at DESC);
CREATE INDEX idx_fund_event_project_type ON fund_event(project_id, event_type, occurred_at);
CREATE INDEX idx_fund_event_external_no ON fund_event(external_no);
CREATE INDEX idx_payment_expense ON payment_record(expense_request_id);
CREATE INDEX idx_payment_project_paid ON payment_record(project_id, paid_at DESC);
CREATE INDEX idx_payment_import_row_batch ON payment_import_row(batch_id, status);
CREATE INDEX idx_recon_diff_batch_status ON reconciliation_diff(batch_id, status);
CREATE INDEX idx_output_type_status ON research_output(output_type, status);
CREATE INDEX idx_output_doi ON research_output(doi);
CREATE INDEX idx_output_patent_no ON research_output(patent_no);
CREATE INDEX idx_output_evidence_version ON output_evidence(output_version_id);
CREATE INDEX idx_output_relation_project ON output_project_relation(project_id, status);
CREATE INDEX idx_output_review_version ON output_review(output_version_id);
CREATE INDEX idx_output_duplicate_status ON output_duplicate_candidate(status, score DESC);
CREATE INDEX idx_acceptance_status ON acceptance(status, updated_at DESC);
CREATE INDEX idx_acceptance_material_version ON acceptance_material(acceptance_version_id, material_type);
CREATE INDEX idx_acceptance_review_version ON acceptance_review(acceptance_version_id, review_role);
CREATE INDEX idx_rectification_acceptance_status ON acceptance_rectification(acceptance_id, status);
CREATE INDEX idx_archive_item_package ON archive_item(package_id);
CREATE INDEX idx_audit_object_time ON audit_log(object_type, object_id, occurred_at DESC);
CREATE INDEX idx_audit_actor_time ON audit_log(actor_id, occurred_at DESC);
CREATE INDEX idx_audit_trace ON audit_log(trace_id);
CREATE INDEX idx_notification_user_time ON notification(user_id, created_at DESC);
CREATE INDEX idx_todo_user_status_due ON todo_item(user_id, status, due_at);
CREATE INDEX idx_export_task_user_status ON export_task(user_id, status, created_at DESC);
CREATE INDEX idx_outbox_status_retry ON integration_outbox(status, next_retry_at);
CREATE INDEX idx_sync_log_system_time ON integration_sync_log(system_code, occurred_at DESC);
CREATE INDEX idx_job_run_name_time ON job_run(job_name, started_at DESC);
CREATE INDEX idx_job_failed_detail_run ON job_failed_detail(job_run_id);

-- pg_trgm 模糊检索索引
CREATE INDEX idx_iam_user_display_name_trgm ON iam_user USING gin (display_name gin_trgm_ops);
CREATE INDEX idx_project_title_trgm ON project USING gin (title gin_trgm_ops);
CREATE INDEX idx_output_title_trgm ON research_output USING gin (title gin_trgm_ops);

-- -----------------------------------------------------------------------------
-- 13. 更新时间触发器
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'org_unit','iam_user','iam_role','iam_user_role','iam_temp_grant',
        'org_membership','coi_declaration','file_object','system_config',
        'data_dictionary','number_rule','report_period_rule','budget_subject_template',
        'notification_rule','apply_batch','apply_form_template','project_type_config',
        'application','application_version','wf_template','wf_template_version',
        'wf_instance','wf_task','wf_delegation','project','project_member',
        'project_baseline','milestone','progress_report','deliverable','project_risk',
        'change_request','project_handover','budget_version','budget_line',
        'expense_request','payment_record','payment_import_batch','reconciliation_batch',
        'reconciliation_diff','research_output','output_project_relation','acceptance',
        'acceptance_version','acceptance_rectification','archive_package',
        'todo_item','integration_outbox'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            'trg_' || t || '_updated_at', t
        );
    END LOOP;
END;
$$;

-- -----------------------------------------------------------------------------
-- 14. 只追加与不可变内容保护
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION reject_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is forbidden', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER trg_fund_event_immutable
    BEFORE UPDATE OR DELETE ON fund_event
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE OR REPLACE FUNCTION protect_versioned_content()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '% is versioned and cannot be deleted', TG_TABLE_NAME
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    IF TG_TABLE_NAME = 'application_version'
       AND (NEW.form_data IS DISTINCT FROM OLD.form_data
            OR NEW.content_hash IS DISTINCT FROM OLD.content_hash) THEN
        RAISE EXCEPTION 'application_version content is immutable'
            USING ERRCODE = 'integrity_constraint_violation';
    ELSIF TG_TABLE_NAME = 'project_baseline'
       AND (NEW.snapshot IS DISTINCT FROM OLD.snapshot
            OR NEW.snapshot_hash IS DISTINCT FROM OLD.snapshot_hash) THEN
        RAISE EXCEPTION 'project_baseline snapshot is immutable'
            USING ERRCODE = 'integrity_constraint_violation';
    ELSIF TG_TABLE_NAME = 'acceptance_version'
       AND (NEW.snapshot IS DISTINCT FROM OLD.snapshot
            OR NEW.snapshot_hash IS DISTINCT FROM OLD.snapshot_hash) THEN
        RAISE EXCEPTION 'acceptance_version snapshot is immutable'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_application_version_content
    BEFORE UPDATE OR DELETE ON application_version
    FOR EACH ROW EXECUTE FUNCTION protect_versioned_content();

CREATE TRIGGER trg_project_baseline_content
    BEFORE UPDATE OR DELETE ON project_baseline
    FOR EACH ROW EXECUTE FUNCTION protect_versioned_content();

CREATE TRIGGER trg_acceptance_version_content
    BEFORE UPDATE OR DELETE ON acceptance_version
    FOR EACH ROW EXECUTE FUNCTION protect_versioned_content();

CREATE OR REPLACE FUNCTION protect_received_archive()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'RECEIVED' THEN
        RAISE EXCEPTION 'archive_package % is already received', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_archive_received_immutable
    BEFORE UPDATE ON archive_package
    FOR EACH ROW EXECUTE FUNCTION protect_received_archive();

-- -----------------------------------------------------------------------------
-- 15. 关键表注释
-- -----------------------------------------------------------------------------

COMMENT ON TABLE org_unit IS '组织树；path 为从根到当前节点的编码路径';
COMMENT ON TABLE iam_user IS '本地人员账号；身份证、手机号明文只存加密列';
COMMENT ON TABLE iam_temp_grant IS '临时授权；valid_to 到期后立即失效';
COMMENT ON TABLE application IS '申报主档；current_version_id 指向当前不可变版本';
COMMENT ON TABLE application_version IS '申报不可变版本；form_data 与 content_hash 禁止覆盖';
COMMENT ON TABLE wf_instance IS '审批实例；一个业务版本最多一个 RUNNING 实例';
COMMENT ON TABLE wf_task IS '审批任务；action_request_id 保证重复提交幂等';
COMMENT ON TABLE project IS '项目主档；项目、审批、支付状态机相互独立';
COMMENT ON TABLE project_baseline IS '项目基线；snapshot 与 snapshot_hash 不可覆盖';
COMMENT ON TABLE milestone IS '里程碑；保留原计划、当前计划和实际完成日期';
COMMENT ON TABLE progress_report IS '周期报告主档；每项目每周期唯一';
COMMENT ON TABLE project_risk IS '风险与处置；idempotency_key 供扫描任务幂等';
COMMENT ON TABLE budget_version IS '预算版本；同一项目同一时刻最多一个 EFFECTIVE';
COMMENT ON TABLE budget_balance IS '预算余额投影；四类金额由 fund_event 重建';
COMMENT ON TABLE fund_event IS '不可变资金事件；禁止 UPDATE 与 DELETE';
COMMENT ON TABLE payment_record IS '财务支付记录；external_payment_no 全局唯一';
COMMENT ON TABLE reconciliation_diff IS '对账差异；关闭必须记录责任人与证据';
COMMENT ON TABLE research_output IS '成果主档；成果与项目多对多';
COMMENT ON TABLE output_project_relation IS '成果项目关联；撤销置 REVOKED，不物理删除';
COMMENT ON TABLE acceptance IS '验收主档；同一项目最多一个未结束验收';
COMMENT ON TABLE acceptance_version IS '验收快照；snapshot 与 snapshot_hash 不可覆盖';
COMMENT ON TABLE archive_package IS '归档包；接收后不可修改';
COMMENT ON TABLE audit_log IS '审计日志；只允许 INSERT';
COMMENT ON TABLE integration_outbox IS '事务内 outbox；与业务写同一事务';
COMMENT ON TABLE job_run IS '后台任务执行记录；记录分片、耗时和成功失败数';

COMMENT ON COLUMN budget_balance.approved_amount IS '生效预算上限';
COMMENT ON COLUMN budget_balance.reserved_amount IS '在审占用';
COMMENT ON COLUMN budget_balance.approved_unpaid_amount IS '已批未付';
COMMENT ON COLUMN budget_balance.paid_amount IS '实际净支付';
COMMENT ON COLUMN budget_balance.version IS '乐观锁版本；所有变更必须写 fund_event';
COMMENT ON COLUMN fund_event.idempotency_key IS '业务幂等键；重复事件返回原事件';
COMMENT ON COLUMN acceptance_version.snapshot_hash IS 'SHA-256(canonical_json(snapshot) + sorted(material.sha256))';
COMMENT ON COLUMN archive_package.manifest_hash IS 'SHA-256(canonical_json(manifest))';

-- =============================================================================
-- 基线结束
-- =============================================================================









