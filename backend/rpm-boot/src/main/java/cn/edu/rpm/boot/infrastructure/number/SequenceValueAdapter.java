package cn.edu.rpm.boot.infrastructure.number;

import cn.edu.rpm.common.domain.port.SequenceValuePort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

/**
 * PostgreSQL 序列取值适配器（T-APP-009）。
 *
 * <p>使用 {@code SELECT nextval(?::regclass)} 参数化获取序列值，不拼接 SQL；
 * 序列名白名单校验在 {@link cn.edu.rpm.common.domain.service.NumberGenerator}
 * 中完成。查询超时 5 秒（RULE-DB4）。</p>
 */
@Repository
public class SequenceValueAdapter implements SequenceValuePort {

    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private static final String NEXT_VALUE = "SELECT nextval(?::regclass)";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造适配器。
     *
     * @param dataSource 数据源（由 rpm-boot 装配）
     */
    public SequenceValueAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    @Override
    public long nextValue(String sequenceName) {
        try {
            Long value = jdbcTemplate.queryForObject(NEXT_VALUE, Long.class, sequenceName);
            if (value == null) {
                throw new BizException(ErrorCode.SYS_002, "编号序列无返回值: " + sequenceName);
            }
            return value;
        } catch (DataAccessException ex) {
            throw new BizException(ErrorCode.SYS_002, "编号序列不可用: " + sequenceName);
        }
    }
}
