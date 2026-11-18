-- =============================================================================
-- 科研项目管理系统 — P1 范围补充支撑表（PRD V2.1 D-16 对齐）
-- 版本：V07
-- 覆盖：PORT-05 外部链接 / APPLY-07 专家评审 / EXEC-07 轻量任务
--       RES-07 全院成果库（复用 V01 表与 pg_trgm 索引，无新表）/
--       CHG-05 验收标准重定
-- 设计：09-P1范围补充接口设计.md
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. wf_instance.biz_type 扩展（专家评审批次如需审批确认时预留）
-- -----------------------------------------------------------------------------
ALTER TABLE wf_instance DROP CONSTRAINT IF EXISTS ck_wf_instance_biz_type;
ALTER TABLE wf_instance
    ADD CONSTRAINT ck_wf_instance_biz_type CHECK (biz_type IN
        ('APPLICATION','EXPENSE','CHANGE','ACCEPTANCE','HANDOVER',
         'MILESTONE_DELAY',      -- 里程碑延期审批 (EXEC-02)
         'WAIVER',               -- 验收条件豁免审批 (ACC-01)
         'SPECIAL_APPROVAL',     -- 支出特批审批 (FUND-04)
         'ARCHIVE_CORRECTION',   -- 档案更正审批 (ACC-06)
         'EXPERT_REVIEW'));      -- 专家评审汇总确认 (APPLY-07)

-- -----------------------------------------------------------------------------
-- 1. 专家评审分配（APPLY-07）
-- 回避声明复用既有 coi_declaration（biz_type='EXPERT_REVIEW', biz_id=assignment_id）
-- -----------------------------------------------------------------------------
CREATE TABLE expert_review_assignment (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id                uuid NOT NULL REFERENCES apply_batch(id) ON DELETE RESTRICT,
    application_id          uuid NOT NULL REFERENCES application(id) ON DELETE RESTRICT,
    application_version_id  uuid NOT NULL REFERENCES application_version(id) ON DELETE RESTRICT,
    expert_id               uuid NOT NULL REFERENCES iam_user(id),
    status                  varchar(16) NOT NULL DEFAULT 'ASSIGNED',
    review_deadline         timestamptz,
    assigned_by             uuid NOT NULL REFERENCES iam_user(id),
    removed_by              uuid REFERENCES iam_user(id),
    removed_reason          varchar(512),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_expert_assign_status CHECK (status IN
        ('ASSIGNED','ACTIVE','COMPLETED','DECLINED','REMOVED'))
    -- 同一版本同一专家仅一条有效分配（下方 uk_expert_assign_active 部分唯一索引）；
    -- REMOVED 后允许重新分配，旧行留痕
);

CREATE INDEX idx_expert_assign_batch ON expert_review_assignment(batch_id);
CREATE INDEX idx_expert_assign_expert ON expert_review_assignment(expert_id, status);

CREATE UNIQUE INDEX uk_expert_assign_active
    ON expert_review_assignment(application_version_id, expert_id)
    WHERE status <> 'REMOVED';

