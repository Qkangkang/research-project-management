package cn.edu.rpm.workflow.domain.port;

import cn.edu.rpm.workflow.domain.model.TemplateDefinition;

/**
 * 模板流程定义解析端口（T-WF-013；技术文档 03 §3.3）。
 *
 * <p>由基础设施适配器实现（jsonb 文本 ↔ 领域模型），使领域服务不依赖 Jackson。
 * 格式非法时抛 {@code RPM-SYS-001}。</p>
 */
public interface TemplateDefinitionCodec {

    /**
     * 解析 {@code wf_template_version.definition}。
     *
     * @param definitionJson 流程定义 JSON
     * @return 领域模型
     * @throws cn.edu.rpm.common.exception.BizException 格式非法 {@code RPM-SYS-001}
     */
    TemplateDefinition parse(String definitionJson);
}
