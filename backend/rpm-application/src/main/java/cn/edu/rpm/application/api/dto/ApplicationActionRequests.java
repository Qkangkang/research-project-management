package cn.edu.rpm.application.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 申报动作请求体集合（T-APP-022；技术文档 04 §3.3～§3.5）。
 *
 * <p>仅承载 HTTP 形态字段，业务校验在应用服务内完成。</p>
 */
public final class ApplicationActionRequests {

    private ApplicationActionRequests() {
    }

    /**
     * 撤回申报请求。
     *
     * @param reason 撤回原因
     */
    public record WithdrawRequest(String reason) {
    }

    /**
     * 形式审查请求。
     *
     * @param versionId          申报版本 ID
     * @param taskId             审批任务 ID（可空）
     * @param expectedTaskVersion 任务期望版本
     * @param decision           决策：PASS/RETURN/NOT_ACCEPT
     * @param issues             问题项
     * @param comment            意见
     */
    public record FormalReviewRequest(
            String versionId,
            String taskId,
            Integer expectedTaskVersion,
            String decision,
            List<Issue> issues,
            String comment
    ) {

        /**
         * 审查问题项。
         *
         * @param field   字段路径
         * @param message 问题说明
         */
        public record Issue(String field, String message) {
        }
    }

    /**
     * 立项决策请求。
     *
     * @param versionId        申报版本 ID
     * @param decision         决策：APPROVE/REJECT
     * @param comment          意见
     * @param approvedStartDate 批准开始日期（APPROVE 必填）
     * @param approvedEndDate   批准结束日期（APPROVE 必填）
     */
    public record DecisionRequest(
            String versionId,
            String decision,
            String comment,
            LocalDate approvedStartDate,
            LocalDate approvedEndDate
    ) {
    }

    /**
     * 外部项目登记请求。
     *
     * @param batchId          申报批次 ID
     * @param projectType      项目类型
     * @param title            项目名称
     * @param deptId           承担部门
     * @param piId             项目负责人
     * @param funderName       资助单位
     * @param approvalNo       批复号
     * @param contractNo       合同号
     * @param financialProjectNo 财务项目号
     * @param approvedAmount   批准金额
     * @param startDate        开始日期
     * @param endDate          结束日期
     * @param verifiedBy       核验人
     * @param evidenceFileIds  证明材料
     */
    public record ExternalRegistrationRequest(
            String batchId,
            String projectType,
            String title,
            String deptId,
            String piId,
            String funderName,
            String approvalNo,
            String contractNo,
            String financialProjectNo,
            BigDecimal approvedAmount,
            LocalDate startDate,
            LocalDate endDate,
            String verifiedBy,
            List<String> evidenceFileIds
    ) {
    }
}
