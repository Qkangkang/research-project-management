package cn.edu.rpm.common.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UuidUtils} 纯单元测试。
 *
 * <p>锚点：数据库 uuid 列经 MyBatis 读取为连字符 {@code String}（32 位十六进制），
 * 而领域契约使用 {@link UUID}；解析必须同时兼容 32 位无连字符与标准 8-4-4-4-12 两种文本形态，
 * 非法输入必须快速失败，禁止返回 {@code null} 掩盖脏数据。</p>
 */
class UuidUtilsTest {

    private static final String COMPACT = "0123456789abcdef0123456789abcdef";
    private static final String CANONICAL = "01234567-89ab-cdef-0123-456789abcdef";

    @Test
    void parsesCompactHexWithoutHyphens() {
        assertEquals(UUID.fromString(CANONICAL), UuidUtils.parse(COMPACT));
    }

    @Test
    void parsesCanonicalTextAsIs() {
        assertEquals(UUID.fromString(CANONICAL), UuidUtils.parse(CANONICAL));
    }

    @Test
    void formatsCanonicalText() {
        assertEquals(CANONICAL, UuidUtils.format(UUID.fromString(CANONICAL)));
    }

    @Test
    void formatsCompactTextWithoutHyphens() {
        assertEquals(COMPACT, UuidUtils.formatCompact(UUID.fromString(CANONICAL)));
    }

    @Test
    void sameIdAcceptsBothTextForms() {
        assertTrue(UuidUtils.sameId(COMPACT, CANONICAL));
        assertTrue(UuidUtils.sameId(CANONICAL, COMPACT));
        assertTrue(UuidUtils.sameId(COMPACT, UUID.fromString(CANONICAL)));
        assertFalse(UuidUtils.sameId(COMPACT, (String) null));
        assertFalse(UuidUtils.sameId(null, UUID.fromString(CANONICAL)));
        assertFalse(UuidUtils.sameId("not-a-uuid", CANONICAL));
    }

    @Test
    void rejectsNullBlankAndMalformedText() {
        assertThrows(IllegalArgumentException.class, () -> UuidUtils.parse(null));
        assertThrows(IllegalArgumentException.class, () -> UuidUtils.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> UuidUtils.parse("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> UuidUtils.parse("01234567-89ab-cdef-0123"));
        assertThrows(IllegalArgumentException.class, () -> UuidUtils.parse("0123456789abcdef0123456789abcdeg"));
    }
}
