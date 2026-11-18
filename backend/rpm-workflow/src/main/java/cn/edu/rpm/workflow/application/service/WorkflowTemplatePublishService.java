package cn.edu.rpm.workflow.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.workflow.application.dto.PublishTemplateCmd;
import cn.edu.rpm.workflow.domain.model.BudgetConditionRange;
import cn.edu.rpm.workflow.domain.model.TemplateDefinition;
import cn.edu.rpm.workflow.domain.port.TemplateConditionMatcher;
import cn.edu.rpm.workflow.domain.port.TemplateDefinitionCodec;
import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateVersionEntity;
import cn.edu.rpm.workflow.infrastructure.repository.WfTemplateRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfTemplateVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 审批模板发布服务（T-WF-013；技术文档 03 §3.3）。
 *
 * <p>发布是唯一产生新模板版本的入口，发布前逐项校验：</p>
 * <ol>
 *   <li>流程定义可解析、首节点与其他节点处理人非空 → 阻断 {@code RPM-WF-002}
 *       （TC-APP-017，模板保持 DRAFT）；</li>
 *   <li>退回策略完整：每个节点 {@code returnTo} 必须指向 {@code start} 或更早节点
 *       → 否则 {@code RPM-SYS-001}；</li>
 *   <li>路由条件符合白名单且可解析 → 否则 {@code RPM-SYS-001}；</li>
 *   <li>同一模板下金额条件与仍在生效的已发布版本不重叠 → 否则 {@code RPM-SYS-001}
 *       （TC-APP-016 的发布期防线）；</li>
 *   <li>生效时间不得早于最新已发布版本 → 否则 {@code RPM-SYS-001}。</li>
 * </ol>
 *
 * <p>发布后：旧版本 {@code effective_to} 收口到新版本 {@code effective_from}，
 * 但已运行实例绑定旧 {@code template_version_id}，不随新版本变化（TC-APP-018）。</p>
 */
@Service
public class WorkflowTemplatePublishService {

    private final WfTemplateRepository templateRepository;

    private final WfTemplateVersionRepository templateVersionRepository;

    private final TemplateDefinitionCodec definitionCodec;

    private final TemplateConditionMatcher conditionMatcher;

    /**
     * 构造发布服务。
     *
     * @param templateRepository        模板主档仓储
     * @param templateVersionRepository 模板版本仓储
     * @param definitionCodec           流程定义解析器
     * @param conditionMatcher          条件校验/匹配器
     */
    public WorkflowTemplatePublishService(WfTemplateRepository templateRepository,
                                          WfTemplateVersionRepository templateVersionRepository,
                                          TemplateDefinitionCodec definitionCodec,
                                          TemplateConditionMatcher conditionMatcher) {
        this.templateRepository = Objects.requireNonNull(templateRepository, "templateRepository 不能为空");
        this.templateVersionRepository = Objects.requireNonNull(templateVersionRepository,
                "templateVersionRepository 不能为空");
        this.definitionCodec = Objects.requireNonNull(definitionCodec, "definitionCodec 不能为空");
        this.conditionMatcher = Objects.requireNonNull(conditionMatcher, "conditionMatcher 不能为空");
    }

