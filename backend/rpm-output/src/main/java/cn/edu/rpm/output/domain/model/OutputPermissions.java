package cn.edu.rpm.output.domain.model;

/**
 * 成果功能权限码（与《05-经费成果验收接口设计.md》§4.1、《09-P1范围补充接口设计.md》
 * §5.1 权限列逐字一致）。
 *
 * <p>Controller 的 {@code @RequirePermission} 与应用服务入口的
 * {@code AuthorizationService.check(...)} 必须引用本类常量，禁止散落字面量。</p>
 */
public final class OutputPermissions {

    /** 登记成果。 */
    public static final String CREATE = "output:create";

    /** 成果详情。 */
    public static final String VIEW = "output:view";

    /** 保存成果草稿/修订。 */
    public static final String EDIT = "output:edit";

    /** 提交成果审核。 */
    public static final String SUBMIT = "output:submit";

    /** 关联项目/撤销关联。 */
    public static final String RELATE = "output:relate";

    /** 审核成果（含撤回审核）。 */
    public static final String REVIEW = "output:review";

    /** 全院成果库检索与统计。 */
    public static final String LIBRARY_VIEW = "output:library:view";

    /**
     * 项目查看（成果关联目标项目可见性校验与项目成果列表）。
     *
     * <p>该权限点归 rpm-project 域；成果模块仅以权限码形式做统一授权判定，
     * 不依赖 rpm-project 的常量类，避免跨模块编译依赖。</p>
     */
    public static final String PROJECT_VIEW = "project:view";

    private OutputPermissions() {
    }
}
