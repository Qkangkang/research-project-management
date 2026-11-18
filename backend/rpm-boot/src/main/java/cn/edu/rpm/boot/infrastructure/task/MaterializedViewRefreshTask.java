package cn.edu.rpm.boot.infrastructure.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 物化视图定时刷新任务（V02 {@code refresh_all_materialized_views}）。
 *
 * <p>按技术方案，{@code mv_project_summary} 等四个物化视图是报表/看板读模型。
 * 函数内部已按依赖顺序刷新、逐视图写 {@code mv_refresh_log}（失败后置 SKIPPED，
 * 保留各视图上次数据），本任务只负责周期触发与结果观测。</p>
 *
 * <p>默认每日 02:30 刷新（低峰期）；CONCURRENTLY 与锁语义由函数内
 * {@code REFRESH MATERIALIZED VIEW}（非并发）承担——低峰窗口内独占刷新可接受，
 * 后续报表模块上线时再评估切换并发刷新。</p>
 */
@Component
public class MaterializedViewRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(MaterializedViewRefreshTask.class);

    /** 单次刷新语句超时（秒）：防止锁等待无限挂起（RULE-DB4）。 */
    private static final int REFRESH_TIMEOUT_SECONDS = 300;

    private final JdbcTemplate jdbcTemplate;

    private final boolean enabled;

    public MaterializedViewRefreshTask(DataSource dataSource,
                                       @Value("${rpm.reporting.mv-refresh-enabled:true}") boolean enabled) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(REFRESH_TIMEOUT_SECONDS);
        this.enabled = enabled;
    }

    /** 每日 02:30 触发全量物化视图刷新（可在配置中关闭，如报表未启用的环境）。 */
    @Scheduled(cron = "${rpm.reporting.mv-refresh-cron:0 30 2 * * *}")
    public void refreshScheduled() {
        if (!enabled) {
            return;
        }
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    "SELECT view_name, status, row_count, error_msg "
                            + "FROM refresh_all_materialized_views('SCHEDULE')");
            log.info("物化视图刷新完成 results={}", results);
        } catch (Exception e) {
            log.error("物化视图刷新任务异常", e);
        }
    }
}
