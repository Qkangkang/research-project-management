package cn.edu.rpm.iam.application.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 方法功能权限声明（T-IAM-014，技术文档 03 §2.1）。
 *
 * <p>只做功能权限粗筛：{@code value} 中的权限码必须全部存在于
 * {@link cn.edu.rpm.iam.application.dto.AuthContext#permissionCodes()} 才放行；
 * 数据范围、业务状态、字段权限仍必须由应用服务调用
 * {@code AuthorizationService.check(...)} 完成，禁止以本注解替代最终授权。</p>
 *
 * <p>示例：{@code @RequirePermission("project:view")}</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 所需功能权限码（全部满足才放行）。
     *
     * @return 权限码数组
     */
    String[] value();
}
