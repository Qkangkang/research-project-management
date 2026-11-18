package cn.edu.rpm.workflow.domain.port;

import cn.edu.rpm.workflow.domain.model.WorkflowTemplateCandidate;
import cn.edu.rpm.workflow.domain.model.WorkflowTemplateVersionCandidate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 审批模板路由查询端口（T-WF-012；技术文档 03 §3.3）。
 *
 * <p>由基础设施适配器实现（读取 {@code wf_template} / {@code wf_template_version}），
 * 使领域服务不依赖 MyBatis 实体。</p>
 */
public interface WorkflowTemplateQueryPort {

    /**
     * 查询全部已发布（{@code status = PUBLISHED}）模板。
     *
     * @return 模板候选列表；无命中返回空列表
     */
    List<WorkflowTemplateCandidate> findPublishedTemplates();

    /**
     * 查询指定模板在业务时点生效的版本。
     *
     * <p>生效口径：{@code status = PUBLISHED}、{@code effective_from <= at}、
     * {@code effective_to} 为空或 {@code effective_to > at}；区间重叠时取
     * {@code effective_from} 最新的一条。</p>
     *
     * @param templateId 模板 ID
     * @param at         业务时点
     * @return 生效版本；无命中返回 {@link Optional#empty()}
     */
    Optional<WorkflowTemplateVersionCandidate> findEffectiveVersion(String templateId, OffsetDateTime at);

    /**
     * 按模板业务编码查询指定时点生效的版本（业务模块按编码引用模板，如
     * {@code MILESTONE_DELAY}；Batch-12 里程碑延期使用）。
     *
     * <p>生效口径与 {@link #findEffectiveVersion(String, OffsetDateTime)} 相同；
     * 编码不存在或无生效版本时返回 {@link Optional#empty()}。</p>
     *
     * @param templateCode 模板业务编码（{@code wf_template.code}，全局唯一）
     * @param at           业务时点
     * @return 生效版本；无命中返回 {@link Optional#empty()}
     */
    Optional<WorkflowTemplateVersionCandidate> findEffectiveVersionByCode(String templateCode,
                                                                        OffsetDateTime at);
}
