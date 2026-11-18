-- =============================================================================
-- 科研项目管理系统 — 物化视图
-- 版本：V02
-- 修复：评审问题 #2 — RPT-01～RPT-05 的物化视图基础设施
-- 说明：
--   1. 物化视图承载报表快照数据，提升查询性能并降低在线负载
--   2. 刷新任务 ReportRefreshJob 按依赖顺序执行：
--      mv_project_summary → mv_funding_summary → mv_output_summary → mv_risk_summary
--   3. 每个视图包含 data_as_of 时间戳，标识数据截止时点（响应体原样返回）
--   4. 单个视图刷新失败终止后续视图（依赖顺序），保留上次数据
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 项目摘要物化视图 (RPT-01 项目概况统计)
-- -----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_project_summary AS
SELECT
    p.id AS project_id,
    p.project_no,
    p.title AS project_title,
    p.status,
    p.project_type,
    p.dept_id,
    ou.name AS dept_name,
    ou.path AS dept_path,
    pb.id AS baseline_id,
    pb.version_no AS baseline_version,
    p.start_date,
    p.end_date,
    pb.status AS baseline_status,
    -- 成员统计（去重用户数，仅 ACTIVE 成员）
    (SELECT COUNT(DISTINCT pm.user_id)
     FROM project_member pm
     WHERE pm.project_id = p.id
       AND pm.status = 'ACTIVE') AS member_count,
    -- 里程碑统计（按生效基线）
    (SELECT COUNT(*) FROM milestone m
     WHERE m.baseline_id = pb.id
       AND m.status <> 'CANCELLED') AS milestone_total,
    (SELECT COUNT(*) FROM milestone m
     WHERE m.baseline_id = pb.id
       AND m.status = 'COMPLETED') AS milestone_completed,
    -- 报告统计（迟报以版本 late_flag 为唯一事实来源，修复 #23）
    (SELECT COUNT(*) FROM progress_report pr
     WHERE pr.project_id = p.id) AS report_total,
    (SELECT COUNT(*) FROM progress_report pr
     JOIN progress_report_version prv ON prv.id = pr.current_version_id
     WHERE pr.project_id = p.id
       AND prv.late_flag = true) AS report_late_count,
    now() AS data_as_of
FROM project p
LEFT JOIN project_baseline pb
       ON pb.project_id = p.id AND pb.status = 'EFFECTIVE'
LEFT JOIN org_unit ou ON ou.id = p.dept_id;

-- 唯一索引（CONCURRENTLY 刷新必需）
CREATE UNIQUE INDEX uk_mv_project_summary_id ON mv_project_summary(project_id);
CREATE INDEX idx_mv_project_summary_dept ON mv_project_summary(dept_id);
CREATE INDEX idx_mv_project_summary_status ON mv_project_summary(status);

-- -----------------------------------------------------------------------------
-- 2. 经费物化视图 (RPT-03 经费统计)
-- 口径：budget_balance 按 budget_line 投影；生效预算版本 = budget_version.EFFECTIVE
-- -----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_funding_summary AS
SELECT
    bv.project_id,
    bv.id AS budget_version_id,
    bl.subject_code,
    bl.subject_name,
    bl.amount AS planned_amount,
    bb.approved_amount,
    bb.reserved_amount,
    bb.approved_unpaid_amount,
    bb.paid_amount,
    -- 可用余额 = 批准额 - 占用 - 批准未付 - 已付（可用于新支出的剩余额度）
    bb.approved_amount - bb.reserved_amount - bb.approved_unpaid_amount - bb.paid_amount
        AS available_balance,
    -- 执行率 = 已支付 / 预算科目计划额
    CASE WHEN bl.amount > 0 THEN
        ROUND(100.0 * bb.paid_amount / bl.amount, 2)
    ELSE 0 END AS execution_rate,
    now() AS data_as_of
FROM budget_version bv
JOIN budget_line bl ON bl.budget_version_id = bv.id
JOIN budget_balance bb ON bb.budget_line_id = bl.id
WHERE bv.status = 'EFFECTIVE';

CREATE UNIQUE INDEX uk_mv_funding_summary
    ON mv_funding_summary(budget_version_id, subject_code);
