package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ProgressReportVersionEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code progress_report_version} 报告版本仓储（T-EXEC-002）。
 */
public interface ProgressReportVersionRepository {

    /**
     * 插入报告版本。
     *
     * @param version 待插入的版本 PO
     */
    void insert(ProgressReportVersionEntity version);

    /**
     * 按主键查询报告版本。
     *
     * @param id 版本 ID
     * @return 命中的版本；不存在返回空
     */
    Optional<ProgressReportVersionEntity> findById(String id);

    /**
     * 按报告 + 版本号查询（uk_progress_report_version_no）。
     *
     * @param reportId  报告 ID
     * @param versionNo 版本号
     * @return 命中的版本；不存在返回空
     */
    Optional<ProgressReportVersionEntity> findByReportIdAndVersionNo(String reportId, int versionNo);

    /**
     * 按报告查询全部版本（按版本号升序，旧版本保留）。
     *
     * @param reportId 报告 ID
     * @return 版本列表；无命中返回空列表
     */
    List<ProgressReportVersionEntity> findByReportId(String reportId);

    /**
     * 查询报告当前最大版本号（无版本返回 0）。
     *
     * @param reportId 报告 ID
     * @return 最大版本号
     */
    int findMaxVersionNo(String reportId);

    /**
     * 按主键更新版本（仅审核决策字段）。
     *
     * @param version 携带主键的版本 PO
     * @return 受影响行数
     */
    int update(ProgressReportVersionEntity version);
}
