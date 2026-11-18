package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplicationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code application} 申报主档仓储实现（T-APP-003）。
 *
 * <p>基于 MyBatis-Plus {@link ApplicationMapper} 的薄委托层；条件查询以
 * {@link LambdaQueryWrapper} 按实体注解生成；悲观锁走 Mapper XML 的
 * {@code selectByIdForUpdate}（{@code SELECT ... FOR UPDATE}）。</p>
 */
@Repository
public class ApplicationRepositoryImpl implements ApplicationRepository {

    private final ApplicationMapper applicationMapper;

    public ApplicationRepositoryImpl(ApplicationMapper applicationMapper) {
        this.applicationMapper = applicationMapper;
    }

    @Override
    public void insert(ApplicationEntity application) {
        applicationMapper.insert(application);
    }

    @Override
    public Optional<ApplicationEntity> findById(String id) {
        return Optional.ofNullable(applicationMapper.selectById(id));
    }

    @Override
    public Optional<ApplicationEntity> findByApplicationNo(String applicationNo) {
        return Optional.ofNullable(applicationMapper.selectOne(
                new LambdaQueryWrapper<ApplicationEntity>()
                        .eq(ApplicationEntity::getApplicationNo, applicationNo)));
    }

    @Override
    public List<ApplicationEntity> findByApplicantIdAndStatus(String applicantId, String status) {
        return applicationMapper.selectList(
                new LambdaQueryWrapper<ApplicationEntity>()
                        .eq(ApplicationEntity::getApplicantId, applicantId)
                        .eq(ApplicationEntity::getStatus, status)
                        .orderByDesc(ApplicationEntity::getUpdatedAt));
    }

    @Override
    public ApplicationPageResult search(ApplicationSearchCriteria criteria) {
        int pageNo = Math.max(criteria.page(), 1);
        int pageSize = Math.min(Math.max(criteria.size(), 1), 200);
        LambdaQueryWrapper<ApplicationEntity> wrapper = new LambdaQueryWrapper<>();
        if (hasText(criteria.keyword())) {
            String keyword = criteria.keyword().trim();
            wrapper.and(w -> w.like(ApplicationEntity::getApplicationNo, keyword)
                    .or().like(ApplicationEntity::getTitle, keyword));
        }
        if (hasText(criteria.projectType())) {
            wrapper.eq(ApplicationEntity::getProjectType, criteria.projectType().trim());
        }
        if (hasText(criteria.status())) {
            wrapper.eq(ApplicationEntity::getStatus, criteria.status().trim());
        }
        if (hasText(criteria.batchId())) {
            wrapper.eq(ApplicationEntity::getBatchId, criteria.batchId().trim());
        }
        if (!criteria.scopeAll()) {
            applyDataScope(wrapper, criteria.applicantId(), criteria.deptIds());
        }
        wrapper.orderByDesc(ApplicationEntity::getUpdatedAt);

        Page<ApplicationEntity> page = applicationMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper);
        return new ApplicationPageResult(page.getRecords(), page.getTotal());
    }

    @Override
    public Optional<ApplicationEntity> selectForUpdate(String applicationId) {
        return Optional.ofNullable(applicationMapper.selectByIdForUpdate(applicationId));
    }

    @Override
    public int update(ApplicationEntity application) {
        return applicationMapper.updateById(application);
    }

    private static void applyDataScope(LambdaQueryWrapper<ApplicationEntity> wrapper,
                                       String applicantId, Set<String> deptIds) {
        boolean hasApplicant = hasText(applicantId);
        boolean hasDepts = deptIds != null && !deptIds.isEmpty();
        if (hasApplicant && hasDepts) {
            wrapper.and(w -> w.eq(ApplicationEntity::getApplicantId, applicantId)
                    .or().in(ApplicationEntity::getDeptId, deptIds));
            return;
        }
        if (hasApplicant) {
            wrapper.eq(ApplicationEntity::getApplicantId, applicantId);
            return;
        }
        if (hasDepts) {
            wrapper.in(ApplicationEntity::getDeptId, deptIds);
            return;
        }
        wrapper.apply("1 = 0");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
