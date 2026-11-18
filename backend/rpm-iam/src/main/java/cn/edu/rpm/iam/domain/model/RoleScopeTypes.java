package cn.edu.rpm.iam.domain.model;

/**
 * 角色数据范围类型常量（{@code iam_role.scope_type}，ck_iam_role_scope）。
 *
 * <p>取值与 V01 基线 DDL 的 CHECK 约束逐一对应；授权解析时：</p>
 * <ul>
 *   <li>{@link #SELF} → 本人负责项目（{@code pi_id}）；</li>
 *   <li>{@link #DEPT} → 授权部门；{@link #DEPT_TREE} → 授权部门树（子树展开）；</li>
 *   <li>{@link #ALL} → 仅对 {@link RoleCodes#ALL_SCOPE_ROLES} 生效的全院范围；</li>
 *   <li>{@link #PROJECT} / {@link #CUSTOM} → 项目级授权，由临时授权/项目成员承载。</li>
 * </ul>
 */
public final class RoleScopeTypes {

    /** 本人。 */
    public static final String SELF = "SELF";

    /** 参与项目。 */
    public static final String PROJECT = "PROJECT";

    /** 部门。 */
    public static final String DEPT = "DEPT";

    /** 部门树。 */
    public static final String DEPT_TREE = "DEPT_TREE";

    /** 全院。 */
    public static final String ALL = "ALL";

    /** 自定义授权集。 */
    public static final String CUSTOM = "CUSTOM";

    private RoleScopeTypes() {
    }
}
