package cn.edu.rpm.execution.domain.model;

/**
 * 执行域功能权限码（与《04-核心业务接口设计.md》§2.1 权限列逐字一致）。
 *
 * <p>Controller 的 {@code @RequirePermission} 与应用服务入口的
 * {@code AuthorizationService.check(...)} 必须引用本类常量。</p>
 */
public final class ExecutionPermissions {

    /** 查看执行数据（报告/交付物/任务）。 */
    public static final String VIEW = "execution:view";

    /** 创建/编辑报告草稿。 */
    public static final String REPORT_EDIT = "execution:report:edit";

    /** 提交周期报告。 */
    public static final String REPORT_SUBMIT = "execution:report:submit";

    /** 审核/退回周期报告。 */
    public static final String REPORT_REVIEW = "execution:report:review";

    /** 上传交付物。 */
    public static final String DELIVERABLE_UPLOAD = "execution:deliverable:upload";

    /** 分派轻任务。 */
    public static final String TASK_ASSIGN = "exec:task:assign";

    private ExecutionPermissions() {
    }
}