CREATE INDEX idx_mv_funding_summary_project ON mv_funding_summary(project_id);
CREATE INDEX idx_mv_funding_summary_rate ON mv_funding_summary(execution_rate);

-- -----------------------------------------------------------------------------
-- 3. 成果物化视图 (RPT-04 成果统计)
-- 去重规则：全院口径按 research_output.id 去重；REVOKED（撤回）不计入
-- -----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_output_summary AS
SELECT
    p.id AS project_id,
    p.project_no,
    ro.output_type,
    COUNT(DISTINCT ro.id) AS output_count,
    COUNT(DISTINCT CASE WHEN ro.status = 'APPROVED' THEN ro.id END) AS approved_count,
    COUNT(DISTINCT CASE WHEN ro.status = 'SUBMITTED' THEN ro.id END) AS submitted_count,
    COUNT(DISTINCT CASE WHEN ro.status = 'REVOKED' THEN ro.id END) AS revoked_count,
    -- 多项目关联成果数（该成果同时关联其他项目）
    COUNT(DISTINCT CASE WHEN (
        SELECT COUNT(*) FROM output_project_relation opr2
        WHERE opr2.output_id = ro.id
          AND opr2.status = 'ACTIVE'
    ) > 1 THEN ro.id END) AS cross_project_count,
    now() AS data_as_of
FROM project p
JOIN output_project_relation opr
     ON opr.project_id = p.id AND opr.status = 'ACTIVE'
JOIN research_output ro
     ON ro.id = opr.output_id AND ro.status <> 'REVOKED'
GROUP BY p.id, p.project_no, ro.output_type;

CREATE UNIQUE INDEX uk_mv_output_summary
    ON mv_output_summary(project_id, output_type);
CREATE INDEX idx_mv_output_summary_type ON mv_output_summary(output_type);

-- -----------------------------------------------------------------------------
-- 4. 风险物化视图 (RPT-06 领导风险视图)
-- -----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW mv_risk_summary AS
SELECT
    p.id AS project_id,
    p.project_no,
    p.title AS project_title,
    p.status AS project_status,
    p.dept_id,
    pr.id AS risk_id,
    pr.risk_type,
    pr.risk_level,
    pr.status AS risk_status,
    pr.title AS risk_title,
    pr.owner_id,
    pr.due_date,
    pr.source_rule,
    -- 超期天数（未关闭且已过期限）
    CASE WHEN pr.status IN ('OPEN','PROCESSING') AND pr.due_date < CURRENT_DATE THEN
        CURRENT_DATE - pr.due_date
    ELSE 0 END AS overdue_days,
    now() AS data_as_of
FROM project p
JOIN project_risk pr ON pr.project_id = p.id;

CREATE UNIQUE INDEX uk_mv_risk_summary ON mv_risk_summary(risk_id);
CREATE INDEX idx_mv_risk_summary_level ON mv_risk_summary(risk_level);
CREATE INDEX idx_mv_risk_summary_overdue ON mv_risk_summary(overdue_days) WHERE overdue_days > 0;
CREATE INDEX idx_mv_risk_summary_status ON mv_risk_summary(risk_status);

-- -----------------------------------------------------------------------------
-- 5. 物化视图刷新日志
-- -----------------------------------------------------------------------------
CREATE TABLE mv_refresh_log (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    view_name           varchar(128) NOT NULL,
    refresh_started_at  timestamptz NOT NULL DEFAULT now(),
    refresh_ended_at    timestamptz,
    status              varchar(32) NOT NULL DEFAULT 'RUNNING',
    row_count           bigint,
    error_message       text,
    triggered_by        varchar(16) NOT NULL DEFAULT 'SCHEDULE', -- SCHEDULE / MANUAL
    operator_id         uuid,
    CONSTRAINT ck_mv_refresh_status CHECK (status IN
        ('RUNNING','SUCCESS','FAILED','SKIPPED'))
);

CREATE INDEX idx_mv_refresh_log_view_time ON mv_refresh_log(view_name, refresh_started_at DESC);
CREATE INDEX idx_mv_refresh_log_status ON mv_refresh_log(status, refresh_started_at DESC);

