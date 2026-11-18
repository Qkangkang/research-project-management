package cn.edu.rpm.application.application.handler;

import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationReviewEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationReviewRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import cn.edu.rpm.workflow.domain.port.ApprovalResultHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 申报审批回调处理器（T-APP-021；技术文档 03 §3.2、07 §2.2）。
 *
 * <p>由 {@code WorkflowTaskAppService} 在通用审批任务动作后按 {@code bizType=APPLICATION}
 * 分派：</p>
 * <ul>
 *   <li>{@code onReturned}：申报置 {@code SUPPLEMENT} 并记录本次意见，旧意见保留；</li>
 *   <li>{@code onRejected}：申报与当前版本置 {@code REJECTED}；</li>
 *   <li>{@code onApproved}：<b>不直接终审</b>（立项由决策接口携带批准日期完成），
 *       只记录节点通过；</li>
 *   <li>{@code onWithdrawn}：申报置 {@code WITHDRAWN}。</li>
 * </ul>
 *
 * <p>幂等：业务状态已推进时直接返回，不重复写审查意见（同一 {@code requestId} 重复投递
 * 结果一致，TC-APP-046）。版本校验：{@code bizVersionId} 不是当前审批版本时记录
 * {@code STALE_APPROVAL} 并抛 {@code RPM-WF-005}（技术文档 03 §3.5 规则 1）。</p>
 */
@Component
public class ApplicationApprovalHandler implements ApprovalResultHandler {

    /** 审批业务类型。 */
    public static final String BIZ_TYPE = "APPLICATION";

    private static final String OBJECT_APPLICATION = "APPLICATION";

    private final ApplicationRepository applicationRepository;

    private final ApplicationVersionRepository applicationVersionRepository;

    private final ApplicationReviewRepository applicationReviewRepository;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造处理器。
     *
     * @param applicationRepository       申报主档仓储
     * @param applicationVersionRepository 申报版本仓储
     * @param applicationReviewRepository 审查意见仓储
     * @param auditRecorder               审计记录器
     * @param clock                       时间基准
     */
    public ApplicationApprovalHandler(ApplicationRepository applicationRepository,
                                      ApplicationVersionRepository applicationVersionRepository,
                                      ApplicationReviewRepository applicationReviewRepository,
                                      AuditRecorder auditRecorder,
                                      Clock clock) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository,
                "applicationRepository 不能为空");
        this.applicationVersionRepository = Objects.requireNonNull(applicationVersionRepository,
                "applicationVersionRepository 不能为空");
        this.applicationReviewRepository = Objects.requireNonNull(applicationReviewRepository,
                "applicationReviewRepository 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    /**
     * 审批节点通过：只记录，不生成项目（立项由决策接口完成）。
     *
     * @param result 审批结果
     * @throws BizException 版本过期 {@code RPM-WF-005}
     */
    @Override
    @Transactional
    public void onApproved(ApprovalResult result) {
        ApplicationEntity application = requireCurrentApplication(result);
        audit(application, result, "APPLICATION_APPROVAL_NODE_APPROVED", "审批节点通过");
    }

    /**
     * 退回补正：{@code IN_REVIEW} → {@code SUPPLEMENT}，记录本次意见（旧意见保留）。
     *
     * @param result 审批结果
     * @throws BizException 版本过期 {@code RPM-WF-005}
     */
    @Override
    @Transactional
    public void onReturned(ApprovalResult result) {
        ApplicationEntity application = requireCurrentApplication(result);
        if (!ApplicationEntity.STATUS_IN_REVIEW.equals(application.getStatus())) {
            return;
        }
        application.markSupplement();
        if (applicationRepository.update(application) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "申报已被并发修改，请刷新后重试");
        }
        insertReview(result, ApplicationReviewEntity.DECISION_RETURN);
        audit(application, result, "APPLICATION_RETURNED", "审批退回补正");
    }

    /**
     * 审批拒绝：{@code IN_REVIEW} → {@code REJECTED}（终态），当前版本 {@code REJECTED}。
     *
     * @param result 审批结果
     * @throws BizException 版本过期 {@code RPM-WF-005}
     */
    @Override
    @Transactional
    public void onRejected(ApprovalResult result) {
        ApplicationEntity application = requireCurrentApplication(result);
        if (ApplicationEntity.STATUS_REJECTED.equals(application.getStatus())) {
            return;
        }
        if (!ApplicationEntity.STATUS_IN_REVIEW.equals(application.getStatus())) {
            throw new BizException(ErrorCode.APPLY_004, "当前状态不允许审批拒绝");
        }
        application.markRejected();
        if (applicationRepository.update(application) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "申报已被并发修改，请刷新后重试");
        }
        applicationVersionRepository.findById(application.getCurrentVersionId())
                .ifPresent(version -> {
                    version.markRejected();
                    applicationVersionRepository.update(version);
                });
        insertReview(result, ApplicationReviewEntity.DECISION_REJECT);
        audit(application, result, "APPLICATION_REJECTED", "审批拒绝");
    }

    /**
     * 实例撤回/终止：{@code IN_REVIEW} → {@code WITHDRAWN}；已撤回时幂等返回。
     *
     * @param result 审批结果
     */
    @Override
    @Transactional
    public void onWithdrawn(ApprovalResult result) {
        ApplicationEntity application = requireCurrentApplication(result);
        if (ApplicationEntity.STATUS_WITHDRAWN.equals(application.getStatus())) {
            return;
        }
        if (!ApplicationEntity.STATUS_IN_REVIEW.equals(application.getStatus())) {
            return;
        }
        application.markWithdrawn();
        if (applicationRepository.update(application) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "申报已被并发修改，请刷新后重试");
        }
        audit(application, result, "APPLICATION_WITHDRAWN", "审批实例撤回");
    }

    private ApplicationEntity requireCurrentApplication(ApprovalResult result) {
        ApplicationEntity application = applicationRepository
                .selectForUpdate(UuidUtils.formatCompact(result.bizId()))
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报不存在"));
        String currentVersionId = application.getCurrentVersionId();
        String approvalVersionId = UuidUtils.formatCompact(result.bizVersionId());
        if (!UuidUtils.sameId(currentVersionId, approvalVersionId)) {
            auditRecorder.recordNonCritical(new AuditEvent(
                    TraceIds.currentOrUntracked(),
                    result.operatorId(),
                    result.onBehalfOf(),
                    "STALE_APPROVAL",
                    OBJECT_APPLICATION,
                    result.bizId(),
                    null,
                    null,
                    null,
                    "审批版本已过期",
                    null,
                    null,
                    clock.instant()));
            throw new BizException(ErrorCode.WF_005, "审批版本已过期");
        }
        return application;
    }

    private void insertReview(ApprovalResult result, String decision) {
        ApplicationReviewEntity review = new ApplicationReviewEntity();
        review.setApplicationVersionId(result.bizVersionId().toString().replace("-", ""));
        review.setReviewStage(ApplicationReviewEntity.STAGE_FORMAL);
        review.setReviewerId(UuidUtils.formatCompact(result.operatorId()));
        review.setDecision(decision);
        review.setIssues("[]");
        review.setComment(result.comment());
        review.setReviewedAt(OffsetDateTime.now(clock));
        applicationReviewRepository.insert(review);
    }

    private void audit(ApplicationEntity application, ApprovalResult result,
                       String action, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                result.operatorId(),
                result.onBehalfOf(),
                action,
                OBJECT_APPLICATION,
                UuidUtils.parse(application.getId()),
                application.getVersion(),
                null,
                null,
                reason,
                null,
                null,
                clock.instant()));
    }
}
