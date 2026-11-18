package cn.edu.rpm.common.api;

import cn.edu.rpm.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Result} 统一响应与 {@link PageVO} 分页响应的形状测试。
 * 期望形状锚点：《04-核心业务接口设计.md》§1.2。
 */
class ResultTest {

    @AfterEach
    void clearMdc() {
        MDC.remove(Result.MDC_TRACE_KEY);
    }

    @Test
    void okShouldCarryOkCodeAndSuccessMessage() {
        Result<Void> result = Result.ok();

        assertEquals("OK", result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void okShouldCarryDataAndTraceIdFromMdc() {
        MDC.put(Result.MDC_TRACE_KEY, "trace-abc-001");

        Result<String> result = Result.ok("payload");

        assertEquals("OK", result.getCode());
        assertEquals("payload", result.getData());
        assertEquals("trace-abc-001", result.getTraceId());
    }

    @Test
    void okWithoutMdcShouldLeaveTraceIdNull() {
        Result<Void> result = Result.ok();
        assertNull(result.getTraceId());
    }

    @Test
    void errorShouldCarryErrorCodeDefaultMessageAndHttpStatusTimestamp() {
        MDC.put(Result.MDC_TRACE_KEY, "trace-err-002");

        Result<Void> result = Result.error(ErrorCode.FUND_001);

        assertEquals("RPM-FUND-001", result.getCode());
        assertEquals("可用余额不足", result.getMessage());
        assertEquals("trace-err-002", result.getTraceId());
        assertNotNull(result.getTimestamp());
        assertTrue(result.getTimestamp() instanceof OffsetDateTime);
    }

    @Test
    void errorShouldAllowCustomMessageOverride() {
        Result<Void> result = Result.error(ErrorCode.FUND_001, "科目 301 可用余额不足");

        assertEquals("RPM-FUND-001", result.getCode());
        assertEquals("科目 301 可用余额不足", result.getMessage());
    }

    @Test
    void pageVoShouldKeepDocumentedPaginationShape() {
        PageVO<String> page = PageVO.of(List.of("a", "b"), 1, 20, 101);

        assertEquals(List.of("a", "b"), page.getItems());
        assertEquals(1, page.getPage());
        assertEquals(20, page.getSize());
        assertEquals(101, page.getTotal());
    }

    @Test
    void pageVoShouldReplaceNullItemsWithEmptyList() {
        PageVO<String> page = PageVO.of(null, 2, 20, 0);

        assertNotNull(page.getItems());
        assertTrue(page.getItems().isEmpty());
        assertEquals(2, page.getPage());
        assertEquals(0, page.getTotal());
    }
}