-- -----------------------------------------------------------------------------
-- 6. 刷新协调函数
-- 按依赖顺序刷新；某视图失败后，后续视图记录 SKIPPED，保留各视图上次数据
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION refresh_all_materialized_views(
    p_triggered_by varchar DEFAULT 'SCHEDULE'
)
RETURNS TABLE(view_name varchar, status varchar, row_count bigint, error_msg text)
LANGUAGE plpgsql
AS $$
DECLARE
    v_log_id uuid;
    v_views text[] := ARRAY[
        'mv_project_summary',
        'mv_funding_summary',
        'mv_output_summary',
        'mv_risk_summary'
    ];
    v_view text;
    v_remaining text;
    v_row_count bigint;
    v_failed boolean := false;
BEGIN
    FOREACH v_view IN ARRAY v_views
    LOOP
        IF v_failed THEN
            -- 依赖顺序：前置失败后，后续视图全部 SKIPPED（保留上次数据）
            INSERT INTO mv_refresh_log(view_name, status, triggered_by, refresh_ended_at)
            VALUES (v_view, 'SKIPPED', p_triggered_by, now());
            RETURN QUERY SELECT v_view, 'SKIPPED'::varchar, NULL::bigint,
                                '前置视图刷新失败'::text;
            CONTINUE;
        END IF;

        BEGIN
            INSERT INTO mv_refresh_log(view_name, status, triggered_by)
            VALUES (v_view, 'RUNNING', p_triggered_by)
            RETURNING id INTO v_log_id;

            EXECUTE 'REFRESH MATERIALIZED VIEW CONCURRENTLY ' || v_view;
            EXECUTE 'SELECT COUNT(*) FROM ' || v_view INTO v_row_count;

            UPDATE mv_refresh_log
            SET status = 'SUCCESS', refresh_ended_at = now(), row_count = v_row_count
            WHERE id = v_log_id;

            RETURN QUERY SELECT v_view, 'SUCCESS'::varchar, v_row_count, NULL::text;

        EXCEPTION WHEN OTHERS THEN
            UPDATE mv_refresh_log
            SET status = 'FAILED', refresh_ended_at = now(), error_message = SQLERRM
            WHERE id = v_log_id;

            RETURN QUERY SELECT v_view, 'FAILED'::varchar, NULL::bigint, SQLERRM;
            v_failed := true;
        END;
    END LOOP;
END;
$$;

-- -----------------------------------------------------------------------------
-- 7. 按需刷新单个视图（手工补偿入口）
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION refresh_single_materialized_view(
    p_view_name varchar,
    p_operator_id uuid DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
AS $$
DECLARE
    v_log_id uuid;
    v_row_count bigint;
BEGIN
    -- 仅允许刷新白名单视图，防注入
    IF p_view_name NOT IN
        ('mv_project_summary','mv_funding_summary','mv_output_summary','mv_risk_summary')
    THEN
        RAISE EXCEPTION 'unknown materialized view: %', p_view_name;
    END IF;

    INSERT INTO mv_refresh_log(view_name, status, triggered_by, operator_id)
    VALUES (p_view_name, 'RUNNING', 'MANUAL', p_operator_id)
    RETURNING id INTO v_log_id;

    EXECUTE 'REFRESH MATERIALIZED VIEW CONCURRENTLY ' || p_view_name;
    EXECUTE 'SELECT COUNT(*) FROM ' || p_view_name INTO v_row_count;

    UPDATE mv_refresh_log
    SET status = 'SUCCESS', refresh_ended_at = now(), row_count = v_row_count
    WHERE id = v_log_id;

    RETURN v_log_id;

EXCEPTION WHEN OTHERS THEN
    UPDATE mv_refresh_log
    SET status = 'FAILED', refresh_ended_at = now(), error_message = SQLERRM
    WHERE id = v_log_id;
    RAISE;
END;
$$;

-- -----------------------------------------------------------------------------
-- 8. 初始数据填充
-- CONCURRENTLY 刷新要求视图已填充；首次建库用非并发刷新（绿地库无并发）
-- -----------------------------------------------------------------------------
REFRESH MATERIALIZED VIEW mv_project_summary;
REFRESH MATERIALIZED VIEW mv_funding_summary;
REFRESH MATERIALIZED VIEW mv_output_summary;
REFRESH MATERIALIZED VIEW mv_risk_summary;
