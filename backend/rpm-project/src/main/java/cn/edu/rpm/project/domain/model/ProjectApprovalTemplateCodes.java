package cn.edu.rpm.project.domain.model;

/**
 * 项目类审批模板业务编码（技术文档 08 §8.2 变更矩阵）。
 *
 * <p>模板编码属于审批配置契约，集中定义避免散落字面量；模板版本由
 * {@code rpm-workflow} 按业务时点查询，应用服务不直接读取模板表。</p>
 */
public final class ProjectApprovalTemplateCodes {

    /** 周期变更审批模板。 */
    public static final String CHANGE_PERIOD = "CHANGE_PERIOD";

    /** 负责人变更审批模板。 */
    public static final String CHANGE_PI = "CHANGE_PI";

    /** 成员变更审批模板。 */
    public static final String CHANGE_MEMBER = "CHANGE_MEMBER";

    /** 目标/里程碑变更审批模板。 */
    public static final String CHANGE_OBJECTIVE = "CHANGE_OBJECTIVE";

    /** 预算变更审批模板。 */
    public static final String CHANGE_BUDGET = "CHANGE_BUDGET";

    /** 合作单位变更审批模板。 */
    public static final String CHANGE_PARTNER = "CHANGE_PARTNER";

    /** 负责人交接审批模板。 */
    public static final String HANDOVER = "HANDOVER";

    private ProjectApprovalTemplateCodes() {
    }
}
