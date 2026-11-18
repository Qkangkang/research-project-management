package cn.edu.rpm.workflow.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.workflow.domain.model.TemplateDefinition;
import cn.edu.rpm.workflow.domain.port.TemplateDefinitionCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 Jackson 的模板流程定义解析器（T-WF-013；技术文档 03 §3.3）。
 *
 * <p>受控格式：</p>
 * <pre>{@code
 * {"nodes":[
 *   {"key":"formalReview","name":"形式审查","assignee":"role:research_admin","returnTo":"start"},
 *   {"key":"decision","name":"决策","assignee":"role:leader","returnTo":"formalReview"}
 * ]}
 * }</pre>
 * <p>结构与节点 key 唯一性在本类校验（非法抛 {@code RPM-SYS-001}）；
 * "处理人非空 / 退回目标合法"由发布服务校验，以便区分 {@code RPM-WF-002} 与
 * {@code RPM-SYS-001}。</p>
 */
@Repository
public class JacksonTemplateDefinitionCodec implements TemplateDefinitionCodec {

    private final ObjectMapper objectMapper;

    /**
     * 构造解析器。
     *
     * @param objectMapper JSON 解析器
     */
    public JacksonTemplateDefinitionCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    @Override
    public TemplateDefinition parse(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            throw invalid("流程定义不能为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(definitionJson);
        } catch (Exception ex) {
            throw invalid("流程定义不是合法 JSON");
        }
        if (root == null || !root.isObject()) {
            throw invalid("流程定义根节点必须是对象");
        }
        JsonNode nodes = root.get("nodes");
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            throw invalid("流程定义 nodes 必须是非空数组");
        }

        List<TemplateDefinition.TemplateNode> result = new ArrayList<>(nodes.size());
        Set<String> keys = new HashSet<>();
        for (JsonNode node : nodes) {
            if (!node.isObject()) {
                throw invalid("节点必须是对象");
            }
            String key = text(node, "key");
            String name = text(node, "name");
            String assignee = text(node, "assignee");
            String returnTo = text(node, "returnTo");
            if (key == null || key.isBlank()) {
                throw invalid("节点 key 不能为空");
            }
            if (!keys.add(key)) {
                throw invalid("节点 key 重复: " + key);
            }
            if (name == null || name.isBlank()) {
                throw invalid("节点 name 不能为空: " + key);
            }
            result.add(new TemplateDefinition.TemplateNode(key, name, assignee, returnTo));
        }
        return new TemplateDefinition(result);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.SYS_001, "流程定义不可解析: " + message);
    }
}
