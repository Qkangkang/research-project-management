package cn.edu.rpm.iam.domain.model;

import java.util.Set;

/**
 * 角色编码常量（{@code iam_role.code}），与《产品需求文档》§6.2 / 技术文档 03 §1.2 对齐。
 *
 * <p>角色编码是数据字典值（由 DDL 与初始化数据治理），代码侧统一引用本类，
 * 禁止在授权逻辑中散落字符串字面量。</p>
 */
public final class RoleCodes {

    /** 项目负责人。 */
    public static final String PI = "PI";

    /** 项目成员。 */
    public static final String MEMBER = "MEMBER";

    /** 科研助理/经办人。 */
    public static final String ASSISTANT = "ASSISTANT";

    /** 部门科研秘书。 */
    public static final String DEPT_SECRETARY = "DEPT_SECRETARY";

    /** 科研管理人员。 */
    public static final String RESEARCH_ADMIN = "RESEARCH_ADMIN";

    /** 财务人员。 */
    public static final String FINANCE = "FINANCE";

    /** 院所领导。 */
    public static final String LEADER = "LEADER";

    /** 评审专家。 */
    public static final String EXPERT = "EXPERT";

    /** 审计人员。 */
    public static final String AUDITOR = "AUDITOR";

    /** 档案人员。 */
    public static final String ARCHIVIST = "ARCHIVIST";

    /** 系统管理员。 */
    public static final String SYS_ADMIN = "SYS_ADMIN";

    /** 本地内置系统管理员（历史初始化数据使用的角色编码）。 */
    public static final String ADMIN = "ADMIN";

    /**
     * 可产生"全院范围"的角色（技术文档 03 §2.2：{@code scopeAll} 只由
     * {@code RESEARCH_ADMIN / LEADER / AUDITOR} 的授权范围产生，不由前端传入）。
     */
    public static final Set<String> ALL_SCOPE_ROLES = Set.of(
            RESEARCH_ADMIN, LEADER, AUDITOR, SYS_ADMIN, ADMIN);

    private RoleCodes() {
    }
}
