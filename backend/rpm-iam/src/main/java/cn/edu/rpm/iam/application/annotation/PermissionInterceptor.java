package cn.edu.rpm.iam.application.annotation;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 功能权限切面（T-IAM-014，技术文档 03 §2.1）。
 *
 * <p>拦截标注 {@link RequirePermission} 的 Controller 方法，在进入方法前校验
 * {@link AuthContext#permissionCodes()} 是否包含声明的全部权限码：</p>
 * <ul>
 *   <li>无认证上下文 → {@code RPM-COMMON-401}；</li>
 *   <li>任一权限缺失 → {@code RPM-COMMON-403}，并记录拒绝日志（安全日志要求）。</li>
 * </ul>
 *
 * <p>本切面只做粗筛，不替代应用服务的 {@code AuthorizationService.check(...)}。</p>
 */
@Aspect
@Component
public class PermissionInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionInterceptor.class);

    /**
     * Controller 方法前置功能权限校验。
     *
     * @param joinPoint        被拦截方法（用于安全日志定位）
     * @param requirePermission 方法上的权限声明
     * @throws BizException 未认证 {@code RPM-COMMON-401}；权限不足 {@code RPM-COMMON-403}
     */
    @Before("@annotation(requirePermission)")
    public void checkPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        AuthContext ctx = AuthContextHolder.require();
        boolean permitted = Arrays.stream(requirePermission.value())
                .allMatch(ctx::hasPermission);
        if (!permitted) {
            LOG.warn("功能权限不足 method={} required={} userId={}",
                    joinPoint.getSignature().toShortString(), Arrays.toString(requirePermission.value()),
                    ctx.userId());
            throw new BizException(ErrorCode.COMMON_403);
        }
    }
}
