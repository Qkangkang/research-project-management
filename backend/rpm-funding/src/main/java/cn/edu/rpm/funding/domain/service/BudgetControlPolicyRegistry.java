package cn.edu.rpm.funding.domain.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 预算控制策略注册器（T-FUND-012；技术文档 05 §1.3）。
 *
 * <p>按 {@link BudgetControlPolicy#controlLevel()} 建立索引，未知级别抛
 * {@link IllegalArgumentException}（配置错误快速失败）。重复注册同一级别同样拒绝，
 * 避免装配阶段静默覆盖。</p>
 */
@Component
public class BudgetControlPolicyRegistry {

    private final Map<String, BudgetControlPolicy> policies;

    /**
     * 构造注册器并校验策略完整性。
     *
     * @param policyList Spring 注入的全部策略实现
     */
    public BudgetControlPolicyRegistry(List<BudgetControlPolicy> policyList) {
        Objects.requireNonNull(policyList, "policyList 不能为空");
        Map<String, BudgetControlPolicy> registered = new LinkedHashMap<>();
        for (BudgetControlPolicy policy : policyList) {
            String level = normalize(policy.controlLevel());
            if (registered.putIfAbsent(level, policy) != null) {
                throw new IllegalArgumentException("预算控制级别重复注册: " + level);
            }
        }
        this.policies = Map.copyOf(registered);
    }

    /**
     * 按控制级别取策略。
     *
     * @param controlLevel 控制级别
     * @return 对应策略
     * @throws IllegalArgumentException 级别为空或未注册
     */
    public BudgetControlPolicy policy(String controlLevel) {
        if (controlLevel == null || controlLevel.isBlank()) {
            throw new IllegalArgumentException("控制级别不能为空");
        }
        BudgetControlPolicy policy = policies.get(normalize(controlLevel));
        if (policy == null) {
            throw new IllegalArgumentException("未知预算控制级别: " + controlLevel);
        }
        return policy;
    }

    private static String normalize(String level) {
        return level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
    }
}
