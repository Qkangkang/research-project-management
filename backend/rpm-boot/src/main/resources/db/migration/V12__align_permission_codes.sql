-- V12__align_permission_codes.sql
-- 对齐后端权限常量与前端的权限码，补齐缺失权限并绑定到 ADMIN 角色。
-- 只新增脚本，不修改 V10/V11 历史迁移。

-- 1. 将 V11 中带下划线的历史权限码统一为后端常量使用的连字符形式。
UPDATE iam_permission p
SET code = 'application:formal-review'
WHERE p.code = 'application:formal_review'
  AND NOT EXISTS (
      SELECT 1 FROM iam_permission existing
      WHERE existing.code = 'application:formal-review'
  );

UPDATE iam_permission p
SET code = 'application:external-register'
WHERE p.code = 'application:external_register'
  AND NOT EXISTS (
      SELECT 1 FROM iam_permission existing
      WHERE existing.code = 'application:external-register'
  );

-- 2. 补齐后端已使用但权限字典缺失的权限点。
INSERT INTO iam_permission (id, code, name, module) VALUES
    ('00000011-0000-0000-0000-000000000058', 'fund:expense:special-approve',
     '支出特批审批', 'funding'),
    ('00000011-0000-0000-0000-000000000059', 'project:create',
     '创建项目', 'project')
ON CONFLICT (code) DO NOTHING;

-- 3. 将全量权限绑定到 ADMIN 角色（幂等，含本次新增与改名后的权限）。
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
