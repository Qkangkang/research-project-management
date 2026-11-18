package cn.edu.rpm.workflow.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.workflow.domain.dto.TemplateRoutingContext;
import cn.edu.rpm.workflow.domain.model.WorkflowTemplateCandidate;
import cn.edu.rpm.workflow.domain.model.WorkflowTemplateVersionCandidate;
import cn.edu.rpm.workflow.domain.port.TemplateConditionMatcher;
import cn.edu.rpm.workflow.domain.port.WorkflowTemplateQueryPort;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 审批模板路由（T-WF-012；技术文档 03 §3.3）。
 *
 * <p>路由步骤：</p>
 * <ol>
 *   <li>取全部已发布模板，按 {@code project_type} 粗筛（模板项目类型为空表示不限）；</li>
 *   <li>取每个模板在业务时点生效的版本；</li>
 *   <li>按版本 {@code condition_expr} 白名单条件精确匹配；</li>
 *   <li>恰好命中 1 个版本返回其 ID；0 个抛 {@code RPM-WF-001}；
 *       多个抛 {@code RPM-SYS-001}（条件冲突，禁止启动审批实例）。</li>
 * </ol>
 *
 * <p>已运行实例在启动时绑定 {@code template_version_id}，后续模板发布不影响旧实例。</p>
 */
public class WorkflowTemplateResolver {

    private final WorkflowTemplateQueryPort templateQueryPort;

    private final TemplateConditionMatcher conditionMatcher;

    /**
     * 构造路由服务。
     *
     * @param templateQueryPort 模板查询端口
     * @param conditionMatcher  条件匹配器
     */
    public WorkflowTemplateResolver(WorkflowTemplateQueryPort templateQueryPort,
                                    TemplateConditionMatcher conditionMatcher) {
        this.templateQueryPort = Objects.requireNonNull(templateQueryPort, "templateQueryPort 不能为空");
        this.conditionMatcher = Objects.requireNonNull(conditionMatcher, "conditionMatcher 不能为空");
    }

    /**
     * 解析业务上下文命中的唯一模板版本（TC-APP-015 / TC-APP-016）。
     *
     * @param context 路由上下文（项目类型、预算、部门等白名单字段）
     * @param at      业务时点（用于选择生效版本）
     * @return 命中的模板版本 ID
     * @throws BizException 无可用模板 {@code RPM-WF-001}；命中多个模板 {@code RPM-SYS-001}
     */
    public UUID resolve(TemplateRoutingContext context, OffsetDateTime at) {
        Objects.requireNonNull(context, "context 不能为空");
        Objects.requireNonNull(at, "at 不能为空");

        List<UUID> matchedVersions = new ArrayList<>();
        for (WorkflowTemplateCandidate candidate : templateQueryPort.findPublishedTemplates()) {
            if (candidate.projectType() != null
                    && !candidate.projectType().equals(context.projectType())) {
                continue;
            }
            WorkflowTemplateVersionCandidate version = templateQueryPort
                    .findEffectiveVersion(candidate.templateId().toString(), at)
                    .orElse(null);
            if (version != null && conditionMatcher.matches(version.conditionExpr(), context)) {
                matchedVersions.add(version.versionId());
            }
        }

        if (matchedVersions.isEmpty()) {
            throw new BizException(ErrorCode.WF_001, "未找到可用审批模板");
        }
        if (matchedVersions.size() > 1) {
            throw new BizException(ErrorCode.SYS_001, "模板条件冲突：命中 " + matchedVersions.size() + " 个模板版本");
        }
        return matchedVersions.get(0);
    }
}