-- -----------------------------------------------------------------------------
-- 2. 专家评分与意见提交（APPLY-07；一次提交不可改，意见留痕）
-- score_items 结构由评分表模板校验（09 §3.4）；汇总规则固定版本 AVG-TRIM-1
-- -----------------------------------------------------------------------------
CREATE TABLE expert_review_submission (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id   uuid NOT NULL UNIQUE REFERENCES expert_review_assignment(id) ON DELETE RESTRICT,
    score_items     jsonb NOT NULL,           -- [{itemCode, score, weight}]，服务端重算校验
    total_score     numeric(6,2) NOT NULL,
    recommendation  varchar(24) NOT NULL,     -- APPROVE / REVISE / REJECT
    comment         varchar(4000),
    submitted_at    timestamptz NOT NULL DEFAULT now(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_expert_sub_recommend CHECK (recommendation IN
        ('APPROVE','REVISE','REJECT')),
    CONSTRAINT ck_expert_sub_score CHECK (total_score >= 0 AND total_score <= 100)
);

CREATE INDEX idx_expert_sub_assignment ON expert_review_submission(assignment_id);

-- 专家意见 append-only：禁止 UPDATE/DELETE（意见留痕，对齐 PRD §8.2）
CREATE FUNCTION v07_raise_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'expert_review_submission is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_expert_sub_immutable
    BEFORE UPDATE OR DELETE ON expert_review_submission
    FOR EACH ROW EXECUTE FUNCTION v07_raise_immutable();

-- -----------------------------------------------------------------------------
-- 3. 专家评审汇总（APPLY-07；append-only，最新一条为当前汇总）
-- -----------------------------------------------------------------------------
CREATE TABLE expert_review_summary (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_version_id  uuid NOT NULL REFERENCES application_version(id) ON DELETE RESTRICT,
    rule_version            varchar(16) NOT NULL DEFAULT 'AVG-TRIM-1',
    expert_count            integer NOT NULL,
    valid_count             integer NOT NULL,
    avg_score               numeric(6,2) NOT NULL,
    detail                  jsonb NOT NULL,   -- 匿名编号明细与去最高/最低记录
    generated_by            uuid NOT NULL REFERENCES iam_user(id),
    generated_at            timestamptz NOT NULL DEFAULT now(),
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_expert_sum_count CHECK (valid_count >= 1 AND expert_count >= valid_count),
    CONSTRAINT ck_expert_sum_score CHECK (avg_score >= 0 AND avg_score <= 100)
);

CREATE INDEX idx_expert_sum_version ON expert_review_summary(application_version_id, generated_at DESC);

CREATE TRIGGER tg_expert_sum_immutable
    BEFORE UPDATE OR DELETE ON expert_review_summary
    FOR EACH ROW EXECUTE FUNCTION v07_raise_immutable();

-- -----------------------------------------------------------------------------
-- 4. 外部消息平台跳转授权（PORT-05；模式同 V06 file_download_grant）
-- token 仅存 SHA-256 哈希；有效期 ≤ 5 分钟；一次性消费
-- -----------------------------------------------------------------------------
CREATE TABLE external_link_grant (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash      char(64) NOT NULL UNIQUE,     -- SHA-256(token)
    user_id         uuid NOT NULL REFERENCES iam_user(id),
    resource_type   varchar(16) NOT NULL,         -- PROJECT / APPLICATION / TODO / REPORT
    resource_id     uuid NOT NULL,
    purpose         varchar(256),
    channel         varchar(16),                  -- WECOM / DINGTALK / EMAIL / OTHER
    expires_at      timestamptz NOT NULL,
    consumed_at     timestamptz,
    consumed_ip     varchar(64),
    revoked_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid NOT NULL REFERENCES iam_user(id),
    CONSTRAINT ck_ext_link_resource CHECK (resource_type IN
        ('PROJECT','APPLICATION','TODO','REPORT')),
    CONSTRAINT ck_ext_link_channel CHECK (channel IS NULL OR channel IN
        ('WECOM','DINGTALK','EMAIL','OTHER')),
    CONSTRAINT ck_ext_link_ttl CHECK (expires_at <= created_at + interval '5 minutes')
);

-- 每用户每资源仅一条未消费授权，防 token 刷发
CREATE UNIQUE INDEX uk_ext_link_active
    ON external_link_grant(user_id, resource_type, resource_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_ext_link_expiry ON external_link_grant(expires_at);

-- -----------------------------------------------------------------------------
-- 5. 轻量任务（EXEC-07；无任务依赖，成员仅可更新被授权任务）
-- -----------------------------------------------------------------------------
CREATE TABLE project_task (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    title           varchar(256) NOT NULL,
    description     varchar(2000),
    assignee_id     uuid NOT NULL REFERENCES iam_user(id),
    due_date        date,
    status          varchar(16) NOT NULL DEFAULT 'TODO',
    deliverable_id  uuid REFERENCES deliverable(id) ON DELETE SET NULL,
    completed_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_project_task_status CHECK (status IN
        ('TODO','IN_PROGRESS','DONE','CANCELLED'))
);

CREATE INDEX idx_task_project ON project_task(project_id, status);
CREATE INDEX idx_task_assignee ON project_task(assignee_id, status);

-- -----------------------------------------------------------------------------
-- 6. 验收标准重定（CHG-05；GOAL 变更生效后触发，一项目至多一条 PENDING）
-- original_criteria 为变更前基线快照，不可变；原始目标仍以基线历史供审计
-- -----------------------------------------------------------------------------
CREATE TABLE acceptance_criteria_revision (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          uuid NOT NULL REFERENCES project(id) ON DELETE RESTRICT,
    change_request_id   uuid NOT NULL REFERENCES change_request(id) ON DELETE RESTRICT,
    original_criteria   jsonb NOT NULL,   -- 变更前生效验收标准快照
    revised_criteria    jsonb,            -- 确认时写入
    status              varchar(16) NOT NULL DEFAULT 'PENDING',
    confirmed_by        uuid REFERENCES iam_user(id),
    confirmed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_criteria_rev_status CHECK (status IN
        ('PENDING','CONFIRMED','CANCELLED'))
);

-- 一项目至多一条待确认重定任务（幂等兜底）
CREATE UNIQUE INDEX uk_criteria_rev_pending
    ON acceptance_criteria_revision(project_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_criteria_rev_project ON acceptance_criteria_revision(project_id, status);