    /**
     * 发布模板新版本。
     *
     * @param cmd 发布命令
     * @return 新发布的模板版本 ID
     * @throws BizException 模板不存在 {@code RPM-COMMON-404}；节点无处理人 {@code RPM-WF-002}；
     *                      退回策略/金额重叠/条件非法/发布冲突 {@code RPM-SYS-001}/{@code RPM-SYS-003}
     */
    @Transactional
    public UUID publish(PublishTemplateCmd cmd) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        String templateId = UuidUtils.formatCompact(cmd.templateId());
        WfTemplateEntity template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "审批模板不存在"));

        TemplateDefinition definition = definitionCodec.parse(cmd.definition());
        validateAssignees(definition);
        validateReturnPolicy(definition);
        conditionMatcher.validate(cmd.conditionExpr());
        validateEffectiveFrom(templateId, cmd.effectiveFrom());
        validateBudgetOverlap(templateId, cmd.conditionExpr(), cmd.effectiveFrom());

        int nextVersionNo = templateVersionRepository.findLatestByTemplateId(templateId)
                .map(latest -> latest.getVersionNo() + 1)
                .orElse(1);
        closeOpenVersions(templateId, cmd.effectiveFrom());

        OffsetDateTime now = OffsetDateTime.now();
        WfTemplateVersionEntity version = new WfTemplateVersionEntity();
        version.setTemplateId(templateId);
        version.setVersionNo(nextVersionNo);
        version.setDefinition(cmd.definition());
        version.setConditionExpr(cmd.conditionExpr() == null || cmd.conditionExpr().isBlank()
                ? "{}" : cmd.conditionExpr());
        version.setEffectiveFrom(cmd.effectiveFrom());
        version.setStatus(WfTemplateVersionEntity.STATUS_PUBLISHED);
        version.setPublishedBy(UuidUtils.formatCompact(cmd.publishedBy()));
        version.setPublishedAt(now);
        templateVersionRepository.insert(version);

        template.setStatus(WfTemplateEntity.STATUS_PUBLISHED);
        template.setCurrentVersionId(version.getId());
        if (templateRepository.update(template) == 0) {
            throw new BizException(ErrorCode.SYS_003, "审批模板发布冲突，请重试");
        }
        return UuidUtils.parse(version.getId());
    }

    private void validateAssignees(TemplateDefinition definition) {
        List<TemplateDefinition.TemplateNode> nodes = definition.nodes();
        TemplateDefinition.TemplateNode first = nodes.get(0);
        if (first.assignee() == null || first.assignee().isBlank()) {
            throw new BizException(ErrorCode.WF_002, "模板首节点无可用处理人: " + first.key());
        }
        for (TemplateDefinition.TemplateNode node : nodes) {
            if (node.assignee() == null || node.assignee().isBlank()) {
                throw new BizException(ErrorCode.WF_002, "审批节点无处理人: " + node.key());
            }
        }
    }

    private void validateReturnPolicy(TemplateDefinition definition) {
        Set<String> earlierKeys = new HashSet<>();
        for (TemplateDefinition.TemplateNode node : definition.nodes()) {
            String returnTo = node.returnTo();
            if (returnTo == null || returnTo.isBlank()) {
                throw new BizException(ErrorCode.SYS_001, "节点缺少退回策略: " + node.key());
            }
            boolean legal = "start".equals(returnTo) || earlierKeys.contains(returnTo);
            if (!legal) {
                throw new BizException(ErrorCode.SYS_001,
                        "节点退回目标必须为 start 或更早节点: " + node.key() + " -> " + returnTo);
            }
            earlierKeys.add(node.key());
        }
    }

    private void validateEffectiveFrom(String templateId, OffsetDateTime effectiveFrom) {
        Optional<WfTemplateVersionEntity> latest =
                templateVersionRepository.findLatestByTemplateId(templateId);
        if (latest.isPresent() && latest.get().getEffectiveFrom() != null
                && effectiveFrom.isBefore(latest.get().getEffectiveFrom())) {
            throw new BizException(ErrorCode.SYS_001, "新版本生效时间不得早于最新版本");
        }
    }

    private void validateBudgetOverlap(String templateId, String conditionExpr,
                                       OffsetDateTime effectiveFrom) {
        Optional<BudgetConditionRange> newRange = conditionMatcher.findBudgetRange(conditionExpr);
        if (newRange.isEmpty()) {
            return;
        }
        for (WfTemplateVersionEntity existing :
                templateVersionRepository.findPublishedByTemplateId(templateId)) {
            if (existing.getEffectiveTo() != null
                    && !existing.getEffectiveTo().isAfter(effectiveFrom)) {
                continue;
            }
            Optional<BudgetConditionRange> existingRange =
                    conditionMatcher.findBudgetRange(existing.getConditionExpr());
            if (existingRange.isPresent() && existingRange.get().overlaps(newRange.get())) {
                throw new BizException(ErrorCode.SYS_001,
                        "金额条件与已发布版本重叠: version=" + existing.getVersionNo());
            }
        }
    }

    private void closeOpenVersions(String templateId, OffsetDateTime effectiveFrom) {
        for (WfTemplateVersionEntity existing :
                templateVersionRepository.findPublishedByTemplateId(templateId)) {
            if (existing.getEffectiveTo() == null) {
                existing.setEffectiveTo(effectiveFrom);
                if (templateVersionRepository.update(existing) == 0) {
                    throw new BizException(ErrorCode.SYS_003, "模板版本收口冲突，请重试");
                }
            }
        }
    }
}
