package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BudgetControlPolicyRegistry} 纯单元测试（T-FUND-012）。
 *
 * <p>覆盖：按 {@code BLOCK/WARN/APPROVAL} 返回正确实现；未知级别抛
 * {@link IllegalArgumentException}；同一级别重复注册被拒绝。</p>
 */
class BudgetControlPolicyRegistryTest {

    private final BlockBudgetControlPolicy block = new BlockBudgetControlPolicy();

    private final WarnBudgetControlPolicy warn = new WarnBudgetControlPolicy();

    private final ApprovalBudgetControlPolicy approval = new ApprovalBudgetControlPolicy();

    private final BudgetControlPolicyRegistry registry =
            new BudgetControlPolicyRegistry(List.of(block, warn, approval));

    @Test
    void dispatchesByControlLevel() {
        assertThat(registry.policy(BudgetVersionEntity.CONTROL_BLOCK)).isSameAs(block);
        assertThat(registry.policy(BudgetVersionEntity.CONTROL_WARN)).isSameAs(warn);
        assertThat(registry.policy(BudgetVersionEntity.CONTROL_APPROVAL)).isSameAs(approval);
    }

    @Test
    void unknownControlLevelThrowsIllegalArgument() {
        assertThatThrownBy(() -> registry.policy("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知预算控制级别");
        assertThatThrownBy(() -> registry.policy(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("控制级别不能为空");
    }

    @Test
    void duplicateControlLevelRegistrationRejected() {
        assertThatThrownBy(() -> new BudgetControlPolicyRegistry(List.of(block, block)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复注册");
    }
}
