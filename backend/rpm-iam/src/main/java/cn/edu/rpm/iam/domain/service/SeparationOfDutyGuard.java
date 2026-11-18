package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.ApprovalContext;
import cn.edu.rpm.iam.application.dto.ExpertContext;
import cn.edu.rpm.iam.domain.model.CoiDeclarationStatus;
import cn.edu.rpm.iam.domain.port.CoiDeclarationQueryPort;

import java.util.Objects;

/**
 * 职责分离与利益冲突守护（T-IAM-011，技术文档 03 §2.6）。
 *
 * <p>规则（按人员 ID 判断，不按账号名）：</p>
 * <ol>
 *   <li>申请人不得审批本人事项——抛 {@code RPM-WF-003}（TC-IAM-011）；</li>
 *   <li>PI 与财务终审不得为同一人——抛 {@code RPM-WF-006}（TC-IAM-014）；</li>
 *   <li>专家与申请人同部门时，必须存在已处置（{@code CONFIRMED / EXEMPTED}）的
 *       利益冲突声明，否则抛 {@code RPM-WF-006}（TC-IAM-012）。</li>
 * </ol>
 *
 * <p>异常直接抛出而非返回判定：工作流任务处理（Batch-6/7）在事务内调用本守护，
 * 校验失败必须阻止任务推进与业务回写。</p>
 */
public class SeparationOfDutyGuard {

    private final CoiDeclarationQueryPort coiDeclarationQueryPort;

    public SeparationOfDutyGuard(CoiDeclarationQueryPort coiDeclarationQueryPort) {
        this.coiDeclarationQueryPort = Objects.requireNonNull(coiDeclarationQueryPort,
                "coiDeclarationQueryPort 不能为空");
    }

    /**
     * 审批职责分离校验：自审阻断、PI/财务终审冲突阻断。
     *
     * @param ctx 审批上下文
     * @throws BizException 自审 {@code RPM-WF-003}；PI 兼任财务终审 {@code RPM-WF-006}
     */
    public void checkApproval(ApprovalContext ctx) {
        Objects.requireNonNull(ctx, "审批上下文不能为空");
        if (ctx.applicantId().equals(ctx.approverId())) {
            throw new BizException(ErrorCode.WF_003, "禁止审批本人事项");
        }
        if (ctx.piId() != null && ctx.piId().equals(ctx.financeFinalApproverId())) {
            throw new BizException(ErrorCode.WF_006, "PI 与财务终审不得为同一人");
        }
    }

    /**
     * 专家利益冲突校验：同部门且无已处置声明时阻断评审。
     *
     * @param ctx 专家评审上下文
     * @throws BizException 存在未处置的利益冲突 {@code RPM-WF-006}
     */
    public void checkExpert(ExpertContext ctx) {
        Objects.requireNonNull(ctx, "专家上下文不能为空");
        if (ctx.expertDeptId() == null || ctx.applicantDeptId() == null
                || !ctx.expertDeptId().equals(ctx.applicantDeptId())) {
            return;
        }
        boolean resolved = coiDeclarationQueryPort
                .findByBizAndExpert(ctx.bizType(), UuidUtils.formatCompact(ctx.bizId()),
                        UuidUtils.formatCompact(ctx.expertId()))
                .stream()
                .anyMatch(declaration -> ctx.applicantId().equals(declaration.relatedUserId())
                        && CoiDeclarationStatus.isResolved(declaration.status()));
        if (!resolved) {
            throw new BizException(ErrorCode.WF_006, "专家与申请人同部门且存在未处理的利益冲突");
        }
    }
}
