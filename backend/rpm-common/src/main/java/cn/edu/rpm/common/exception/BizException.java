package cn.edu.rpm.common.exception;

/**
 * 业务异常：携带 {@link ErrorCode} 与可选的自定义消息。
 *
 * <p>触发场景：应用服务/领域服务在业务规则不满足时抛出，
 * 由 {@link GlobalExceptionHandler} 统一转换为文档约定的 HTTP 状态与统一响应 JSON。
 * 系统级异常（未预期错误）禁止使用本类，交给兜底处理器返回 {@code RPM-COMMON-500}。</p>
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 以错误码默认文案构造业务异常。
     *
     * @param errorCode 业务错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 以自定义消息构造业务异常（覆盖默认文案，面向前端可读）。
     *
     * @param errorCode 业务错误码
     * @param message   自定义消息
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造业务异常的便捷工厂方法。
     *
     * @param errorCode 业务错误码
     * @return 业务异常实例
     */
    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
