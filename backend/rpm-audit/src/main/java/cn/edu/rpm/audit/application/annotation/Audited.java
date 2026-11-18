package cn.edu.rpm.audit.application.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 关键动作审计标注（T-AUD-003；技术文档 03 §4.2）。
 *
 * <p>标注在关键应用服务方法上，由 {@link AuditAspect} 在方法成功返回后自动写入
 * {@code audit_log}。业务事务回滚或方法抛出异常时不写审计（技术文档 03 §4.3）。</p>
 *
 * <p>敏感明文不会落库：切面只记录方法参数/返回值的 SHA-256 摘要。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * 受控动作码（如 {@code APPLICATION_SUBMIT}、{@code EXPENSE_APPROVED}）。
     *
     * @return 动作码
     */
    String action();

    /**
     * 对象类型（如 {@code APPLICATION}、{@code EXPENSE_REQUEST}）。
     *
     * @return 对象类型
     */
    String objectType();
}
