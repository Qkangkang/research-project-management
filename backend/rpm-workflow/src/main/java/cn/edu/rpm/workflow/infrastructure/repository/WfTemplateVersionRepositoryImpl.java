package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateVersionEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfTemplateVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code wf_template_version} 模板版本仓储实现（T-WF-003）。
 *
 * <p>生效版本查询使用 {@code (effective_to IS NULL OR effective_to > at)}
 * 半开区间语义，并按 {@code effective_from} 倒序取最新一条，避免历史脏数据
 * 导致 {@code selectOne} 抛多结果异常。</p>
 */
@Repository
public class WfTemplateVersionRepositoryImpl implements WfTemplateVersionRepository {

    private final WfTemplateVersionMapper wfTemplateVersionMapper;

    public WfTemplateVersionRepositoryImpl(WfTemplateVersionMapper wfTemplateVersionMapper) {
        this.wfTemplateVersionMapper = wfTemplateVersionMapper;
    }

    @Override
    public void insert(WfTemplateVersionEntity version) {
        wfTemplateVersionMapper.insert(version);
    }

    @Override
    public Optional<WfTemplateVersionEntity> findByTemplateIdAndVersionNo(String templateId, int versionNo) {
        return Optional.ofNullable(wfTemplateVersionMapper.selectOne(
                new LambdaQueryWrapper<WfTemplateVersionEntity>()
                        .eq(WfTemplateVersionEntity::getTemplateId, templateId)
                        .eq(WfTemplateVersionEntity::getVersionNo, versionNo)));
    }

    @Override
    public Optional<WfTemplateVersionEntity> findEffectiveByTemplateId(String templateId, OffsetDateTime at) {
        List<WfTemplateVersionEntity> versions = wfTemplateVersionMapper.selectList(
                new LambdaQueryWrapper<WfTemplateVersionEntity>()
                        .eq(WfTemplateVersionEntity::getTemplateId, templateId)
                        .eq(WfTemplateVersionEntity::getStatus, WfTemplateVersionEntity.STATUS_PUBLISHED)
                        .le(WfTemplateVersionEntity::getEffectiveFrom, at)
                        .and(wrapper -> wrapper
                                .isNull(WfTemplateVersionEntity::getEffectiveTo)
                                .or()
                                .gt(WfTemplateVersionEntity::getEffectiveTo, at))
                        .orderByDesc(WfTemplateVersionEntity::getEffectiveFrom));
        return versions.stream().findFirst();
    }

    @Override
    public Optional<WfTemplateVersionEntity> findLatestByTemplateId(String templateId) {
        List<WfTemplateVersionEntity> versions = wfTemplateVersionMapper.selectList(
                new LambdaQueryWrapper<WfTemplateVersionEntity>()
                        .eq(WfTemplateVersionEntity::getTemplateId, templateId)
                        .orderByDesc(WfTemplateVersionEntity::getVersionNo)
                        .last("LIMIT 1"));
        return versions.stream().findFirst();
    }

    @Override
    public List<WfTemplateVersionEntity> findPublishedByTemplateId(String templateId) {
        return wfTemplateVersionMapper.selectList(
                new LambdaQueryWrapper<WfTemplateVersionEntity>()
                        .eq(WfTemplateVersionEntity::getTemplateId, templateId)
                        .eq(WfTemplateVersionEntity::getStatus, WfTemplateVersionEntity.STATUS_PUBLISHED)
                        .orderByAsc(WfTemplateVersionEntity::getVersionNo));
    }

    @Override
    public int update(WfTemplateVersionEntity version) {
        return wfTemplateVersionMapper.updateById(version);
    }
}
