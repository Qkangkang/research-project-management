package cn.edu.rpm.iam.application.dto;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 项目数据范围（技术文档 03 §2.2）。
 *
 * <p>由 {@code DataScopeResolver} 解析后注入仓储查询，与仓储参数化 SQL 的
 * {@code scopeAll / pi_id / dept_id = ANY / grantedProjectIds} 条件一一对应：
 * 列表、详情、附件下载、导出、异步任务必须复用同一实例（TC-IAM-015 五处同权）。
 * 项目成员身份由仓储以 {@code EXISTS project_member... user_id = :userId} 表达，
 * 不放入本对象；{@code scopeAll} 只能由 {@code RESEARCH_ADMIN / LEADER / AUDITOR}
 * 的授权范围产生，禁止前端传入。</p>
 *
 * @param scopeAll          是否全院范围
 * @param piId              本人负责项目的负责人 ID（PI 身份）；非 PI 为空
 * @param deptIds           授权部门树范围的组织 ID 集合（含子树展开结果）
 * @param grantedProjectIds 临时授权命中的项目 ID 集合（已按动作与有效期过滤）
 */
public record ProjectScope(
        boolean scopeAll,
        UUID piId,
        Set<UUID> deptIds,
        Set<UUID> grantedProjectIds
) {

    /** 规范化集合字段。 */
    public ProjectScope {
        deptIds = deptIds == null ? Set.of() : Set.copyOf(deptIds);
        grantedProjectIds = grantedProjectIds == null ? Set.of() : Set.copyOf(grantedProjectIds);
    }

    /**
     * 空范围：无任何项目可见。
     *
     * @return 空范围实例
     */
    public static ProjectScope empty() {
        return new ProjectScope(false, null, Set.of(), Set.of());
    }

    /**
     * 全院范围。
     *
     * @return 全院范围实例
     */
    public static ProjectScope allScope() {
        return new ProjectScope(true, null, Set.of(), Set.of());
    }

    /**
     * 构造带集合入参的范围（便于调用方以可变集合装配）。
     *
     * @param scopeAll          是否全院
     * @param piId              负责人 ID
     * @param deptIds           部门 ID 集合
     * @param grantedProjectIds 授权项目 ID 集合
     * @return 范围实例
     */
    public static ProjectScope of(boolean scopeAll, UUID piId,
                                  Collection<UUID> deptIds, Collection<UUID> grantedProjectIds) {
        return new ProjectScope(scopeAll, piId,
                deptIds == null ? Set.of() : Set.copyOf(deptIds),
                grantedProjectIds == null ? Set.of() : Set.copyOf(grantedProjectIds));
    }
}
