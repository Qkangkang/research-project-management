package cn.edu.rpm.common.exception;

import cn.edu.rpm.common.api.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：把异常统一转换为《04-核心业务接口设计.md》§1.2 统一响应。
 *
 * <p>映射规则：</p>
 * <ul>
 *   <li>{@link BizException} → 错误码约定的 HTTP 状态 + 错误码 + 消息；</li>
 *   <li>参数校验失败（{@link MethodArgumentNotValidException}）→ 400 / {@code RPM-COMMON-400}，消息含字段定位；</li>
 *   <li>未映射路径/方法（404、405）→ 404 / {@code RPM-COMMON-404}（文档未定义 405 专用错误码，统一按资源不存在返回）；</li>
 *   <li>其余未预期异常 → 500 / {@code RPM-COMMON-500}，日志记录完整堆栈，响应只透出 TraceID。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常：按错误码文档约定的 HTTP 状态返回统一响应。
     *
     * @param ex 应用服务/领域服务抛出的业务异常
     * @return 统一错误响应（HTTP 状态取自 {@link ErrorCode#getHttpStatus()}）
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        LOG.warn("业务异常 code={} message={} traceId={}",
                errorCode.getCode(), ex.getMessage(), MDC.get(Result.MDC_TRACE_KEY));
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.error(errorCode, ex.getMessage()));
    }

    /**
     * 处理 JSR-303 参数校验失败：返回 400 与逐字段错误定位。
     *
     * @param ex Spring MVC 参数绑定校验异常
     * @return 400 / RPM-COMMON-400，消息形如 {@code field: message; field: message}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationFailure(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        LOG.warn("参数校验失败 detail={} traceId={}", detail, MDC.get(Result.MDC_TRACE_KEY));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.COMMON_400, detail));
    }

    /**
     * 处理未映射资源：文档未定义 405 专用错误码，404/405 统一按资源不存在返回 RPM-COMMON-404。
     *
     * @param ex 未找到资源/处理器或 HTTP 方法不支持异常
     * @return 404 / RPM-COMMON-404
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception ex) {
        LOG.warn("资源不存在 path={} type={} traceId={}",
                ex.getMessage(), ex.getClass().getSimpleName(), MDC.get(Result.MDC_TRACE_KEY));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ErrorCode.COMMON_404));
    }

    /**
     * 兜底处理未预期异常：记录完整堆栈（含 TraceID），响应不泄露内部信息。
     *
     * @param ex 任意未预期异常
     * @return 500 / RPM-COMMON-500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        LOG.error("未预期异常 traceId={}", MDC.get(Result.MDC_TRACE_KEY), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCode.COMMON_500));
    }
}
