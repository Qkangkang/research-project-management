package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ProgressReportEntity;
import cn.edu.rpm.execution.infrastructure.mapper.ProgressReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code progress_report} 周期报告仓储实现（T-EXEC-001）。
 */
@Repository
public class ProgressReportRepositoryImpl implements ProgressReportRepository {

    private final ProgressReportMapper progressReportMapper;

    public ProgressReportRepositoryImpl(ProgressReportMapper progressReportMapper) {
        this.progressReportMapper = progressReportMapper;
    }

    @Override
    public void insert(ProgressReportEntity report) {
        progressReportMapper.insert(report);
    }

    @Override
    public Optional<ProgressReportEntity> findById(String id) {
        return Optional.ofNullable(progressReportMapper.selectById(id));
    }

    @Override
    public Optional<ProgressReportEntity> selectForUpdate(String id) {
        return Optional.ofNullable(progressReportMapper.selectByIdForUpdate(id));
    }

    @Override
    public List<ProgressReportEntity> findByProjectId(String projectId) {
        return progressReportMapper.selectList(new LambdaQueryWrapper<ProgressReportEntity>()
                .eq(ProgressReportEntity::getProjectId, projectId)
                .orderByDesc(ProgressReportEntity::getPeriodKey));
    }

    @Override
    public Optional<ProgressReportEntity> findByProjectIdAndPeriod(String projectId, String periodType,
                                                                   String periodKey) {
        return Optional.ofNullable(progressReportMapper.selectOne(
                new LambdaQueryWrapper<ProgressReportEntity>()
                        .eq(ProgressReportEntity::getProjectId, projectId)
                        .eq(ProgressReportEntity::getPeriodType, periodType)
                        .eq(ProgressReportEntity::getPeriodKey, periodKey)));
    }

    @Override
    public List<ProgressReportEntity> findDueBy(LocalDate date, String status) {
        return progressReportMapper.selectList(new LambdaQueryWrapper<ProgressReportEntity>()
                .le(ProgressReportEntity::getDueDate, date)
                .eq(ProgressReportEntity::getStatus, status)
                .orderByAsc(ProgressReportEntity::getDueDate));
    }

    @Override
    public int update(ProgressReportEntity report) {
        return progressReportMapper.updateById(report);
    }
}
