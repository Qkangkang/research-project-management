package cn.edu.rpm.boot.config;

/**
 * 审计操作人上下文（T-ENV-005 辅助）。
 *
 * <p>以 ThreadLocal 保存当前请求的操作人标识，供 {@link AuditMetaObjectHandler}
 * 自动填充 {@code created_by} / {@code updated_by}。请求级状态必须在请求结束时
 * {@link #clear()}（由 {@link AuditActorFilter} 在 finally 中清理），防止线程池复用串号。
 * 上下文缺失（如系统任务、后台 Job）时返回 {@code null}，审计列保持为空；数据库列是
 * {@code uuid} 且部分列有 {@code iam_user} 外键，不能写 {@code system} 之类非 UUID 占位值。</p>
 */
public final class AuditUserContext {

    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private AuditUserContext() {
    }

    /**
     * 设置当前请求操作人（登录用户 ID），由 Web 层过滤器在请求入口调用。
     *
     * @param actor 操作人标识（用户 ID，不使用账号名）
     */
    public static void set(String actor) {
        ACTOR.set(actor);
    }

    /**
     * 读取当前操作人；未设置时返回 {@code null}（系统任务不写审计操作人）。
     *
     * @return 操作人用户 ID；无请求上下文时为 {@code null}
     */
    public static String currentActor() {
        return ACTOR.get();
    }

    /**
     * 清理上下文，必须在请求结束（finally）调用，防止线程池复用串号。
     */
    public static void clear() {
        ACTOR.remove();
    }
}
