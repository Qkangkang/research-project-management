package cn.edu.rpm.funding.domain.model;

/**
 * 经费模块权限码（技术文档 05 §2 接口目录）。
 *
 * <p>权限码与接口文档逐字对齐，禁止在调用侧自造字符串。</p>
 */
public final class FundingPermissions {

    /** 经费查看。 */
    public static final String VIEW = "fund:view";

    /** 预算创建/编辑。 */
    public static final String BUDGET_EDIT = "fund:budget:edit";

    /** 预算提交/生效。 */
    public static final String BUDGET_SUBMIT = "fund:budget:submit";

    /** 支出草稿创建。 */
    public static final String EXPENSE_CREATE = "fund:expense:create";

    /** 支出编辑（特批申请等）。 */
    public static final String EXPENSE_EDIT = "fund:expense:edit";

    /** 支出提交。 */
    public static final String EXPENSE_SUBMIT = "fund:expense:submit";

    /** 支出撤回（审批中撤回）。 */
    public static final String EXPENSE_WITHDRAW = "fund:expense:withdraw";

    /** 支出取消（待补正/已批未付/部分支付）。 */
    public static final String EXPENSE_CANCEL = "fund:expense:cancel";

    /** 支出特批审批（独立权限点）。 */
    public static final String EXPENSE_SPECIAL_APPROVE = "fund:expense:special-approve";

    /** 支付导入。 */
    public static final String FINANCE_IMPORT = "fund:finance:import";

    /** 对账与差异关闭。 */
    public static final String FINANCE_RECONCILE = "fund:finance:reconcile";

    /** 支付导入（05 §2 对齐码）。 */
    public static final String PAYMENT_IMPORT = "finance:payment:import";

    /** 导入复核应用（05 §2 对齐码）。 */
    public static final String PAYMENT_APPLY = "finance:payment:apply";

    /** 对账批次查看（05 §2 对齐码）。 */
    public static final String RECONCILE_VIEW = "finance:reconcile:view";

    /** 对账执行（05 §2 对齐码）。 */
    public static final String RECONCILE_RUN = "finance:reconcile:run";

    /** 差异关闭（05 §2 对齐码）。 */
    public static final String RECONCILE_CLOSE = "finance:reconcile:close";

    private FundingPermissions() {
    }
}
