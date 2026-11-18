package cn.edu.rpm.iam.application.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AuthContext} 纯单元测试（T-IAM-007 先行测试，技术文档 03 §2.1）。
 *
 * <p>验收锚点：record 可构造；权限集合与代理人集合防御性复制为不可变；
 * 功能权限判定只基于快照集合；关键字段缺失快速失败。</p>
 */
class AuthContextTest {

    private static final UUID USER_ID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final UUID ROLE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant REQUEST_TIME = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void normalizesNullCollectionsToEmptyImmutableSets() {
        AuthContext ctx = new AuthContext(USER_ID, ROLE_ID, "E1001", null, null, REQUEST_TIME);

        assertThat(ctx.permissionCodes()).isEmpty();
        assertThat(ctx.delegatedFromUserIds()).isEmpty();
        assertThat(ctx.hasPermission("project:view")).isFalse();
        assertThat(ctx.employeeNo()).isEqualTo("E1001");
        assertThat(ctx.requestTime()).isEqualTo(REQUEST_TIME);
    }

    @Test
    void copiesPermissionCodesAndAnswersHasPermission() {
        Set<String> source = new HashSet<>(Set.of("project:view", "fund:expense:submit"));

        AuthContext ctx = new AuthContext(USER_ID, ROLE_ID, "E1001", source, Set.of(), REQUEST_TIME);
        source.add("project:edit");

        assertThat(ctx.permissionCodes()).containsExactlyInAnyOrder("project:view", "fund:expense:submit");
        assertThat(ctx.hasPermission("project:view")).isTrue();
        assertThat(ctx.hasPermission("project:edit")).isFalse();
    }

    @Test
    void keepsDelegatedFromUserIds() {
        UUID delegator = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        AuthContext ctx = new AuthContext(USER_ID, ROLE_ID, "E1001", Set.of(), Set.of(delegator), REQUEST_TIME);

        assertThat(ctx.delegatedFromUserIds()).containsExactly(delegator);
    }

    @Test
    void rejectsMissingUserIdOrRequestTime() {
        assertThatThrownBy(() -> new AuthContext(null, ROLE_ID, "E1001", Set.of(), Set.of(), REQUEST_TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuthContext(USER_ID, ROLE_ID, "E1001", Set.of(), Set.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
