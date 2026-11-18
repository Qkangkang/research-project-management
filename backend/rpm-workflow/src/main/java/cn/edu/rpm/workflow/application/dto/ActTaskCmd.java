package cn.edu.rpm.workflow.application.dto;

import cn.edu.rpm.workflow.domain.dto.ApprovalAction;

import java.util.Objects;
import java.util.UUID;

/**
 * 审批任务处理命令（T-WF-014；技术文档 04 §3.11）。
 *
 * <p>普通处理与重派共用 {@code act} 入口（TC-APP-026 的验证用例锚点为
 * {@code WorkflowTaskAppService.act}）：</p>
 * <ul>
 *   <li>普通处理：{@code action} 必填，{@code reassignTo} 为空；</li>
 *   <li>重派：{@code reassignTo} 必填且 {@code action} 必须为空，仅超时/异常任务可重派。</li>
 * </ul>
 *
 * @param requestId       请求幂等键（TC-APP-023）
 * @param expectedVersion 读取任务时的版本号（TC-APP-024）
 * @param action          处理动作：APPROVED / RETURNED / REJECTED；重派时为空
 * @param comment         处理意见（退回/拒绝/重派必填，最长 2048）
 * @param onBehalfOf      委托人用户 ID（代理审批时填写；代理人与委托人必须存在有效委托）
 * @param reassignTo      新处理人用户 ID（仅管理员重派时填写）
 */
public record ActTaskCmd(
        UUID requestId,
        Integer expectedVersion,
        ApprovalAction action,
        String comment,
        UUID onBehalfOf,
        UUID reassignTo
) {

    /** 处理意见最大长度（与 wf_task.comment 列一致）。 */
    public static final int MAX_COMMENT_LENGTH = 2048;

    /** 校验组合约束。 */
    public ActTaskCmd {
        Objects.requireNonNull(requestId, "requestId 不能为空");
        Objects.requireNonNull(expectedVersion, "expectedVersion 不能为空");
        if (reassignTo == null) {
            Objects.requireNonNull(action, "action 不能为空");
        } else if (action != null) {
            throw new IllegalArgumentException("重派任务时不能同时指定处理动作");
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("处理意见不能超过 " + MAX_COMMENT_LENGTH + " 字");
        }
        boolean commentRequired = reassignTo != null
                || action == ApprovalAction.RETURNED
                || action == ApprovalAction.REJECTED;
        if (commentRequired && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException("退回、拒绝或重派必须填写意见/原因");
        }
    }
}
