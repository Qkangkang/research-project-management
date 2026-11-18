-- V11__init_permissions.sql
-- 初始化系统权限数据，并将全量权限绑定到 ADMIN 角色
-- 对应需求：所有接口 @RequirePermission 检查需要权限表有数据才能放行

-- ============================================================
-- 1. 插入全部权限码（幂等，冲突跳过）
-- ============================================================

INSERT INTO iam_permission (id, code, name, module) VALUES
-- 申报模块
('00000011-0000-0000-0000-000000000001', 'application:view',           '查看申报',         'application'),
('00000011-0000-0000-0000-000000000002', 'application:create',         '创建申报',         'application'),
('00000011-0000-0000-0000-000000000003', 'application:edit',           '编辑申报',         'application'),
('00000011-0000-0000-0000-000000000004', 'application:submit',         '提交申报',         'application'),
('00000011-0000-0000-0000-000000000005', 'application:withdraw',       '撤回申报',         'application'),
('00000011-0000-0000-0000-000000000006', 'application:formal_review',  '形式审查',         'application'),
('00000011-0000-0000-0000-000000000007', 'application:decide',         '立项决策',         'application'),
('00000011-0000-0000-0000-000000000008', 'application:external_register', '外部项目登记',  'application'),

-- 项目模块
('00000011-0000-0000-0000-000000000010', 'project:view',               '查看项目',         'project'),
('00000011-0000-0000-0000-000000000011', 'project:state:change',       '项目状态变更',     'project'),
('00000011-0000-0000-0000-000000000012', 'project:member:manage',      '项目成员管理',     'project'),
('00000011-0000-0000-0000-000000000013', 'project:milestone:manage',   '里程碑管理',       'project'),
('00000011-0000-0000-0000-000000000014', 'project:risk:create',        '创建风险',         'project'),
('00000011-0000-0000-0000-000000000015', 'project:risk:close',         '关闭风险',         'project'),
('00000011-0000-0000-0000-000000000016', 'project:change:create',      '创建变更申请',     'project'),
('00000011-0000-0000-0000-000000000017', 'project:change:submit',      '提交变更申请',     'project'),
('00000011-0000-0000-0000-000000000018', 'project:handover',           '项目交接',         'project'),

-- 执行模块
('00000011-0000-0000-0000-000000000020', 'execution:view',             '查看执行进度',     'execution'),
('00000011-0000-0000-0000-000000000021', 'execution:report:edit',      '编辑进度报告',     'execution'),
('00000011-0000-0000-0000-000000000022', 'execution:report:submit',    '提交进度报告',     'execution'),
('00000011-0000-0000-0000-000000000023', 'execution:report:review',    '审核进度报告',     'execution'),
('00000011-0000-0000-0000-000000000024', 'execution:deliverable:upload','上传交付物',      'execution'),
('00000011-0000-0000-0000-000000000025', 'exec:task:assign',           '分配任务',         'execution'),

-- 资金模块
('00000011-0000-0000-0000-000000000030', 'fund:view',                  '查看资金',         'funding'),
('00000011-0000-0000-0000-000000000031', 'fund:budget:edit',           '编辑预算',         'funding'),
('00000011-0000-0000-0000-000000000032', 'fund:budget:submit',         '提交预算',         'funding'),
('00000011-0000-0000-0000-000000000033', 'fund:expense:create',        '创建支出申请',     'funding'),
('00000011-0000-0000-0000-000000000034', 'fund:expense:edit',          '编辑支出申请',     'funding'),
('00000011-0000-0000-0000-000000000035', 'fund:expense:submit',        '提交支出申请',     'funding'),
('00000011-0000-0000-0000-000000000036', 'fund:expense:withdraw',      '撤回支出申请',     'funding'),
('00000011-0000-0000-0000-000000000037', 'fund:expense:cancel',        '作废支出申请',     'funding'),
('00000011-0000-0000-0000-000000000038', 'fund:finance:import',        '财务数据导入',     'funding'),
('00000011-0000-0000-0000-000000000039', 'fund:finance:reconcile',     '对账操作',         'funding'),
('00000011-0000-0000-0000-000000000040', 'finance:payment:apply',      '发起支付',         'funding'),
('00000011-0000-0000-0000-000000000041', 'finance:payment:import',     '导入支付流水',     'funding'),
('00000011-0000-0000-0000-000000000042', 'finance:reconcile:view',     '查看对账',         'funding'),
('00000011-0000-0000-0000-000000000043', 'finance:reconcile:run',      '执行对账',         'funding'),
('00000011-0000-0000-0000-000000000044', 'finance:reconcile:close',    '关闭差异',         'funding'),

-- 成果模块
('00000011-0000-0000-0000-000000000050', 'output:view',                '查看成果',         'output'),
('00000011-0000-0000-0000-000000000051', 'output:create',              '登记成果',         'output'),
('00000011-0000-0000-0000-000000000052', 'output:edit',                '编辑成果',         'output'),
('00000011-0000-0000-0000-000000000053', 'output:submit',              '提交成果审核',     'output'),
('00000011-0000-0000-0000-000000000054', 'output:review',              '审核成果',         'output'),
('00000011-0000-0000-0000-000000000055', 'output:relate',              '关联成果与项目',   'output'),
('00000011-0000-0000-0000-000000000056', 'output:library:view',        '查看成果库',       'output'),
('00000011-0000-0000-0000-000000000057', 'output:project:view',        '查看项目成果',     'output')

ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 2. 将全量权限绑定到 ADMIN 角色（幂等）
-- ============================================================

INSERT INTO iam_role_permission (role_id, permission_id, created_at)
SELECT
    r.id,
    p.id,
    now()
FROM iam_role r
         CROSS JOIN iam_permission p
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
