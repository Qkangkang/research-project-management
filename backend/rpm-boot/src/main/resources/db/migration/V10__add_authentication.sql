-- V10__add_authentication.sql
-- 新增：登录认证支持（本地账号 + 预留 SSO）
-- 需求背景：需求文档 §10.3 SSO 协议锁定 OIDC，V1 先实现本地账号作为备用

-- 1. 扩展 iam_user 表，增加密码字段（幂等，列已存在则跳过）
ALTER TABLE iam_user ADD COLUMN IF NOT EXISTS password_hash varchar(255);
COMMENT ON COLUMN iam_user.password_hash IS '密码 BCrypt 哈希值；本地账号必填，SSO 账号为 NULL';

-- 2. 创建登录日志表（幂等）
CREATE TABLE IF NOT EXISTS iam_login_log (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid REFERENCES iam_user(id) ON DELETE SET NULL,
    employee_no         varchar(64) NOT NULL,
    login_type          varchar(16) NOT NULL,
    login_ip            varchar(64),
    user_agent          varchar(512),
    login_result        varchar(16) NOT NULL,
    failure_reason      varchar(256),
    login_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_iam_login_log_type CHECK (login_type IN ('LOCAL','SSO','REFRESH')),
    CONSTRAINT ck_iam_login_log_result CHECK (login_result IN ('SUCCESS','FAILURE'))
);

CREATE INDEX IF NOT EXISTS idx_iam_login_log_user_id ON iam_login_log(user_id);
CREATE INDEX IF NOT EXISTS idx_iam_login_log_employee_no ON iam_login_log(employee_no);
CREATE INDEX IF NOT EXISTS idx_iam_login_log_login_at ON iam_login_log(login_at DESC);

COMMENT ON TABLE iam_login_log IS '登录日志（审计用，只追加不修改）';
COMMENT ON COLUMN iam_login_log.user_id IS '用户 ID（用户删除后置 NULL）';
COMMENT ON COLUMN iam_login_log.employee_no IS '工号（冗余存储，用户删除后仍可追溯）';
COMMENT ON COLUMN iam_login_log.login_type IS '登录类型：LOCAL 本地账号 / SSO 单点登录 / REFRESH 刷新 Token';
COMMENT ON COLUMN iam_login_log.login_result IS '登录结果：SUCCESS / FAILURE';
COMMENT ON COLUMN iam_login_log.failure_reason IS '失败原因：账号不存在/密码错误/账号锁定等';

-- 3. 插入根组织单元（如果不存在，用于管理员账号的 dept_id）
INSERT INTO org_unit (id, code, name, unit_type, path, status, created_at, updated_at)
SELECT 'c0a80001-0000-0000-0000-000000000010', 'ROOT', '科研院', 'OTHER', '/ROOT', 'ACTIVE', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM org_unit LIMIT 1)
ON CONFLICT DO NOTHING;

-- 4. 插入管理员角色（如果不存在）
INSERT INTO iam_role (id, code, name, scope_type, status, created_at, updated_at)
SELECT 'c0a80001-0000-0000-0000-000000000011', 'ADMIN', '系统管理员', 'ALL', 'ACTIVE', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM iam_role WHERE code = 'ADMIN')
ON CONFLICT DO NOTHING;

-- 5. 插入初始管理员账号（密码：Admin@123，BCrypt 加密）
-- BCrypt hash 使用 strength=10（Spring Security 默认）
INSERT INTO iam_user (
    id, employee_no, display_name, dept_id, email,
    password_hash, status, created_at, updated_at
) VALUES (
    'c0a80001-0000-0000-0000-000000000001',
    'admin',
    '系统管理员',
    (SELECT id FROM org_unit ORDER BY created_at LIMIT 1),
    'admin@example.com',
    '$2a$10$g/HsTpDOeYxNGD.QhlEMBOxLl4rugNe3LPxlOWjVrV0Myk0Y9sv7q',
    'ACTIVE',
    now(),
    now()
) ON CONFLICT (employee_no) DO NOTHING;

-- 6. 给管理员分配超级管理员角色
INSERT INTO iam_user_role (user_id, role_id, status, created_at, updated_at)
SELECT
    'c0a80001-0000-0000-0000-000000000001',
    r.id,
    'ACTIVE',
    now(),
    now()
FROM iam_role r
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM iam_user_role ur
    WHERE ur.user_id = 'c0a80001-0000-0000-0000-000000000001'
      AND ur.role_id = r.id
  );

-- 7. 记录数据库版本信息
COMMENT ON TABLE iam_user IS 'IAM 用户表（V10 新增 password_hash 支持本地认证）';
