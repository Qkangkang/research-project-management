package cn.edu.rpm.workflow.infrastructure.adapter;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.workflow.domain.model.WorkflowTemplateCandidate;
import cn.edu.rpm.workflow.domain.model.WorkflowTemplateVersionCandidate;
import cn.edu.rpm.workflow.domain.port.WorkflowTemplateQueryPort;
import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateVersionEntity;
import cn.edu.rpm.workflow.infrastructure.repository.WfTemplateRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfTemplateVersionRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 模板路由查询端口适配器（T-WF-012；技术文档 03 §3.3）。
 *
 * <p>把 {@code wf_template} / {@code wf_template_version} 单表仓储转换为领域候选模型，
 * 使 {@code WorkflowTemplateResolver} 不依赖 MyBatis 实体。模板 ID 入参统一规范化为
 * 32 位无连字符文本（与 MyBatis-Plus 主键形态一致），避免调用方形态差异导致查不到。</p>
 */
@Repository
public class WorkflowTemplateQueryAdapter implements WorkflowTemplateQueryPort {

    private final WfTemplateRepository templateRepository;

    private final WfTemplateVersionRepository templateVersionRepository;

    /**
     * 构造适配器。
     *
     * @param templateRepository        模板主档仓储
     * @param templateVersionRepository 模板版本仓储
     */
    public WorkflowTemplateQueryAdapter(WfTemplateRepository templateRepository,
                                        WfTemplateVersionRepository templateVersionRepository) {
        this.templateRepository = Objects.requireNonNull(templateRepository, "templateRepository 不能为空");
        this.templateVersionRepository = Objects.requireNonNull(templateVersionRepository,
                "templateVersionRepository 不能为空");
    }

    @Override
    public List<WorkflowTemplateCandidate> findPublishedTemplates() {
        return templateRepository.findAll().stream()
                .filter(template -> WfTemplateEntity.STATUS_PUBLISHED.equals(template.getStatus()))
                .map(template -> new WorkflowTemplateCandidate(
                        UuidUtils.parse(template.getId()), template.getProjectType()))
                .toList();
    }

    @Override
    public Optional<WorkflowTemplateVersionCandidate> findEffectiveVersion(String templateId,
                                                                          OffsetDateTime at) {
        String normalized = UuidUtils.formatCompact(UuidUtils.parse(templateId));
        return templateVersionRepository.findEffectiveByTemplateId(normalized, at)
                .map(version -> new WorkflowTemplateVersionCandidate(
                        UuidUtils.parse(version.getId()), version.getConditionExpr()));
    }

    @Override
    public Optional<WorkflowTemplateVersionCandidate> findEffectiveVersionByCode(String templateCode,
                                                                                OffsetDateTime at) {
        if (templateCode == null || templateCode.isBlank()) {
            return Optional.empty();
        }
        return templateRepository.findByCode(templateCode.trim())
                .flatMap(template -> findEffectiveVersion(template.getId(), at));
    }
}
