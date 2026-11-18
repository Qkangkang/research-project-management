package cn.edu.rpm.application.domain.model;

/**
 * 申报功能权限码（与《04-核心业务接口设计.md》§2.2 权限列逐字一致）。
 *
 * <p>Controller 的 {@code @RequirePermission} 与应用服务入口的
 * {@code AuthorizationService.check(...)} 必须引用本类常量，禁止散落字面量。</p>
 */
public final class ApplicationPermissions {

    /** 创建申报草稿。 */
    public static final String CREATE = "application:create";

    /** 保存草稿。 */
    public static final String EDIT = "application:edit";

    /** 提交申报并启动审批。 */
    public static final String SUBMIT = "application:submit";

    /** 查看申报。 */
    public static final String VIEW = "application:view";

    /** 形式审查（通过/退回/不予受理）。 */
    public static final String FORMAL_REVIEW = "application:formal-review";

    /** 立项决策（批准/不批准）。 */
    public static final String DECIDE = "application:decide";

    /** 撤回申报。 */
    public static final String WITHDRAW = "application:withdraw";

    /** 外部项目登记立项。 */
    public static final String EXTERNAL_REGISTER = "application:external-register";

    private ApplicationPermissions() {
    }
}
