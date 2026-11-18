package cn.edu.rpm.common.api;

import cn.edu.rpm.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

/**
 * 统一响应包装。
 *
 * <p>响应 JSON 形状（锚点：《04-核心业务接口设计.md》§1.2）：</p>
 * <pre>{@code
 * { "code": "OK", "message": "success", "data": {}, "trace_id": "…", "timestamp": "2026-11-17T10:00:00+08:00" }
 * }</pre>
 *
 * <p>{@code code} 取值为 {@code OK} 或 {@link ErrorCode} 中的错误码字符串；
 * {@code trace_id} 来自 MDC（接入层 TraceIdFilter 写入），缺失时为 {@code null}；
 * {@code timestamp} 为带时区的 ISO 8601 时间点。</p>
 *
 * @param <T> 业务数据类型
 */
public class Result<T> {

    /** 成功响应码。 */
    public static final String CODE_OK = "OK";

    /** 成功响应默认消息。 */
    public static final String MESSAGE_OK = "success";

    /** TraceID 在 SLF4J MDC 中的 key；接入层过滤器（后续批次）负责写入与清理。 */
    public static final String MDC_TRACE_KEY = "traceId";

    private final String code;

    private final String message;

    private final T data;

    @JsonProperty("trace_id")
    private final String traceId;

    private final OffsetDateTime timestamp;

    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get(MDC_TRACE_KEY);
        this.timestamp = OffsetDateTime.now();
    }

    /**
     * 构造无数据载体的成功响应。
     *
     * @return code=OK 的统一响应
     */
    public static Result<Void> ok() {
        return new Result<>(CODE_OK, MESSAGE_OK, null);
    }

    /**
     * 构造携带业务数据的成功响应。
     *
     * @param data 业务数据（可为分页 {@link PageVO}）
     * @param <T>  业务数据类型
     * @return 统一响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(CODE_OK, MESSAGE_OK, data);
    }

    /**
     * 构造错误响应，消息取错误码默认文案。
     *
     * @param errorCode 业务错误码（含 HTTP 状态映射）
     * @param <T>       业务数据类型
     * @return 统一错误响应
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getDefaultMessage(), null);
    }

    /**
     * 构造错误响应并使用自定义消息覆盖默认文案。
     *
     * @param errorCode 业务错误码
     * @param message   面向前端的可读消息（不得包含堆栈等内部信息）
     * @param <T>       业务数据类型
     * @return 统一错误响应
     */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}
