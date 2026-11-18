package cn.edu.rpm.iam.application.annotation;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionInterceptor} 纯单元测试（T-IAM-014 先行测试，技术文档 03 §2.1）。
 *
 * <p>验收锚点：Controller 方法所需功能权限全部具备时放行；缺失时抛
 * {@code RPM-COMMON-403}；无认证上下文时抛 {@code RPM-COMMON-401}。
 * 注解只做功能权限粗筛，数据范围仍由应用服务调用 {@code AuthorizationService.check} 完成。</p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionInterceptorTest {

    private static final UUID USER_ID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final UUID ROLE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant REQUEST_TIME = Instant.parse("2026-11-17T10:00:00Z");

    @Mock
    private JoinPoint joinPoint;

    private final PermissionInterceptor interceptor = new PermissionInterceptor();

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    private void stubSignature() {
        Signature signature = new Signature() {
            @Override
            public String toShortString() {
                return "SampleController.secured()";
            }

            @Override
            public String toLongString() {
                return "cn.edu.rpm.iam.application.annotation.PermissionInterceptorTest$SampleController.secured()";
            }

            @Override
            public String getName() {
                return "secured";
            }

            @Override
            public int getModifiers() {
                return 1;
            }

            @Override
            public Class<?> getDeclaringType() {
                return SampleController.class;
            }

            @Override
            public String getDeclaringTypeName() {
                return SampleController.class.getName();
            }
        };
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    private static RequirePermission annotationOf(String methodName) {
        try {
            return SampleController.class.getMethod(methodName).getAnnotation(RequirePermission.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void passesWhenAllRequiredPermissionsArePresent() {
        AuthContextHolder.set(new AuthContext(USER_ID, ROLE_ID, "E1001",
                Set.of("project:view", "project:edit"), Set.of(), REQUEST_TIME));

        assertThatCode(() -> interceptor.checkPermission(joinPoint, annotationOf("secured")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWith403WhenAnyRequiredPermissionIsMissing() {
        stubSignature();
        AuthContextHolder.set(new AuthContext(USER_ID, ROLE_ID, "E1001",
                Set.of("project:view"), Set.of(), REQUEST_TIME));

        assertThatThrownBy(() -> interceptor.checkPermission(joinPoint, annotationOf("secured")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
    }

    @Test
    void rejectsWith401WhenAuthContextIsAbsent() {
        assertThatThrownBy(() -> interceptor.checkPermission(joinPoint, annotationOf("secured")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_401));
    }

    /**
     * 模拟 Controller：方法上的权限注解由拦截器读取。
     */
    static class SampleController {

        /**
         * 受保护方法：同时要求两个功能权限。
         */
        @RequirePermission({"project:view", "project:edit"})
        public void secured() {
        }
    }
}
