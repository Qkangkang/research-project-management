package cn.edu.rpm.application.domain.model;

/**
 * 表单字段校验错误（T-APP-013；技术文档 04 §3.2 表单校验）。
 *
 * <p>由 {@link cn.edu.rpm.application.domain.service.ApplyFormTemplateResolver#validate}
 * 汇总返回，应用服务据此抛 {@code RPM-APPLY-001}；错误只描述字段与原因，
 * 不回显表单值，避免日志与响应泄露敏感内容。</p>
 *
 * @param field   字段路径（JSON Schema properties 的 key）
 * @param message 校验失败原因（面向用户的固定文案）
 */
public record FormValidationError(String field, String message) {
}
