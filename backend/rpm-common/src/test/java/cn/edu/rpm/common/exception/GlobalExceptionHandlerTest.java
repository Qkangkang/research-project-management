package cn.edu.rpm.common.exception;

import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.api.TestForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GlobalExceptionHandler} 行为测试（纯单元测试，不启动 Spring 容器）。
 * 验收锚点：抛出 BizException 返回统一 JSON；HTTP 404 返回 {@code RPM-COMMON-404}；
 * 参数校验失败返回 {@code RPM-COMMON-400}；未预期异常返回 {@code RPM-COMMON-500}。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void putTraceId() {
        MDC.put(Result.MDC_TRACE_KEY, "tc-trace-001");
    }

    @AfterEach
    void clearTraceId() {
        MDC.remove(Result.MDC_TRACE_KEY);
    }

    @Test
    void bizExceptionShouldMapToDocumentedHttpStatusAndCode() {
        BizException ex = BizException.of(ErrorCode.FUND_001);

        ResponseEntity<Result<Void>> response = handler.handleBizException(ex);

        assertEquals(HttpStatus.valueOf(422), response.getStatusCode());
        Result<Void> body = response.getBody();
        assertNotNull(body);
        assertEquals("RPM-FUND-001", body.getCode());
        assertEquals("可用余额不足", body.getMessage());
        assertEquals("tc-trace-001", body.getTraceId());
        assertNotNull(body.getTimestamp());
    }

    @Test
    void bizExceptionShouldUseExplicitMessageWhenProvided() {
        BizException ex = new BizException(ErrorCode.COMMON_422, "批次已关闭，不可提交");

        ResponseEntity<Result<Void>> response = handler.handleBizException(ex);

        assertEquals(HttpStatus.valueOf(422), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RPM-COMMON-422", response.getBody().getCode());
        assertEquals("批次已关闭，不可提交", response.getBody().getMessage());
    }

    @Test
    void validationFailureShouldReturnRpmCommon400WithFieldDetail() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new TestForm(), "form");
        bindingResult.addError(new FieldError("form", "budgetAmount", "金额格式错误"));
        bindingResult.addError(new FieldError("form", "projectName", "项目名称不能为空"));
        Method method = TestController.class.getMethod("create", TestForm.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Result<Void>> response = handler.handleValidationFailure(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Result<Void> body = response.getBody();
        assertNotNull(body);
        assertEquals("RPM-COMMON-400", body.getCode());
        assertTrue(body.getMessage().contains("budgetAmount"));
        assertTrue(body.getMessage().contains("金额格式错误"));
        assertTrue(body.getMessage().contains("projectName"));
    }

    @Test
    void unmappedPathShouldReturnRpmCommon404() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/v1/unknown");

        ResponseEntity<Result<Void>> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Result<Void> body = response.getBody();
        assertNotNull(body);
        assertEquals("RPM-COMMON-404", body.getCode());
    }

    @Test
    void unexpectedExceptionShouldReturnRpmCommon500() {
        RuntimeException ex = new RuntimeException("数据库连接失败");

        ResponseEntity<Result<Void>> response = handler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Result<Void> body = response.getBody();
        assertNotNull(body);
        assertEquals("RPM-COMMON-500", body.getCode());
        assertNotNull(body.getTimestamp());
    }

    /** 仅用于构造 {@link MethodParameter} 的样例控制器。 */
    static class TestController {
        public Result<Void> create(TestForm form) {
            return Result.ok();
        }
    }
}
