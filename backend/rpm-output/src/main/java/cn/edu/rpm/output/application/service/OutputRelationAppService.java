package cn.edu.rpm.output.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.OutputRelationVO;
import cn.edu.rpm.output.application.dto.RelateOutputCmd;
import cn.edu.rpm.output.domain.model.OutputPermissions;
import cn.edu.rpm.output.domain.model.OutputResourceTypes;
import cn.edu.rpm.output.domain.model.ProjectAccessInfo;
import cn.edu.rpm.output.domain.port.OutputProjectAccessPort;
import cn.edu.rpm.output.infrastructure.entity.OutputProjectRelationEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputProjectRelationRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 成果-项目关联应用服务（T-OUT-012；技术文档 05 §4.3、07 §5.2）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>一个成果可关联多个项目，同一成果-项目唯一（{@code uk_output_project_relation}）；</li>
 *   <li>贡献说明必填；归属比例仅用于统计口径；</li>
 *   <li>成果编辑权限与目标项目可见性同时校验（TC-OUT-018）；</li>
 *   <li>重复有效关联返回 {@code RPM-OUT-003}；已撤销关联再次关联时恢复原记录；</li>
 *   <li>撤销为 {@code REVOKED} 状态迁移，不物理删除（TC-OUT-020）。</li>
 * </ul>
 */
@Service
public class OutputRelationAppService {

    private static final Logger LOG = LoggerFactory.getLogger(OutputRelationAppService.class);

    /** 审计动作：建立关联。 */
    static final String AUDIT_OUTPUT_RELATED = "OUTPUT_RELATED";

    /** 审计动作：恢复关联。 */
    static final String AUDIT_OUTPUT_RELATION_RESTORED = "OUTPUT_RELATION_RESTORED";

    /** 审计动作：撤销关联。 */
    static final String AUDIT_OUTPUT_RELATION_REVOKED = "OUTPUT_RELATION_REVOKED";

    private final ResearchOutputRepository researchOutputRepository;

    private final OutputProjectRelationRepository relationRepository;

