package cn.edu.rpm.project.domain.model;

/**
 * 项目功能权限码（与《04-核心业务接口设计.md》§2.1/§2.3 权限列逐字一致）。
 *
 * <p>Controller 的 {@code @RequirePermission} 与应用服务入口的
 * {@code AuthorizationService.check(...)} 必须引用本类常量，禁止散落字面量。</p>
 */
public final class ProjectPermissions {

    /** 查看项目列表/详情/成员/里程碑/风险。 */
    public static final String VIEW = "project:view";

    /** 新增/变更项目成员。 */
    public static final String MEMBER_MANAGE = "project:member:manage";

    /** 新增/调整里程碑（含里程碑延期申请）。 */
    public static final String MILESTONE_MANAGE = "project:milestone:manage";

    /** 登记项目风险。 */
    public static final String RISK_CREATE = "project:risk:create";

    /** 关闭项目风险。 */
    public static final String RISK_CLOSE = "project:risk:close";

    /** 项目状态迁移（暂停/恢复/终止/结题等）。 */
    public static final String STATE_CHANGE = "project:state:change";

    /** 发起项目变更（负责人/成员/周期/目标/预算/合作单位）。 */
    public static final String CHANGE_CREATE = "project:change:create";

    /** 提交项目变更审批（退回后重新提交）。 */
    public static final String CHANGE_SUBMIT = "project:change:submit";

    /** 发起负责人交接。 */
    public static final String HANDOVER = "project:handover";

    private ProjectPermissions() {
    }
}
