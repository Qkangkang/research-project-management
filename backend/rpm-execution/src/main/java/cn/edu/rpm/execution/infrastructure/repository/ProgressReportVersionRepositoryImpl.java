package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.ProgressReportVersionEntity;
import cn.edu.rpm.execution.infrastructure.mapper.ProgressReportVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code progress_report_version} 报告版本仓储实现（T-EXEC-002）。
 */
@Repository
public class ProgressReportVersionRepositoryImpl implements ProgressReportVersionRepository {

    private final ProgressReportVersionMapper progressReportVersionMapper;

    public ProgressReportVersionRepositoryImpl(ProgressReportVersionMapper progressReportVersionMapper) {
        this.progressReportVersionMapper = progressReportVersionMapper;
    }

    @Override
    public void insert(ProgressReportVersionEntity version) {
        progressReportVersionMapper.insert(version);
    }

    @Override
    public Optional<ProgressReportVersionEntity> findById(String id) {
        return Optional.ofNullable(progressReportVersionMapper.selectById(id));
    }

    @Override
    public Optional<ProgressReportVersionEntity> findByReportIdAndVersionNo(String reportId, int versionNo) {
        return Optional.ofNullable(progressReportVersionMapper.selectOne(
                new LambdaQueryWrapper<ProgressReportVersionEntity>()
                        .eq(ProgressReportVersionEntity::getReportId, reportId)
                        .eq(ProgressReportVersionEntity::getVersionNo, versionNo)));
    }

    @Override
    public List<ProgressReportVersionEntity> findByReportId(String reportId) {
        return progressReportVersionMapper.selectList(
                new LambdaQueryWrapper<ProgressReportVersionEntity>()
                        .eq(ProgressReportVersionEntity::getReportId, reportId)
                        .orderByDesc(ProgressReportVersionEntity::getVersionNo));
    }

    @Override
    public int findMaxVersionNo(String reportId) {
        return progressReportVersionMapper.selectList(
                        new LambdaQueryWrapper<ProgressReportVersionEntity>()
                                .select(ProgressReportVersionEntity::getVersionNo)
                                .eq(ProgressReportVersionEntity::getReportId, reportId))
                .stream()
                .map(ProgressReportVersionEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
    }

    @Override
    public int update(ProgressReportVersionEntity version) {
        return progressReportVersionMapper.updateById(version);
    }
}
