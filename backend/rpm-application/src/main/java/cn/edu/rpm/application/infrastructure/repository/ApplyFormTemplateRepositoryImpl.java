package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplyFormTemplateEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplyFormTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code apply_form_template} 表单模板仓储实现（T-APP-002）。
 *
 * <p>基于 MyBatis-Plus {@link ApplyFormTemplateMapper} 的薄委托层；生效模板查询
 * 与 {@code WfTemplateVersionRepository.findEffectiveByTemplateId} 的口径一致：
 * 状态已发布 + 时点落在 {@code [effective_from, effective_to)} 区间，
 * 区间任一端为空表示该端不限。</p>
 */
@Repository
public class ApplyFormTemplateRepositoryImpl implements ApplyFormTemplateRepository {

    private final ApplyFormTemplateMapper applyFormTemplateMapper;

    public ApplyFormTemplateRepositoryImpl(ApplyFormTemplateMapper applyFormTemplateMapper) {
        this.applyFormTemplateMapper = applyFormTemplateMapper;
    }

    @Override
    public void insert(ApplyFormTemplateEntity template) {
        applyFormTemplateMapper.insert(template);
    }

    @Override
    public Optional<ApplyFormTemplateEntity> findById(String id) {
        return Optional.ofNullable(applyFormTemplateMapper.selectById(id));
    }

    @Override
    public Optional<ApplyFormTemplateEntity> findByProjectTypeAndVersionNo(String projectType, int versionNo) {
        return Optional.ofNullable(applyFormTemplateMapper.selectOne(
                new LambdaQueryWrapper<ApplyFormTemplateEntity>()
                        .eq(ApplyFormTemplateEntity::getProjectType, projectType)
                        .eq(ApplyFormTemplateEntity::getVersionNo, versionNo)));
    }

    @Override
    public Optional<ApplyFormTemplateEntity> findEffectiveByProjectType(String projectType) {
        OffsetDateTime now = OffsetDateTime.now();
        List<ApplyFormTemplateEntity> templates = applyFormTemplateMapper.selectList(
                new LambdaQueryWrapper<ApplyFormTemplateEntity>()
                        .eq(ApplyFormTemplateEntity::getProjectType, projectType)
                        .eq(ApplyFormTemplateEntity::getStatus, ApplyFormTemplateEntity.STATUS_PUBLISHED)
                        .and(w -> w.isNull(ApplyFormTemplateEntity::getEffectiveFrom)
                                .or().le(ApplyFormTemplateEntity::getEffectiveFrom, now))
                        .and(w -> w.isNull(ApplyFormTemplateEntity::getEffectiveTo)
                                .or().gt(ApplyFormTemplateEntity::getEffectiveTo, now))
                        .orderByDesc(ApplyFormTemplateEntity::getVersionNo));
        return templates.stream().findFirst();
    }

    @Override
    public List<ApplyFormTemplateEntity> findAllEffective() {
        OffsetDateTime now = OffsetDateTime.now();
        List<ApplyFormTemplateEntity> templates = applyFormTemplateMapper.selectList(
                new LambdaQueryWrapper<ApplyFormTemplateEntity>()
                        .eq(ApplyFormTemplateEntity::getStatus,
                                ApplyFormTemplateEntity.STATUS_PUBLISHED)
                        .and(w -> w.isNull(ApplyFormTemplateEntity::getEffectiveFrom)
                                .or().le(ApplyFormTemplateEntity::getEffectiveFrom, now))
                        .and(w -> w.isNull(ApplyFormTemplateEntity::getEffectiveTo)
                                .or().gt(ApplyFormTemplateEntity::getEffectiveTo, now))
                        .orderByDesc(ApplyFormTemplateEntity::getVersionNo));
        Map<String, ApplyFormTemplateEntity> latestByType = new LinkedHashMap<>();
        for (ApplyFormTemplateEntity template : templates) {
            latestByType.putIfAbsent(template.getProjectType(), template);
        }
        return List.copyOf(latestByType.values());
    }

    @Override
    public int update(ApplyFormTemplateEntity template) {
        return applyFormTemplateMapper.updateById(template);
    }
}
