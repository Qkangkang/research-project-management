package cn.edu.rpm.application.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 立项创建命令（T-APP-016；技术文档 04 §3.4）。
 *
 * <p>纯 record，不依赖 ORM/Jackson/Spring；只包含立项所需字段与日期。
 * 所有集合字段在构造时拷贝为不可变视图，调用方后续修改原集合不影响本次立项。</p>
 *
 * <p>{@code snapshot} 为已批准申报版本的内容快照（用于生成
 * {@code project_baseline.snapshot} 与 {@code snapshot_hash}）；
 * {@code milestones} 为批准版本中确认的里程碑计划，可为空（外部项目登记无里程碑时）。</p>
 *
 * @param sourceApplicationId        来源申报 ID
 * @param sourceApplicationVersionId 来源申报版本 ID（立项幂等键）
 * @param sourceType                 来源类型：{@code INTERNAL} / {@code EXTERNAL}
 * @param title                      项目名称
 * @param projectType                项目类型
 * @param deptId                     承担部门 ID
 * @param piId                       项目负责人 ID（按人员 ID，不按账号名）
 * @param approvedStartDate          批准开始日期
 * @param approvedEndDate            批准结束日期
 * @param financialProjectNo         财务项目号（可空）
 * @param externalApprovalNo         外部批复号（可空）
 * @param externalContractNo         外部合同号（可空）
 * @param snapshot                   批准版本内容快照（非空，缺省空 Map）
 * @param milestones                 里程碑计划（非空，缺省空列表）
 */
public record ProjectCreationCommand(
        String sourceApplicationId,
        String sourceApplicationVersionId,
        String sourceType,
        String title,
        String projectType,
        String deptId,
        String piId,
        LocalDate approvedStartDate,
        LocalDate approvedEndDate,
        String financialProjectNo,
        String externalApprovalNo,
        String externalContractNo,
        Map<String, Object> snapshot,
        List<MilestonePlan> milestones
) {

    /** 来源类型：院内申报。 */
    public static final String SOURCE_INTERNAL = "INTERNAL";

    /** 来源类型：外部项目登记。 */
    public static final String SOURCE_EXTERNAL = "EXTERNAL";

    /** 规范化集合字段。 */
    public ProjectCreationCommand {
        snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
    }

    /**
     * 里程碑计划（批准版本中确认的计划；不在此处生成基线）。
     *
     * @param code               里程碑编码（同一基线内唯一）
     * @param title              里程碑名称
     * @param planDate           计划日期（后续延期只改 {@code current_plan_date}）
     * @param ownerId            责任人用户 ID；可为空
     * @param completionCriteria 完成判定；完成前为空抛 {@code RPM-EXEC-003}
     */
    public record MilestonePlan(
            String code,
            String title,
            LocalDate planDate,
            String ownerId,
            String completionCriteria
    ) {
    }
}
