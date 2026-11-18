package cn.edu.rpm.application.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * 形式审查命令（T-APP-017；技术文档 04 §3.3、08 §9.1）。
 *
 * <p>{@code decision} 取值 {@code PASS / RETURN / NOT_ACCEPT}。{@code RETURN} 必须提供
 * 非空 {@code issues}；{@code NOT_ACCEPT} 必须提供不少于 10 字的 {@code comment}。
 * {@code taskId} 为当前审批任务 ID：携带时同步完成/终止审批任务；由通用审批任务接口
 * 驱动流程时可为空（此时本服务只落业务审查结论与状态）。</p>
 *
 * @param versionId           被审查的申报版本 ID（必须是当前提交版本）
 * @param taskId              当前审批任务 ID；可为空
 * @param expectedTaskVersion 读取任务时的 {@code wf_task.version}（携带 taskId 时必填，
 *                            用于并发控制，旧版本返回 {@code RPM-WF-004}）
 * @param decision            审查结论：PASS / RETURN / NOT_ACCEPT
 * @param issues              问题项（RETURN 时至少一条）
 * @param comment             审查意见（NOT_ACCEPT 时必填且不少于 10 字）
 * @param requestId           请求幂等键（审批动作幂等）
 */
public record FormalReviewCmd(
        String versionId,
        String taskId,
        Integer expectedTaskVersion,
        String decision,
        List<Issue> issues,
        String comment,
        UUID requestId
) {

    /** 规范化问题项集合。 */
    public FormalReviewCmd {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * 形式审查问题项。
     *
     * @param field   定位字段（如 {@code budget.total}）
     * @param message 问题说明
     */
    public record Issue(String field, String message) {
    }
}