    private final OutputProjectAccessPort projectAccessPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造关联服务。
     *
     * @param researchOutputRepository 成果仓储
     * @param relationRepository       成果-项目关联仓储
     * @param projectAccessPort        项目访问信息端口（rpm-boot 适配项目表）
     * @param authorizationService     统一授权服务
     * @param auditRecorder            审计记录器
     * @param clock                    时间基准
     */
    public OutputRelationAppService(ResearchOutputRepository researchOutputRepository,
                                    OutputProjectRelationRepository relationRepository,
                                    OutputProjectAccessPort projectAccessPort,
                                    AuthorizationService authorizationService,
                                    AuditRecorder auditRecorder,
                                    Clock clock) {
        this.researchOutputRepository = Objects.requireNonNull(researchOutputRepository,
                "researchOutputRepository 不能为空");
        this.relationRepository = Objects.requireNonNull(relationRepository,
                "relationRepository 不能为空");
        this.projectAccessPort = Objects.requireNonNull(projectAccessPort,
                "projectAccessPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 关联成果与项目。
     *
     * <p>触发场景：完成人把成果归属到项目。失败语义：成果不存在
     * {@code RPM-COMMON-404}；成果不可编辑或项目不可见 {@code RPM-COMMON-403}；
     * 贡献说明为空/比例越界 {@code RPM-COMMON-400}；重复关联 {@code RPM-OUT-003}；
     * 并发冲突 {@code RPM-COMMON-409}。</p>
     *
     * @param outputIdText 成果 ID
     * @param cmd          关联命令
     * @param auth         当前请求授权上下文
     * @return 关联结果
     */
    @Transactional
    public OutputRelationVO relate(String outputIdText, RelateOutputCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        String outputId = UuidUtils.formatCompact(parseUuid(outputIdText, "outputId"));
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        authorizeOutput(auth, output);

        String projectId = UuidUtils.formatCompact(parseUuid(cmd.projectId(), "projectId"));
        ProjectAccessInfo projectInfo = requireVisibleProject(auth, projectId);
        String contribution = trimToNull(cmd.contributionDesc());
        if (contribution == null) {
            throw new BizException(ErrorCode.COMMON_400, "贡献说明不能为空");
        }
        if (contribution.length() > 1024) {
            throw new BizException(ErrorCode.COMMON_400, "贡献说明不能超过 1024 字符");
        }
        if (cmd.attributionRatio() != null
                && (cmd.attributionRatio().compareTo(java.math.BigDecimal.ZERO) < 0
                || cmd.attributionRatio().compareTo(java.math.BigDecimal.ONE) > 0)) {
            throw new BizException(ErrorCode.COMMON_400, "归属比例必须在 0～1 之间");
        }

        String actorId = UuidUtils.formatCompact(auth.userId());
        OffsetDateTime now = OffsetDateTime.now(clock);
        OutputProjectRelationEntity relation = relationRepository
                .findByOutputIdAndProjectId(outputId, projectId)
                .orElse(null);
        String action;
        int updated;
        if (relation == null) {
            try {
                relation = OutputProjectRelationEntity.create(
                        outputId, projectId, contribution, cmd.attributionRatio(), actorId);
            } catch (IllegalArgumentException ex) {
                throw new BizException(ErrorCode.COMMON_400, ex.getMessage());
            }
            relationRepository.insert(relation);
            action = AUDIT_OUTPUT_RELATED;
            updated = 1;
        } else if (OutputProjectRelationEntity.STATUS_ACTIVE.equals(relation.getStatus())) {
            throw new BizException(ErrorCode.OUT_003, "该成果与项目已存在有效关联");
        } else {
            try {
                relation.restore(contribution, cmd.attributionRatio());
            } catch (IllegalArgumentException ex) {
                throw new BizException(ErrorCode.COMMON_400, ex.getMessage());
            }
            updated = relationRepository.update(relation);
            action = AUDIT_OUTPUT_RELATION_RESTORED;
        }
        if (updated == 0) {
            throw new BizException(ErrorCode.COMMON_409, "关联已被并发修改，请刷新后重试");
        }

        audit(auth, relation, action, now, "关联成果到项目 " + projectId);
        LOG.debug("成果关联项目 outputId={} projectId={} action={}", outputId, projectId, action);
        return toVo(relation);
    }

    /**
     * 撤销成果-项目关联（软删除，保留历史与验收快照引用）。
     *
     * <p>触发场景：发现关联错误或项目不再使用该成果。重复撤销幂等返回
     * {@code REVOKED}。失败语义：成果/关联不存在 {@code RPM-COMMON-404}；
     * 无权限 {@code RPM-COMMON-403}；并发冲突 {@code RPM-COMMON-409}。</p>
     *
     * @param outputIdText   成果 ID
     * @param relationIdText 关联 ID
     * @param auth           当前请求授权上下文
     * @return 撤销后的关联
     */
    @Transactional
    public OutputRelationVO revoke(String outputIdText, String relationIdText, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        String outputId = UuidUtils.formatCompact(parseUuid(outputIdText, "outputId"));
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        authorizeOutput(auth, output);

        String relationId = UuidUtils.formatCompact(parseUuid(relationIdText, "relationId"));
        OutputProjectRelationEntity relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "关联不存在"));
        if (!UuidUtils.sameId(relation.getOutputId(), outputId)) {
            throw new BizException(ErrorCode.COMMON_404, "关联不存在");
        }
        if (OutputProjectRelationEntity.STATUS_REVOKED.equals(relation.getStatus())) {
            return toVo(relation);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        relation.revoke(UuidUtils.formatCompact(auth.userId()), now);
        if (relationRepository.update(relation) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "关联已被并发修改，请刷新后重试");
        }
        audit(auth, relation, AUDIT_OUTPUT_RELATION_REVOKED, now, "撤销成果项目关联");
        return toVo(relation);
    }

    private ProjectAccessInfo requireVisibleProject(AuthContext auth, String projectId) {
        ProjectAccessInfo info = projectAccessPort.find(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_403,
                        "无权访问目标项目"));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(OutputPermissions.PROJECT_VIEW),
                ResourceRef.project(info.projectId(), info.piId(), info.deptId(),
                        info.memberUserIds()));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403, "无权访问目标项目");
        }
        return info;
    }

    private void authorizeOutput(AuthContext auth, ResearchOutputEntity output) {
        AuthDecision decision = authorizationService.check(auth, Action.of(OutputPermissions.RELATE),
                new ResourceRef(OutputResourceTypes.OUTPUT,
                        UuidUtils.parse(output.getId()),
                        UuidUtils.parse(output.getOwnerId()),
                        UuidUtils.parse(output.getDeptId()),
                        Set.of(UuidUtils.parse(output.getOwnerId()))));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private void audit(AuthContext auth, OutputProjectRelationEntity relation,
                       String action, OffsetDateTime now, String reason) {
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                action,
                "OUTPUT_PROJECT_RELATION",
                UuidUtils.parse(relation.getId()),
                relation.getVersion(),
                null,
                null,
                reason,
                null,
                null,
                now.toInstant()));
    }

    private static OutputRelationVO toVo(OutputProjectRelationEntity relation) {
        return new OutputRelationVO(
                relation.getId(),
                relation.getOutputId(),
                relation.getProjectId(),
                relation.getContributionDesc(),
                relation.getAttributionRatio(),
                relation.getStatus());
    }

    private static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, field + " 不能为空");
        }
        try {
            return UuidUtils.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, field + " 非法");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
