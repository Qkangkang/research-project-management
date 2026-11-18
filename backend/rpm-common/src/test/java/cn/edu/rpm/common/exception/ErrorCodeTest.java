package cn.edu.rpm.common.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ErrorCode} 枚举与《04-核心业务接口设计.md》§1.3 错误码表的一致性测试。
 * 错误码、HTTP 状态与文档表格逐条对齐，禁止臆造或遗漏。
 */
class ErrorCodeTest {

    @Test
    void shouldContainExactlyDocumentedErrorCodes() {
        // 文档 §1.3 共定义 68 条错误码：COMMON 8 + APPLY 8 + WF 6 + PROJ 6 + EXEC 6
        // + FUND 9 + OUT 4 + ACC 8 + INT 4 + RPT 2 + FILE 2 + SYS 3 + PORT 2
        assertEquals(68, ErrorCode.values().length);
    }

    @Test
    void shouldKeepCodeStringsUnique() {
        Set<String> codes = new HashSet<>();
        Arrays.stream(ErrorCode.values()).forEach(e -> codes.add(e.getCode()));
        assertEquals(ErrorCode.values().length, codes.size(), "错误码字符串必须唯一");
    }

    @Test
    void shouldLookupByCodeString() {
        assertEquals(ErrorCode.COMMON_404, ErrorCode.fromCode("RPM-COMMON-404"));
        assertEquals(ErrorCode.FUND_001, ErrorCode.fromCode("RPM-FUND-001"));
        assertNull(ErrorCode.fromCode("RPM-NOT-EXIST"));
    }

    @Test
    void shouldMapHttpStatusExactlyAsDocumented() {
        // 抽取文档中每种 HTTP 状态的代表性条目断言
        assertEquals(400, ErrorCode.COMMON_400.getHttpStatus());
        assertEquals(401, ErrorCode.COMMON_401.getHttpStatus());
        assertEquals(403, ErrorCode.COMMON_403.getHttpStatus());
        assertEquals(404, ErrorCode.COMMON_404.getHttpStatus());
        assertEquals(409, ErrorCode.COMMON_409.getHttpStatus());
        assertEquals(422, ErrorCode.COMMON_422.getHttpStatus());
        assertEquals(429, ErrorCode.COMMON_429.getHttpStatus());
        assertEquals(500, ErrorCode.COMMON_500.getHttpStatus());

        assertEquals(422, ErrorCode.APPLY_001.getHttpStatus());
        assertEquals(409, ErrorCode.APPLY_002.getHttpStatus());
        assertEquals(409, ErrorCode.WF_004.getHttpStatus());
        assertEquals(404, ErrorCode.PROJ_001.getHttpStatus());
        assertEquals(409, ErrorCode.EXEC_001.getHttpStatus());
        assertEquals(422, ErrorCode.FUND_001.getHttpStatus());
        assertEquals(409, ErrorCode.FUND_007.getHttpStatus());
        assertEquals(200, ErrorCode.OUT_001.getHttpStatus());
        assertEquals(409, ErrorCode.ACC_007.getHttpStatus());
        assertEquals(503, ErrorCode.INT_001.getHttpStatus());
        assertEquals(401, ErrorCode.INT_002.getHttpStatus());
        assertEquals(410, ErrorCode.RPT_002.getHttpStatus());
        assertEquals(410, ErrorCode.PORT_001.getHttpStatus());
        assertEquals(403, ErrorCode.PORT_002.getHttpStatus());
        assertEquals(409, ErrorCode.SYS_003.getHttpStatus());
    }

    @Test
    void shouldCarryDocumentedDefaultMessage() {
        assertTrue(ErrorCode.FUND_001.getDefaultMessage().contains("可用余额不足"));
        assertTrue(ErrorCode.WF_003.getDefaultMessage().contains("禁止审批本人事项"));
        assertTrue(ErrorCode.COMMON_404.getDefaultMessage().contains("资源不存在"));
        assertNotNull(ErrorCode.OUT_001.getDefaultMessage());
    }
}
