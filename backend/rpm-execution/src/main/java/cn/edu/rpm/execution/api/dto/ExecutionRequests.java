package cn.edu.rpm.execution.api.dto;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 执行接口请求体集合（T-EXEC-010；技术文档 04 §2.3、09 §4.1）。
 *
 * <p>仅承载 HTTP 形态字段；业务校验在应用服务内完成。</p>
 */
public final class ExecutionRequests {

    private ExecutionRequests() {
    }

    /**
     * 创建报告草稿请求。
     *
     * @param periodType 周期类型
     * @param periodKey  周期键
     */
    public record CreateReportRequest(String periodType, String periodKey) {
    }

    /**
     * 提交报告请求。
     *
     * @param expectedVersion    期望版本
     * @param content            报告内容
     * @param deliverableFileIds 交付物文件 ID
     */
    public record SubmitReportRequest(
            Integer expectedVersion,
            Map<String, Object> content,
            List<String> deliverableFileIds
    ) {
    }

    /**
     * 审核报告请求。
     *
     * @param action  动作：RETURN / APPROVE
     * @param comment 意见
     */
    public record ReviewReportRequest(String action, String comment) {
    }

    /**
     * 上传交付物请求。
     *
     * @param fileId          文件对象 ID
     * @param title           名称
     * @param milestoneId     关联里程碑 ID
     * @param reportVersionId 关联报告版本 ID
     * @param sha256          内容 SHA-256
     */
    public record UploadDeliverableRequest(
            String fileId,
            String title,
            String milestoneId,
            String reportVersionId,
            String sha256
    ) {
    }

    /**
     * 创建轻任务请求。
     *
     * @param title         标题
     * @param description   描述
     * @param assigneeId    处理人
     * @param dueDate       截止日期
     * @param deliverableId 关联交付物
     */
    public record CreateTaskRequest(
            String title,
            String description,
            String assigneeId,
            LocalDate dueDate,
            String deliverableId
    ) {
    }

    /**
     * 完成轻任务请求。
     *
     * @param deliverableId 关联交付物
     */
    public record CompleteTaskRequest(String deliverableId) {
    }

    /**
     * 取消轻任务请求。
     *
     * @param reason 取消理由
     */
    public record CancelTaskRequest(String reason) {
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
            throw new BizException(ErrorCode.COMMON_400, "Idempotency-Key 非法");
        }
    }
}
