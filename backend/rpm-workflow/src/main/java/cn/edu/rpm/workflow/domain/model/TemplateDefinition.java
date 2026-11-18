package cn.edu.rpm.workflow.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 审批模板流程定义（T-WF-013；技术文档 03 §3.3）。
 *
 * <p>{@code wf_template_version.definition} 的领域视图。一期不开放任意 BPMN/脚本，
 * 仅允许受控的节点列表：每节点必须有可解析的处理人规则与退回策略。</p>
 *
 * @param nodes 节点列表（按审批顺序）
 */
public record TemplateDefinition(
        List<TemplateNode> nodes
) {

    /** 校验节点列表。 */
    public TemplateDefinition {
        Objects.requireNonNull(nodes, "nodes 不能为空");
        nodes = List.copyOf(nodes);
    }

    /**
     * 审批节点定义。
     *
     * @param key      节点 key（唯一）
     * @param name     节点名称
     * @param assignee 处理人规则（如 {@code role:research_admin}、{@code user:UUID}）
     * @param returnTo 退回目标节点 key；首节点退回到 {@code start}
     */
    public record TemplateNode(
            String key,
            String name,
            String assignee,
            String returnTo
    ) {
    }
}
