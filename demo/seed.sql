-- 本地联调专用数据，不纳入 Flyway，不会自动在其他环境执行。
-- 用途：准备组织/人员/项目/申报/预算/执行等下拉选项与联调数据。

BEGIN;

-- 业务编号规则（创建申报、项目、支出、资金事件、支付导入、对账批次）
INSERT INTO number_rule (
    id, rule_code, prefix, date_pattern, sequence_name, padding, reset_cycle, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'c0a80001-0000-0000-0000-000000000031',
    'APPLICATION_NO', 'SB', 'yyyy', 'seq_application_no', 6, 'YEAR', 'ACTIVE',
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000032',
    'PROJECT_NO', 'KY', 'yyyy', 'seq_project_no', 6, 'YEAR', 'ACTIVE',
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000033',
    'EXPENSE_REQUEST_NO', 'ZC', 'yyyy', 'seq_expense_request_no', 6, 'YEAR', 'ACTIVE',
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000034',
    'FUND_EVENT_NO', 'ZJ', 'yyyy', 'seq_fund_event_no', 6, 'YEAR', 'ACTIVE',
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000035',
    'PAYMENT_IMPORT_BATCH_NO', 'ZF', 'yyyy', 'seq_payment_import_batch_no', 6, 'YEAR', 'ACTIVE',
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000036',
    'RECONCILIATION_BATCH_NO', 'DZ', 'yyyy', 'seq_reconciliation_batch_no', 6, 'YEAR', 'ACTIVE',
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (rule_code) DO UPDATE SET
    prefix = EXCLUDED.prefix,
    date_pattern = EXCLUDED.date_pattern,
    sequence_name = EXCLUDED.sequence_name,
    padding = EXCLUDED.padding,
    reset_cycle = EXCLUDED.reset_cycle,
    status = EXCLUDED.status,
    updated_at = now();

-- 周期报告规则（月报/季报均使用默认 5 天到期缓冲）
INSERT INTO report_period_rule (
    id, project_type, period_type, due_offset_days, late_grace_days, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES
('c0a80001-0000-0000-0000-000000000041', 'GENERAL', 'MONTH', 5, 3, 'ACTIVE',
 now(), now(), 'c0a80001-0000-0000-0000-000000000001',
 'c0a80001-0000-0000-0000-000000000001', 0),
('c0a80001-0000-0000-0000-000000000042', 'GENERAL', 'QUARTER', 5, 3, 'ACTIVE',
 now(), now(), 'c0a80001-0000-0000-0000-000000000001',
 'c0a80001-0000-0000-0000-000000000001', 0),
('c0a80001-0000-0000-0000-000000000043', 'KEY', 'MONTH', 5, 3, 'ACTIVE',
 now(), now(), 'c0a80001-0000-0000-0000-000000000001',
 'c0a80001-0000-0000-0000-000000000001', 0),
('c0a80001-0000-0000-0000-000000000044', 'KEY', 'QUARTER', 5, 3, 'ACTIVE',
 now(), now(), 'c0a80001-0000-0000-0000-000000000001',
 'c0a80001-0000-0000-0000-000000000001', 0),
('c0a80001-0000-0000-0000-000000000045', 'YOUTH', 'MONTH', 5, 3, 'ACTIVE',
 now(), now(), 'c0a80001-0000-0000-0000-000000000001',
 'c0a80001-0000-0000-0000-000000000001', 0),
('c0a80001-0000-0000-0000-000000000046', 'YOUTH', 'QUARTER', 5, 3, 'ACTIVE',
 now(), now(), 'c0a80001-0000-0000-0000-000000000001',
 'c0a80001-0000-0000-0000-000000000001', 0)
ON CONFLICT (project_type, period_type) DO UPDATE SET
    due_offset_days = EXCLUDED.due_offset_days,
    late_grace_days = EXCLUDED.late_grace_days,
    status = EXCLUDED.status,
    updated_at = now();

-- 部门与人员（下拉初始化）
INSERT INTO org_unit (
    id, parent_id, code, name, unit_type, path, sort_order, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'c0a80001-0000-0000-0000-000000000011',
    'c0a80001-0000-0000-0000-000000000010',
    'DEPT-IST', '信息科学学院', 'DEPARTMENT', '/ROOT/DEPT-IST', 10, 'ACTIVE',
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000012',
    'c0a80001-0000-0000-0000-000000000010',
    'DEPT-MSE', '材料科学学院', 'DEPARTMENT', '/ROOT/DEPT-MSE', 20, 'ACTIVE',
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000013',
    'c0a80001-0000-0000-0000-000000000010',
    'DEPT-ORD', '科研管理处', 'DEPARTMENT', '/ROOT/DEPT-ORD', 30, 'ACTIVE',
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO iam_user (
    id, employee_no, display_name, dept_id, email, status,
    password_hash, created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'c0a80001-0000-0000-0000-000000000021',
    'T2026001', '陈晓明', 'c0a80001-0000-0000-0000-000000000011',
    'chenxiaoming@example.edu.cn', 'ACTIVE',
    (SELECT password_hash FROM iam_user WHERE employee_no = 'admin'),
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000022',
    'T2026002', '李静', 'c0a80001-0000-0000-0000-000000000012',
    'lijing@example.edu.cn', 'ACTIVE',
    (SELECT password_hash FROM iam_user WHERE employee_no = 'admin'),
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000023',
    'T2026003', '王建国', 'c0a80001-0000-0000-0000-000000000013',
    'wangjianguo@example.edu.cn', 'ACTIVE',
    (SELECT password_hash FROM iam_user WHERE employee_no = 'admin'),
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'c0a80001-0000-0000-0000-000000000024',
    'T2026004', '赵敏', 'c0a80001-0000-0000-0000-000000000013',
    'zhaomin@example.edu.cn', 'ACTIVE',
    (SELECT password_hash FROM iam_user WHERE employee_no = 'admin'),
    now(), now(), 'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

-- 本地演示账号统一授予管理员角色，便于分别验证申请人/复核人等职责分离流程。
-- 仅用于本地联调，不得复制到测试或生产环境。
INSERT INTO iam_user_role (
    id, user_id, role_id, status, valid_from, created_at, updated_at,
    created_by, updated_by, version
)
SELECT
    seed_role.id, seed_role.user_id, r.id, 'ACTIVE', now(), now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
FROM (
    VALUES
        ('c0a80001-0000-0000-0000-000000000051'::uuid,
         'c0a80001-0000-0000-0000-000000000021'::uuid),
        ('c0a80001-0000-0000-0000-000000000052'::uuid,
         'c0a80001-0000-0000-0000-000000000022'::uuid),
        ('c0a80001-0000-0000-0000-000000000053'::uuid,
         'c0a80001-0000-0000-0000-000000000023'::uuid),
        ('c0a80001-0000-0000-0000-000000000054'::uuid,
         'c0a80001-0000-0000-0000-000000000024'::uuid)
) AS seed_role(id, user_id)
JOIN iam_role r ON r.code = 'ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM iam_user_role ur
    WHERE ur.user_id = seed_role.user_id AND ur.role_id = r.id
)
ON CONFLICT (id) DO NOTHING;

-- 项目（项目下拉初始化）
INSERT INTO project (
    id, project_no, title, project_type, dept_id, pi_id, status, source_type,
    start_date, end_date, created_at, updated_at, created_by, updated_by, version
) VALUES (
    'a0000001-0000-0000-0000-000000000001',
    'LOCAL-2026-001',
    '本地联调科研项目',
    'GENERAL',
    'c0a80001-0000-0000-0000-000000000010',
    'c0a80001-0000-0000-0000-000000000001',
    'EXECUTING',
    'INTERNAL',
    CURRENT_DATE,
    CURRENT_DATE + INTERVAL '1 year',
    now(),
    now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO project (
    id, project_no, title, project_type, dept_id, pi_id, status, source_type,
    start_date, end_date, created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a0000001-0000-0000-0000-000000000002',
    'LOCAL-2026-002',
    '人工智能辅助科研项目管理',
    'KEY',
    'c0a80001-0000-0000-0000-000000000011',
    'c0a80001-0000-0000-0000-000000000021',
    'EXECUTING',
    'INTERNAL',
    CURRENT_DATE - INTERVAL '30 days',
    CURRENT_DATE + INTERVAL '1 year',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000001-0000-0000-0000-000000000003',
    'LOCAL-2026-003',
    '高性能复合材料制备与评价',
    'YOUTH',
    'c0a80001-0000-0000-0000-000000000012',
    'c0a80001-0000-0000-0000-000000000022',
    'PENDING_START',
    'INTERNAL',
    CURRENT_DATE + INTERVAL '10 days',
    CURRENT_DATE + INTERVAL '2 years',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

-- 项目成员（责任人/成员下拉初始化）
INSERT INTO project_member (
    id, project_id, user_id, project_role, contribution_desc,
    valid_from, status, created_at, updated_at, created_by, updated_by, version
) VALUES (
    'a0000004-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001',
    'PI',
    '本地联调项目负责人',
    now() - INTERVAL '1 day',
    'ACTIVE',
    now(),
    now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001',
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO project_member (
    id, project_id, user_id, project_role, contribution_desc,
    valid_from, status, created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a0000004-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000021',
    'CORE_MEMBER', '负责人工智能算法与平台研发',
    now() - INTERVAL '1 day', 'ACTIVE', now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000004-0000-0000-0000-000000000003',
    'a0000001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000022',
    'MEMBER', '负责材料实验与数据采集',
    now() - INTERVAL '1 day', 'ACTIVE', now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000004-0000-0000-0000-000000000004',
    'a0000001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000023',
    'MEMBER', '负责科研管理与过程协调',
    now() - INTERVAL '1 day', 'ACTIVE', now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000004-0000-0000-0000-000000000005',
    'a0000001-0000-0000-0000-000000000002',
    'c0a80001-0000-0000-0000-000000000021',
    'PI', '项目负责人',
    now() - INTERVAL '30 days', 'ACTIVE', now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000004-0000-0000-0000-000000000006',
    'a0000001-0000-0000-0000-000000000003',
    'c0a80001-0000-0000-0000-000000000022',
    'PI', '项目负责人',
    now() - INTERVAL '1 day', 'ACTIVE', now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

-- 文件与交付物
INSERT INTO file_object (
    id, bucket, object_key, original_name, mime_type, size_bytes, sha256,
    status, scan_status, uploaded_by, created_at, updated_at, version
) VALUES (
    'a0000002-0000-0000-0000-000000000001',
    'rpm-files',
    'local/demo-deliverable.txt',
    'demo-deliverable.txt',
    'text/plain',
    0,
    repeat('0', 64),
    'AVAILABLE',
    'PASSED',
    'c0a80001-0000-0000-0000-000000000001',
    now(),
    now(),
    0
)
ON CONFLICT (id) DO NOTHING;

-- 修正早期种子使用过的桶名，统一到 application-dev.yml 的 rpm-files。
UPDATE file_object
SET bucket = 'rpm-files', updated_at = now()
WHERE id IN (
    'a0000002-0000-0000-0000-000000000001',
    'a0000002-0000-0000-0000-000000000002'
)
  AND bucket <> 'rpm-files';

INSERT INTO file_object (
    id, bucket, object_key, original_name, mime_type, size_bytes, sha256,
    status, scan_status, uploaded_by, created_at, updated_at, version
) VALUES (
    'a0000002-0000-0000-0000-000000000002',
    'rpm-files',
    'local/demo-progress-report.txt',
    'demo-progress-report.txt',
    'text/plain',
    0,
    repeat('1', 64),
    'AVAILABLE',
    'SKIPPED',
    'c0a80001-0000-0000-0000-000000000001',
    now(), now(), 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO deliverable (
    id, project_id, file_id, title, sha256, status, uploaded_by,
    created_at, updated_at, version
) VALUES (
    'a0000003-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    'a0000002-0000-0000-0000-000000000001',
    '本地联调交付物',
    repeat('0', 64),
    'ACTIVE',
    'c0a80001-0000-0000-0000-000000000001',
    now(),
    now(),
    0
)
ON CONFLICT (id) DO NOTHING;

-- 项目基线（里程碑与预算所依赖的生效基线）
INSERT INTO project_baseline (
    id, project_id, version_no, snapshot, snapshot_hash, effective_at, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a0000007-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    1,
    '{"source":"local-seed","title":"本地联调科研项目"}'::jsonb,
    repeat('a', 64),
    now() - INTERVAL '30 days',
    'EFFECTIVE',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000007-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000002',
    1,
    '{"source":"local-seed","title":"人工智能辅助科研项目管理"}'::jsonb,
    repeat('b', 64),
    now() - INTERVAL '30 days',
    'EFFECTIVE',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

UPDATE project
SET current_baseline_id = 'a0000007-0000-0000-0000-000000000001'
WHERE id = 'a0000001-0000-0000-0000-000000000001'
  AND current_baseline_id IS NULL;

UPDATE project
SET current_baseline_id = 'a0000007-0000-0000-0000-000000000002'
WHERE id = 'a0000001-0000-0000-0000-000000000002'
  AND current_baseline_id IS NULL;

-- 里程碑（交付物与任务下拉初始化）
INSERT INTO milestone (
    id, project_id, baseline_id, code, title, plan_date, current_plan_date,
    owner_id, completion_criteria, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a0000008-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    'a0000007-0000-0000-0000-000000000001',
    'MS-001', '需求调研完成',
    CURRENT_DATE - INTERVAL '20 days',
    CURRENT_DATE - INTERVAL '20 days',
    'c0a80001-0000-0000-0000-000000000001',
    '形成需求调研报告并通过评审', 'COMPLETED',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000008-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000001',
    'a0000007-0000-0000-0000-000000000001',
    'MS-002', '中期成果交付',
    CURRENT_DATE + INTERVAL '90 days',
    CURRENT_DATE + INTERVAL '90 days',
    'c0a80001-0000-0000-0000-000000000021',
    '完成中期报告与原型系统', 'IN_PROGRESS',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000008-0000-0000-0000-000000000003',
    'a0000001-0000-0000-0000-000000000002',
    'a0000007-0000-0000-0000-000000000002',
    'MS-001', '算法方案定稿',
    CURRENT_DATE + INTERVAL '60 days',
    CURRENT_DATE + INTERVAL '60 days',
    'c0a80001-0000-0000-0000-000000000021',
    '算法方案通过专家评审', 'PLANNED',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO deliverable (
    id, project_id, milestone_id, file_id, title, sha256, status, uploaded_by,
    created_at, updated_at, version
) VALUES (
    'a0000003-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000001',
    'a0000008-0000-0000-0000-000000000001',
    'a0000002-0000-0000-0000-000000000002',
    '需求调研报告',
    repeat('1', 64),
    'ACTIVE',
    'c0a80001-0000-0000-0000-000000000001',
    now(), now(), 0
)
ON CONFLICT (id) DO NOTHING;

-- 预算版本、科目与余额（经费下拉初始化）
INSERT INTO budget_version (
    id, project_id, version_no, total_amount, source_type, control_level, status,
    effective_from, approved_by, approved_at,
    created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a0000005-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    1, 1000000.00, 'GRANT', 'BLOCK', 'EFFECTIVE',
    CURRENT_DATE - INTERVAL '30 days',
    'c0a80001-0000-0000-0000-000000000001', now(),
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000005-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000002',
    1, 600000.00, 'GRANT', 'BLOCK', 'EFFECTIVE',
    CURRENT_DATE - INTERVAL '30 days',
    'c0a80001-0000-0000-0000-000000000001', now(),
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO budget_line (
    id, budget_version_id, subject_code, subject_name, amount, control_level,
    created_at, updated_at, version
) VALUES
(
    'a0000006-0000-0000-0000-000000000001',
    'a0000005-0000-0000-0000-000000000001',
    'DEVICE', '设备费', 500000.00, 'BLOCK', now(), now(), 0
),
(
    'a0000006-0000-0000-0000-000000000002',
    'a0000005-0000-0000-0000-000000000001',
    'MATERIAL', '材料费', 300000.00, 'BLOCK', now(), now(), 0
),
(
    'a0000006-0000-0000-0000-000000000003',
    'a0000005-0000-0000-0000-000000000001',
    'TRAVEL', '差旅/会议费', 200000.00, 'WARN', now(), now(), 0
),
(
    'a0000006-0000-0000-0000-000000000004',
    'a0000005-0000-0000-0000-000000000002',
    'SOFTWARE', '软件与数据服务费', 600000.00, 'BLOCK', now(), now(), 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO budget_balance (
    id, budget_line_id, approved_amount, reserved_amount,
    approved_unpaid_amount, paid_amount, version, updated_at
) VALUES
(
    'a0000009-0000-0000-0000-000000000001',
    'a0000006-0000-0000-0000-000000000001',
    500000.00, 0, 0, 0, 0, now()
),
(
    'a0000009-0000-0000-0000-000000000002',
    'a0000006-0000-0000-0000-000000000002',
    300000.00, 0, 0, 0, 0, now()
),
(
    'a0000009-0000-0000-0000-000000000003',
    'a0000006-0000-0000-0000-000000000003',
    200000.00, 0, 0, 0, 0, now()
),
(
    'a0000009-0000-0000-0000-000000000004',
    'a0000006-0000-0000-0000-000000000004',
    600000.00, 0, 0, 0, 0, now()
)
ON CONFLICT (id) DO NOTHING;

-- 周期报告、风险、任务与成果示例
INSERT INTO progress_report (
    id, project_id, period_type, period_key, due_date, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES (
    'a000000b-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    'MONTH', '2026-11', CURRENT_DATE + INTERVAL '5 days', 'DRAFT',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO project_risk (
    id, project_id, risk_type, title, description, risk_level, owner_id,
    due_date, status, created_at, updated_at, created_by, updated_by, version
) VALUES (
    'a000000c-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    'TECHNICAL', '原型系统性能未达预期', '高并发场景响应时间偏长，需要优化索引与缓存。',
    'MEDIUM', 'c0a80001-0000-0000-0000-000000000021',
    CURRENT_DATE + INTERVAL '21 days', 'OPEN',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO project_task (
    id, project_id, title, description, assignee_id, due_date, status,
    deliverable_id, created_at, updated_at, created_by, updated_by, version
) VALUES (
    'a000000a-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    '整理中期检查材料', '汇总阶段成果与预算执行情况。',
    'c0a80001-0000-0000-0000-000000000023',
    CURRENT_DATE + INTERVAL '14 days', 'TODO',
    'a0000003-0000-0000-0000-000000000001',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO research_output (
    id, output_type, title, doi, owner_id, dept_id, completed_at, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES (
    'a000000d-0000-0000-0000-000000000001',
    'PAPER', '科研项目全生命周期数据治理实践', '10.1000/rpm.2026.001',
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000010',
    CURRENT_DATE - INTERVAL '10 days', 'APPROVED',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO output_version (
    id, output_id, version_no, metadata, content_hash, created_by, created_at
) VALUES (
    'a000000e-0000-0000-0000-000000000001',
    'a000000d-0000-0000-0000-000000000001',
    1,
    '{"journal":"科研管理","year":2026}'::jsonb,
    repeat('c', 64),
    'c0a80001-0000-0000-0000-000000000001',
    now()
)
ON CONFLICT (id) DO NOTHING;

UPDATE research_output
SET current_version_id = 'a000000e-0000-0000-0000-000000000001'
WHERE id = 'a000000d-0000-0000-0000-000000000001'
  AND current_version_id IS NULL;

INSERT INTO output_project_relation (
    id, output_id, project_id, contribution_desc, attribution_ratio, status,
    created_by, created_at, updated_at, version
) VALUES (
    'a000000f-0000-0000-0000-000000000001',
    'a000000d-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000001',
    '成果基于本项目的数据治理实践形成', 0.8000, 'ACTIVE',
    'c0a80001-0000-0000-0000-000000000001',
    now(), now(), 0
)
ON CONFLICT (id) DO NOTHING;

-- 申报批次与表单模板（新建申报下拉初始化）
INSERT INTO apply_batch (
    id, batch_no, year, name, start_at, end_at, status, rules,
    created_at, updated_at, created_by, updated_by, version
) VALUES (
    'b0000002-0000-0000-0000-000000000001',
    'LOCAL-2026-OPEN',
    2026,
    '2026 年度院内科研项目申报',
    now() - INTERVAL '30 days',
    now() + INTERVAL '365 days',
    'OPEN',
    '{"maxApplicationsPerApplicant":3,"projectTypes":["GENERAL","KEY","YOUTH"]}'::jsonb,
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO apply_form_template (
    id, project_type, version_no, form_schema, status,
    effective_from, effective_to, published_by, published_at,
    created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'b0000003-0000-0000-0000-000000000001',
    'GENERAL', 1,
    '{
      "type":"object",
      "required":[],
      "requiredAttachments":[],
      "properties":{
        "researchContent":{"type":"string","title":"研究内容","maxLength":4000},
        "expectedOutcome":{"type":"string","title":"预期成果","maxLength":1000},
        "requestedBudget":{"type":"number","title":"申请金额","minimum":0,"maximum":100000000}
      }
    }'::jsonb,
    'PUBLISHED',
    now() - INTERVAL '1 day', now() + INTERVAL '1 year',
    'c0a80001-0000-0000-0000-000000000001', now(),
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'b0000003-0000-0000-0000-000000000002',
    'KEY', 1,
    '{
      "type":"object",
      "required":[],
      "requiredAttachments":[],
      "properties":{
        "researchContent":{"type":"string","title":"研究内容","maxLength":4000},
        "expectedOutcome":{"type":"string","title":"预期成果","maxLength":1000},
        "requestedBudget":{"type":"number","title":"申请金额","minimum":0,"maximum":100000000}
      }
    }'::jsonb,
    'PUBLISHED',
    now() - INTERVAL '1 day', now() + INTERVAL '1 year',
    'c0a80001-0000-0000-0000-000000000001', now(),
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'b0000003-0000-0000-0000-000000000003',
    'YOUTH', 1,
    '{
      "type":"object",
      "required":[],
      "requiredAttachments":[],
      "properties":{
        "researchContent":{"type":"string","title":"研究内容","maxLength":4000},
        "expectedOutcome":{"type":"string","title":"预期成果","maxLength":1000},
        "requestedBudget":{"type":"number","title":"申请金额","minimum":0,"maximum":100000000}
      }
    }'::jsonb,
    'PUBLISHED',
    now() - INTERVAL '1 day', now() + INTERVAL '1 year',
    'c0a80001-0000-0000-0000-000000000001', now(),
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

-- 本地申报审批模板（与 dev 环境 rpmTestApproval 流程定义配套）
INSERT INTO wf_template (
    id, code, name, project_type, condition_schema, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES (
    'b0000004-0000-0000-0000-000000000001',
    'LOCAL_APPLICATION_APPROVAL',
    '本地申报审批模板',
    NULL,
    '{}'::jsonb,
    'PUBLISHED',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO wf_template_version (
    id, template_id, version_no, definition, condition_expr,
    effective_from, effective_to, status, published_by, published_at,
    created_at, updated_at, created_by, updated_by, version
) VALUES (
    'b0000005-0000-0000-0000-000000000001',
    'b0000004-0000-0000-0000-000000000001',
    1,
    '{"nodes":[{"key":"formalReview","name":"形式审查"},{"key":"decision","name":"立项决策"}]}'::jsonb,
    '{}'::jsonb,
    now() - INTERVAL '1 day',
    now() + INTERVAL '1 year',
    'PUBLISHED',
    'c0a80001-0000-0000-0000-000000000001',
    now(),
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

UPDATE wf_template
SET current_version_id = 'b0000005-0000-0000-0000-000000000001'
WHERE id = 'b0000004-0000-0000-0000-000000000001'
  AND current_version_id IS NULL;

-- 本地支出审批模板（与 expenseApproval 流程定义配套）
INSERT INTO wf_template (
    id, code, name, project_type, condition_schema, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES (
    'b0000004-0000-0000-0000-000000000002',
    'EXPENSE_APPROVAL',
    '支出审批模板',
    NULL,
    '{}'::jsonb,
    'PUBLISHED',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO wf_template_version (
    id, template_id, version_no, definition, condition_expr,
    effective_from, effective_to, status, published_by, published_at,
    created_at, updated_at, created_by, updated_by, version
) VALUES (
    'b0000005-0000-0000-0000-000000000002',
    'b0000004-0000-0000-0000-000000000002',
    1,
    '{"nodes":[{"key":"expenseReview","name":"经费审批"}]}'::jsonb,
    '{}'::jsonb,
    now() - INTERVAL '1 day',
    now() + INTERVAL '1 year',
    'PUBLISHED',
    'c0a80001-0000-0000-0000-000000000001',
    now(),
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

UPDATE wf_template
SET current_version_id = 'b0000005-0000-0000-0000-000000000002'
WHERE id = 'b0000004-0000-0000-0000-000000000002'
  AND current_version_id IS NULL;

-- 申报示例（列表/详情/下拉初始化）
INSERT INTO application (
    id, application_no, batch_id, project_type, applicant_id, dept_id,
    title, source_type, status, current_version_id, version,
    created_at, updated_at, created_by, updated_by
) VALUES
(
    'd0000001-0000-0000-0000-000000000001',
    'SB-2026-900001',
    'b0000002-0000-0000-0000-000000000001',
    'GENERAL',
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000010',
    '本地演示申报（草稿）',
    'INTERNAL', 'DRAFT', NULL, 1,
    now() - INTERVAL '2 days', now() - INTERVAL '1 day',
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001'
),
(
    'd0000001-0000-0000-0000-000000000002',
    'SB-2026-900002',
    'b0000002-0000-0000-0000-000000000001',
    'KEY',
    'c0a80001-0000-0000-0000-000000000021',
    'c0a80001-0000-0000-0000-000000000011',
    '本地演示申报（审批中）',
    'INTERNAL', 'IN_REVIEW', NULL, 1,
    now() - INTERVAL '3 days', now() - INTERVAL '2 days',
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO application_version (
    id, application_id, version_no, form_data, content_hash, status,
    submitted_by, submitted_at, submit_idempotency_key,
    created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'd0000002-0000-0000-0000-000000000001',
    'd0000001-0000-0000-0000-000000000001',
    1,
    '{"researchContent":"本地演示草稿内容","expectedOutcome":"形成演示成果","requestedBudget":120000}'::jsonb,
    repeat('a', 64),
    'DRAFT', NULL, NULL, NULL,
    now() - INTERVAL '1 day', now() - INTERVAL '1 day',
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'd0000002-0000-0000-0000-000000000002',
    'd0000001-0000-0000-0000-000000000002',
    1,
    '{"researchContent":"重点项目研究内容","expectedOutcome":"形成重点成果","requestedBudget":500000}'::jsonb,
    repeat('b', 64),
    'SUBMITTED',
    'c0a80001-0000-0000-0000-000000000021', now() - INTERVAL '2 days',
    'local-seed-application-2',
    now() - INTERVAL '2 days', now() - INTERVAL '2 days',
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

UPDATE application
SET current_version_id = 'd0000002-0000-0000-0000-000000000002'
WHERE id = 'd0000001-0000-0000-0000-000000000002'
  AND current_version_id IS NULL;

INSERT INTO application_member (
    id, application_version_id, user_id, member_role, contribution_desc, sort_order,
    created_at
) VALUES
(
    'd0000003-0000-0000-0000-000000000001',
    'd0000002-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001',
    'PI', '申报负责人', 0, now() - INTERVAL '1 day'
),
(
    'd0000003-0000-0000-0000-000000000002',
    'd0000002-0000-0000-0000-000000000002',
    'c0a80001-0000-0000-0000-000000000021',
    'PI', '重点项目负责人', 0, now() - INTERVAL '2 days'
)
ON CONFLICT (id) DO NOTHING;

-- 项目 2/3 的基线、里程碑、交付物、成员、预算与任务（下拉初始化）
INSERT INTO project_baseline (
    id, project_id, version_no, snapshot, snapshot_hash, effective_at, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES (
    'a0000007-0000-0000-0000-000000000003',
    'a0000001-0000-0000-0000-000000000003',
    1,
    '{"source":"local-seed","title":"高性能复合材料制备与评价"}'::jsonb,
    repeat('d', 64),
    now() - INTERVAL '1 day',
    'EFFECTIVE',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

UPDATE project
SET current_baseline_id = 'a0000007-0000-0000-0000-000000000003'
WHERE id = 'a0000001-0000-0000-0000-000000000003'
  AND current_baseline_id IS NULL;

INSERT INTO milestone (
    id, project_id, baseline_id, code, title, plan_date, current_plan_date,
    owner_id, completion_criteria, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a0000008-0000-0000-0000-000000000004',
    'a0000001-0000-0000-0000-000000000003',
    'a0000007-0000-0000-0000-000000000003',
    'MS-001', '材料体系设计评审',
    CURRENT_DATE + INTERVAL '30 days',
    CURRENT_DATE + INTERVAL '30 days',
    'c0a80001-0000-0000-0000-000000000022',
    '完成材料体系设计并通过评审', 'PLANNED',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000008-0000-0000-0000-000000000005',
    'a0000001-0000-0000-0000-000000000003',
    'a0000007-0000-0000-0000-000000000003',
    'MS-002', '首批样件试制完成',
    CURRENT_DATE + INTERVAL '120 days',
    CURRENT_DATE + INTERVAL '120 days',
    'c0a80001-0000-0000-0000-000000000021',
    '完成首批样件试制与性能检测', 'PLANNED',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO deliverable (
    id, project_id, milestone_id, file_id, title, sha256, status, uploaded_by,
    created_at, updated_at, version
) VALUES
(
    'a0000003-0000-0000-0000-000000000003',
    'a0000001-0000-0000-0000-000000000002',
    'a0000008-0000-0000-0000-000000000003',
    'a0000002-0000-0000-0000-000000000002',
    '算法方案说明书', repeat('1', 64), 'ACTIVE',
    'c0a80001-0000-0000-0000-000000000021',
    now(), now(), 0
),
(
    'a0000003-0000-0000-0000-000000000004',
    'a0000001-0000-0000-0000-000000000002',
    NULL,
    'a0000002-0000-0000-0000-000000000001',
    '原型系统演示材料', repeat('0', 64), 'ACTIVE',
    'c0a80001-0000-0000-0000-000000000021',
    now(), now(), 0
),
(
    'a0000003-0000-0000-0000-000000000005',
    'a0000001-0000-0000-0000-000000000003',
    'a0000008-0000-0000-0000-000000000004',
    'a0000002-0000-0000-0000-000000000001',
    '材料配方设计文档', repeat('0', 64), 'ACTIVE',
    'c0a80001-0000-0000-0000-000000000022',
    now(), now(), 0
),
(
    'a0000003-0000-0000-0000-000000000006',
    'a0000001-0000-0000-0000-000000000003',
    'a0000008-0000-0000-0000-000000000005',
    'a0000002-0000-0000-0000-000000000002',
    '首批样件检测报告', repeat('1', 64), 'ACTIVE',
    'c0a80001-0000-0000-0000-000000000022',
    now(), now(), 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO budget_version (
    id, project_id, version_no, total_amount, source_type, control_level, status,
    effective_from, approved_by, approved_at,
    created_at, updated_at, created_by, updated_by, version
) VALUES (
    'a0000005-0000-0000-0000-000000000003',
    'a0000001-0000-0000-0000-000000000003',
    1, 400000.00, 'GRANT', 'BLOCK', 'EFFECTIVE',
    CURRENT_DATE - INTERVAL '1 day',
    'c0a80001-0000-0000-0000-000000000001', now(),
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO budget_line (
    id, budget_version_id, subject_code, subject_name, amount, control_level,
    created_at, updated_at, version
) VALUES
(
    'a0000006-0000-0000-0000-000000000005',
    'a0000005-0000-0000-0000-000000000003',
    'MATERIAL', '材料费', 250000.00, 'BLOCK', now(), now(), 0
),
(
    'a0000006-0000-0000-0000-000000000006',
    'a0000005-0000-0000-0000-000000000003',
    'TEST', '测试化验加工费', 100000.00, 'BLOCK', now(), now(), 0
),
(
    'a0000006-0000-0000-0000-000000000007',
    'a0000005-0000-0000-0000-000000000003',
    'TRAVEL', '差旅/会议费', 50000.00, 'WARN', now(), now(), 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO budget_balance (
    id, budget_line_id, approved_amount, reserved_amount,
    approved_unpaid_amount, paid_amount, version, updated_at
) VALUES
(
    'a0000009-0000-0000-0000-000000000005',
    'a0000006-0000-0000-0000-000000000005',
    250000.00, 0, 0, 0, 0, now()
),
(
    'a0000009-0000-0000-0000-000000000006',
    'a0000006-0000-0000-0000-000000000006',
    100000.00, 0, 0, 0, 0, now()
),
(
    'a0000009-0000-0000-0000-000000000007',
    'a0000006-0000-0000-0000-000000000007',
    50000.00, 0, 0, 0, 0, now()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO project_member (
    id, project_id, user_id, project_role, contribution_desc,
    valid_from, status, created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a0000004-0000-0000-0000-000000000007',
    'a0000001-0000-0000-0000-000000000002',
    'c0a80001-0000-0000-0000-000000000022',
    'CORE_MEMBER', '负责算法验证与实验数据分析',
    now() - INTERVAL '20 days', 'ACTIVE', now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000004-0000-0000-0000-000000000008',
    'a0000001-0000-0000-0000-000000000002',
    'c0a80001-0000-0000-0000-000000000023',
    'MEMBER', '负责项目过程管理与资源协调',
    now() - INTERVAL '20 days', 'ACTIVE', now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000004-0000-0000-0000-000000000009',
    'a0000001-0000-0000-0000-000000000003',
    'c0a80001-0000-0000-0000-000000000021',
    'CORE_MEMBER', '负责材料配方设计与工艺验证',
    now() - INTERVAL '1 day', 'ACTIVE', now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a0000004-0000-0000-0000-000000000010',
    'a0000001-0000-0000-0000-000000000003',
    'c0a80001-0000-0000-0000-000000000024',
    'MEMBER', '负责样件检测与数据归档',
    now() - INTERVAL '1 day', 'ACTIVE', now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO project_task (
    id, project_id, title, description, assignee_id, due_date, status,
    deliverable_id, created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a000000a-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000002',
    '完成算法方案评审', '组织专家评审算法方案并形成评审意见。',
    'c0a80001-0000-0000-0000-000000000022',
    CURRENT_DATE + INTERVAL '30 days', 'TODO',
    'a0000003-0000-0000-0000-000000000003',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a000000a-0000-0000-0000-000000000003',
    'a0000001-0000-0000-0000-000000000003',
    '完成材料配方设计', '形成材料配方设计文档并提交评审。',
    'c0a80001-0000-0000-0000-000000000021',
    CURRENT_DATE + INTERVAL '20 days', 'TODO',
    'a0000003-0000-0000-0000-000000000005',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a000000a-0000-0000-0000-000000000004',
    'a0000001-0000-0000-0000-000000000003',
    '准备样件检测方案', '明确检测指标、设备与送检安排。',
    'c0a80001-0000-0000-0000-000000000024',
    CURRENT_DATE + INTERVAL '40 days', 'TODO',
    NULL,
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO progress_report (
    id, project_id, period_type, period_key, due_date, status,
    created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a000000b-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000002',
    'MONTH', '2026-11', CURRENT_DATE + INTERVAL '5 days', 'DRAFT',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a000000b-0000-0000-0000-000000000003',
    'a0000001-0000-0000-0000-000000000003',
    'MONTH', '2026-11', CURRENT_DATE + INTERVAL '5 days', 'DRAFT',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO project_risk (
    id, project_id, risk_type, title, description, risk_level, owner_id,
    due_date, status, created_at, updated_at, created_by, updated_by, version
) VALUES
(
    'a000000c-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000002',
    'TECHNICAL', '算法泛化能力不足', '跨数据集验证指标波动较大，需要扩充样本并调优。',
    'MEDIUM', 'c0a80001-0000-0000-0000-000000000021',
    CURRENT_DATE + INTERVAL '30 days', 'OPEN',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
),
(
    'a000000c-0000-0000-0000-000000000003',
    'a0000001-0000-0000-0000-000000000003',
    'SCHEDULE', '样件试制周期紧张', '外协试制排期可能影响首批样件交付时间。',
    'MEDIUM', 'c0a80001-0000-0000-0000-000000000022',
    CURRENT_DATE + INTERVAL '45 days', 'OPEN',
    now(), now(),
    'c0a80001-0000-0000-0000-000000000001',
    'c0a80001-0000-0000-0000-000000000001', 0
)
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- =============================================================================
-- 演示角色差异化：PI（项目负责人）角色与分配
-- T2026002（李静）为 PI：可见申报/项目/预算/支出/执行/成果，不可见对账管理等财务与决策入口
-- =============================================================================

INSERT INTO iam_role (id, code, name, scope_type, description, status)
VALUES ('c0a80001-0000-0000-0000-000000000012', 'PI', '项目负责人', 'PROJECT',
        '演示角色：申报/执行/预算/支出/成果登记，不含财务对账与审批决策', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM iam_role r JOIN iam_permission p
  ON p.code IN (
    'application:create','application:edit','application:submit','application:view','application:withdraw',
    'project:view','project:change:create','project:change:submit','project:member:manage','project:milestone:manage','project:risk:create',
    'fund:view','fund:budget:edit','fund:budget:submit','fund:expense:create','fund:expense:edit','fund:expense:submit','fund:expense:withdraw','fund:expense:cancel',
    'execution:view','execution:report:edit','execution:report:submit','execution:deliverable:upload','exec:task:assign',
    'output:view','output:create','output:edit','output:submit','output:relate'
  )
WHERE r.code = 'PI'
  AND NOT EXISTS (SELECT 1 FROM iam_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id);

DELETE FROM iam_user_role
 WHERE user_id = (SELECT id FROM iam_user WHERE employee_no = 'T2026002')
   AND role_id = (SELECT id FROM iam_role WHERE code = 'ADMIN');

INSERT INTO iam_user_role (user_id, role_id, status)
SELECT (SELECT id FROM iam_user WHERE employee_no = 'T2026002'),
       (SELECT id FROM iam_role WHERE code = 'PI'), 'ACTIVE'
WHERE NOT EXISTS (
  SELECT 1 FROM iam_user_role ur
  WHERE ur.user_id = (SELECT id FROM iam_user WHERE employee_no = 'T2026002')
    AND ur.role_id = (SELECT id FROM iam_role WHERE code = 'PI'));

COMMIT;
