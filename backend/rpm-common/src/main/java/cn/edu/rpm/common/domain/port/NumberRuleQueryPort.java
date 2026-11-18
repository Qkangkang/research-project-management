package cn.edu.rpm.common.domain.port;

import cn.edu.rpm.common.domain.model.NumberRule;

import java.util.Optional;

/**
 * 编号规则查询端口（T-APP-009）。
 *
 * <p>由基础设施适配器读取 {@code number_rule} 配置表，使编号生成领域服务
 * 不依赖 MyBatis / JDBC。只返回 {@code status = ACTIVE} 的规则。</p>
 */
public interface NumberRuleQueryPort {

    /**
     * 按规则编码查询启用中的编号规则。
     *
     * @param ruleCode 规则编码（如 {@code APPLICATION_NO}）
     * @return 命中的规则；未配置或已停用返回空 {@link Optional}
     */
    Optional<NumberRule> findActiveByRuleCode(String ruleCode);
}
