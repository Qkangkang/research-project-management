package cn.edu.rpm.workflow.application.registry;

import cn.edu.rpm.workflow.domain.dto.ApprovalResult;
import cn.edu.rpm.workflow.domain.port.ApprovalResultHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ApprovalResultHandlerRegistry} 纯单元测试（T-WF-011 先行测试）。
 *
 * <p>验收锚点：注册 2 个处理器，按 bizType 返回正确处理器；未知类型抛
 * {@link IllegalArgumentException}；重复或空白 bizType 启动即失败。</p>
 */
class ApprovalResultHandlerRegistryTest {

    @Test
    void dispatchesByBizType() {
        ApprovalResultHandler application = new NoopHandler("APPLICATION");
        ApprovalResultHandler expense = new NoopHandler("EXPENSE");

        ApprovalResultHandlerRegistry registry =
                new ApprovalResultHandlerRegistry(List.of(application, expense));

        assertThat(registry.get("APPLICATION")).isSameAs(application);
        assertThat(registry.get("EXPENSE")).isSameAs(expense);
        assertThat(registry.contains("APPLICATION")).isTrue();
        assertThat(registry.contains("UNKNOWN")).isFalse();
    }

    @Test
    void unknownBizTypeThrowsIllegalArgument() {
        ApprovalResultHandlerRegistry registry =
                new ApprovalResultHandlerRegistry(List.of(new NoopHandler("APPLICATION")));

        assertThatThrownBy(() -> registry.get("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void duplicateBizTypeFailsFast() {
        assertThatThrownBy(() -> new ApprovalResultHandlerRegistry(
                List.of(new NoopHandler("APPLICATION"), new NoopHandler("APPLICATION"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void blankBizTypeFailsFast() {
        assertThatThrownBy(() -> new ApprovalResultHandlerRegistry(List.of(new NoopHandler(" "))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyRegistryIsAllowedButGetFails() {
        ApprovalResultHandlerRegistry registry = new ApprovalResultHandlerRegistry(List.of());
        assertThatThrownBy(() -> registry.get("APPLICATION"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 仅记录调用次数的处理器桩。
     */
    private static final class NoopHandler implements ApprovalResultHandler {

        private final String bizType;

        private NoopHandler(String bizType) {
            this.bizType = bizType;
        }

        @Override
        public String bizType() {
            return bizType;
        }

        @Override
        public void onApproved(ApprovalResult result) {
        }

        @Override
        public void onRejected(ApprovalResult result) {
        }

        @Override
        public void onReturned(ApprovalResult result) {
        }

        @Override
        public void onWithdrawn(ApprovalResult result) {
        }
    }
}
