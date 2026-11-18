package cn.edu.rpm.boot.infrastructure.number;

import cn.edu.rpm.common.domain.model.NumberResetCycle;
import cn.edu.rpm.common.domain.model.NumberRule;
import cn.edu.rpm.common.domain.port.NumberRuleQueryPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

/**
 * {@code number_rule} 查询适配器（T-APP-009）。
 *
 * <p>以只读 SQL 读取启用规则；查询超时 5 秒（RULE-DB4）。配置值非法时抛
 * {@code RPM-SYS-002}，不把数据库异常透出到接口层。</p>
 */
@Repository
public class NumberRuleQueryAdapter implements NumberRuleQueryPort {

    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private static final String SELECT_ACTIVE_RULE = """
            SELECT rule_code, prefix, date_pattern, sequence_name, padding, reset_cycle
              FROM number_rule
             WHERE rule_code = ?
               AND status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造适配器。
     *
     * @param dataSource 数据源（由 rpm-boot 装配）
     */
    public NumberRuleQueryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    @Override
    public Optional<NumberRule> findActiveByRuleCode(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return Optional.empty();
        }
        try {
            List<NumberRule> rules = jdbcTemplate.query(SELECT_ACTIVE_RULE,
                    (rs, rowNum) -> new NumberRule(
                            rs.getString("rule_code"),
                            rs.getString("prefix"),
                            rs.getString("date_pattern"),
                            rs.getString("sequence_name"),
                            rs.getInt("padding"),
                            NumberResetCycle.from(rs.getString("reset_cycle"))),
                    ruleCode.trim());
            return rules.stream().findFirst();
        } catch (DataAccessException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.SYS_002, "读取编号规则失败: " + ruleCode);
        }
    }
}
