package cn.edu.rpm.iam.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * 审批职责分离校验上下文（技术文档 03 §2.6 {@code checkApproval}）。
 *
 * <p>按人员 ID 判断，不按账号名：</p>
 * <ul>
 *   <li>{@code applicantId == approverId} → 自审，拒绝（TC-IAM-011，
 *       {@code RPM-WF-003}）；</li>
 *   <li>{@code piId == financeFinalApproverId} → 项目负责人兼任财务终审，
 *       拒绝（TC-IAM-014，{@code RPM-WF-006}）；</li>
 * </ul>
 *
 * @param applicantId           申请人用户 ID
 * @param approverId            本次审批人用户 ID
 * @param piId                  项目负责人用户 ID；无项目上下文时为空
 * @param financeFinalApproverId 财务终审人用户 ID；无财务终审节点时为空
 */
public record ApprovalContext(
        UUID applicantId,
        UUID approverId,
        UUID piId,
        UUID financeFinalApproverId
) {

    /** 校验申请人与审批人。 */
    public ApprovalContext {
        Objects.requireNonNull(applicantId, "applicantId 不能为空");
        Objects.requireNonNull(approverId, "approverId 不能为空");
    }
}
