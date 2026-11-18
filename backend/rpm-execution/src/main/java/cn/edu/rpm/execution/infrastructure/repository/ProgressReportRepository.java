package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code progress_report} 周期报告仓储（T-EXEC-001）。
 *
 * <p>插入撞 {@code uk_progress_report_period} 时抛出 Spring
 * {@link org.springframework.dao.DuplicateKeyException}（TC-EXEC-007）。</p>
 */
public interface ProgressReportRepository {

    /**
     * 插入报告主档。
     *
     * @param report 待插入的报告 PO
     */
    void insert(ProgressReportEntity report);

    /**
     * 按主键查询报告。
     *
     * @param id 报告 ID
     * @return 命中的报告；不存在返回空
     */
    Optional<ProgressReportEntity> findById(String id);

    /**
     * 按主键加行级排他锁查询（提交/审核入口）。
     *
     * @param id 报告 ID
     * @return 加锁命中的报告；不存在返回空
     */
    Optional<ProgressReportEntity> selectForUpdate(String id);

    /**
     * 按项目查询全部报告（周期键降序）。
     *
     * @param projectId 项目 ID
     * @return 报告列表；无命中返回空列表
     */
    List<ProgressReportEntity> findByProjectId(String projectId);

    /**
     * 按项目 + 周期类型 + 周期键查询唯一报告（uk_progress_report_period）。
     *
     * @param projectId  项目 ID
     * @param periodType 周期类型
     * @param periodKey  周期键
     * @return 命中的报告；不存在返回空
     */
    Optional<ProgressReportEntity> findByProjectIdAndPeriod(String projectId, String periodType,
                                                            String periodKey);

    /**
     * 查询指定截止日期前未完成的报告（催报/逾期扫描）。
     *
     * @param date   截止日期上界（含）
     * @param status 报告状态
     * @return 报告列表；无命中返回空列表
     */
    List<ProgressReportEntity> findDueBy(LocalDate date, String status);

    /**
     * 按乐观锁更新报告。
     *
     * @param report 携带主键与旧版本的报告 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ProgressReportEntity report);
}
