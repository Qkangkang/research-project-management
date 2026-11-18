package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ChangeRequestVersionEntity;
import cn.edu.rpm.project.infrastructure.mapper.ChangeRequestVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code change_request_version} 变更版本仓储实现（T-PRJ-008）。
 */
@Repository
public class ChangeRequestVersionRepositoryImpl implements ChangeRequestVersionRepository {

    private final ChangeRequestVersionMapper changeRequestVersionMapper;

    public ChangeRequestVersionRepositoryImpl(ChangeRequestVersionMapper changeRequestVersionMapper) {
        this.changeRequestVersionMapper = changeRequestVersionMapper;
    }

    @Override
    public void insert(ChangeRequestVersionEntity version) {
        changeRequestVersionMapper.insert(version);
    }

    @Override
    public Optional<ChangeRequestVersionEntity> findById(String id) {
        return Optional.ofNullable(changeRequestVersionMapper.selectById(id));
    }

    @Override
    public Optional<ChangeRequestVersionEntity> findByChangeRequestIdAndVersionNo(
            String changeRequestId, int versionNo) {
        return Optional.ofNullable(changeRequestVersionMapper.selectOne(
                new LambdaQueryWrapper<ChangeRequestVersionEntity>()
                        .eq(ChangeRequestVersionEntity::getChangeRequestId, changeRequestId)
                        .eq(ChangeRequestVersionEntity::getVersionNo, versionNo)));
    }

    @Override
    public List<ChangeRequestVersionEntity> findByChangeRequestId(String changeRequestId) {
        return changeRequestVersionMapper.selectList(
                new LambdaQueryWrapper<ChangeRequestVersionEntity>()
                        .eq(ChangeRequestVersionEntity::getChangeRequestId, changeRequestId)
                        .orderByDesc(ChangeRequestVersionEntity::getVersionNo));
    }

    @Override
    public int findMaxVersionNo(String changeRequestId) {
        return changeRequestVersionMapper.selectList(
                        new LambdaQueryWrapper<ChangeRequestVersionEntity>()
                                .select(ChangeRequestVersionEntity::getVersionNo)
                                .eq(ChangeRequestVersionEntity::getChangeRequestId, changeRequestId))
                .stream()
                .map(ChangeRequestVersionEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
    }

    @Override
    public int update(ChangeRequestVersionEntity version) {
        return changeRequestVersionMapper.updateById(version);
    }
}
