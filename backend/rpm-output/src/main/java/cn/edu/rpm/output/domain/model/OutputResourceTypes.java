package cn.edu.rpm.output.domain.model;

/**
 * 成果授权资源类型（{@code ResourceRef.resourceType} 取值）。
 *
 * <p>与 {@code iam_temp_grant.resource_type} 的自由字符串口径一致；成果资源以
 * 完成人（PI 维度）与所属部门参与统一授权的数据范围判定。项目维度的临时授权
 * 仍由 {@code ResourceTypes.PROJECT} 表达，不得混用。</p>
 */
public final class OutputResourceTypes {

    /** 成果资源。 */
    public static final String OUTPUT = "OUTPUT";

    private OutputResourceTypes() {
    }
}
