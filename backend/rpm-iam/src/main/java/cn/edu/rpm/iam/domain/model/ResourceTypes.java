package cn.edu.rpm.iam.domain.model;

/**
 * 授权判定使用的资源类型常量。
 *
 * <p>与 {@code iam_temp_grant.resource_type} 的取值口径一致（大写字符串）；
 * 业务模块构造 {@code ResourceRef} 时必须引用本类常量，禁止散落字面量。</p>
 */
public final class ResourceTypes {

    /** 项目资源。 */
    public static final String PROJECT = "PROJECT";

    /** 支出申请（以所属项目维度授权）。 */
    public static final String EXPENSE = "EXPENSE";

    /** 专家评审（以所属项目/评审批次维度授权）。 */
    public static final String REVIEW = "REVIEW";

    private ResourceTypes() {
    }
}
