package cn.edu.rpm.project.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 项目接口请求体集合（T-PRJ-023；技术文档 04 §2.1）。
 *
 * <p>仅承载 HTTP 形态字段，不包含业务规则；Controller 将请求映射为应用服务 Cmd。</p>
 */
public final class ProjectRequests {

    private ProjectRequests() {
    }

    /**
     * 新增成员请求。
     *
     * @param userId           成员用户 ID
     * @param projectRole      项目内角色
     * @param contributionDesc 贡献说明
     * @param validFrom        有效期起
     * @param validTo          有效期止
     */
    public record AddMemberRequest(
            String userId,
            String projectRole,
            String contributionDesc,
            OffsetDateTime validFrom,
            OffsetDateTime validTo
    ) {
    }

    /**
     * 里程碑计划请求。
     *
     * @param code               里程碑编码
     * @param title              名称
     * @param planDate           计划日期
     * @param ownerId            责任人
     * @param completionCriteria 完成判定
     */
    public record MilestoneRequest(
            String code,
            String title,
            LocalDate planDate,
            String ownerId,
            String completionCriteria
    ) {
    }

    /**
     * 里程碑视图。
     *
     * @param milestoneId     里程碑 ID
     * @param code            编码
     * @param title           名称
     * @param planDate        原计划日期
     * @param currentPlanDate 当前计划日期
     * @param status          状态
     */
    public record MilestoneView(
            String milestoneId,
            String code,
            String title,
            LocalDate planDate,
            LocalDate currentPlanDate,
            String status
    ) {
    }

    /**
     * 新建里程碑请求。
     *
     * @param baselineId 目标基线 ID
     * @param milestones 里程碑计划
     */
    public record CreateMilestonesRequest(String baselineId, List<MilestoneRequest> milestones) {
    }

    /**
     * 登记风险请求。
     *
     * @param ownerId        责任人
     * @param riskType       风险类型
     * @param title          标题
     * @param description    描述
     * @param riskLevel      等级
     * @param dueDate        截止日期
     * @param idempotencyKey 幂等键
     */
    public record CreateRiskRequest(
            String ownerId,
            String riskType,
            String title,
            String description,
            String riskLevel,
            LocalDate dueDate,
            String idempotencyKey
    ) {
    }

    /**
     * 关闭风险请求。
     *
     * @param resolution    处置结果
     * @param evidenceFileId 证据文件 ID
     */
    public record CloseRiskRequest(String resolution, String evidenceFileId) {
    }

    /**
     * 状态迁移请求。
     *
     * @param reason 原因（暂停/终止必填）
     */
    public record StateChangeRequest(String reason) {
    }

    /**
     * 发起变更请求。
     *
     * @param changeType        变更类型
     * @param effectiveDate     生效日期
     * @param payload           类型专属载荷
     * @param reason            变更原因
     * @param attachmentFileIds 附件
     */
    public record CreateChangeRequest(
            String changeType,
            LocalDate effectiveDate,
            Map<String, Object> payload,
            String reason,
            List<String> attachmentFileIds
    ) {
    }

    /**
     * 发起交接请求。
     *
     * @param toUserId     新负责人
     * @param effectiveAt  生效时间
     * @param pendingItems 待转移事项
     * @param reason       交接原因
     */
    public record HandoverRequest(
            String toUserId,
            OffsetDateTime effectiveAt,
            List<PendingItem> pendingItems,
            String reason
    ) {

        /**
         * 待转移事项。
         *
         * @param type   类型（TASK/RISK）
         * @param id     事项 ID
         * @param action 动作
         */
        public record PendingItem(String type, String id, String action) {
        }
    }

    /**
     * 解析幂等键请求头。
     *
     * @param value 请求头值
     * @return 幂等键；为空返回 {@code null}
     */
    public static UUID parseRequestId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new cn.edu.rpm.common.exception.BizException(
                    cn.edu.rpm.common.exception.ErrorCode.COMMON_400, "Idempotency-Key 非法");
        }
    }
}
