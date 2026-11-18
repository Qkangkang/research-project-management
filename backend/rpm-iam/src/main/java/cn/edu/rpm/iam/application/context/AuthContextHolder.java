package cn.edu.rpm.iam.application.context;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.AuthContext;

/**
 * 请求级授权上下文持有器（T-IAM-014 辅助）。
 *
 * <p>由接入层认证过滤器在请求入口写入 {@link #set(AuthContext)}，请求结束在 finally 中
 * {@link #clear()}，防止线程池复用串号（与 {@code AuditUserContext} 同一口径）。
 * 未认证访问受保护方法时返回 {@code RPM-COMMON-401}。</p>
 */
public final class AuthContextHolder {

    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    /**
     * 设置当前请求授权上下文。
     *
     * @param context 授权上下文（登录成功后的快照）
     */
    public static void set(AuthContext context) {
        HOLDER.set(context);
    }

    /**
     * 读取当前请求授权上下文。
     *
     * @return 上下文；未认证返回 {@code null}
     */
    public static AuthContext current() {
        return HOLDER.get();
    }

    /**
     * 读取当前请求授权上下文，未认证时抛出业务异常。
     *
     * @return 上下文
     * @throws BizException 未登录或会话失效 {@code RPM-COMMON-401}
     */
    public static AuthContext require() {
        AuthContext context = HOLDER.get();
        if (context == null) {
            throw new BizException(ErrorCode.COMMON_401);
        }
        return context;
    }

    /**
     * 清理上下文，必须在请求结束（finally）调用。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
