package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationReviewEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplicationReviewMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code application_review} 申报审查意见仓储实现（T-APP-006）。
 *
 * <p>基于 MyBatis-Plus {@link ApplicationReviewMapper} 的薄委托层；审查意见
 * 只追加，不提供更新入口。</p>
 */
@Repository
public class ApplicationReviewRepositoryImpl implements ApplicationReviewRepository {

    private final ApplicationReviewMapper applicationReviewMapper;

    public ApplicationReviewRepositoryImpl(ApplicationReviewMapper applicationReviewMapper) {
        this.applicationReviewMapper = applicationReviewMapper;
    }

    @Override
    public void insert(ApplicationReviewEntity review) {
        applicationReviewMapper.insert(review);
    }

    @Override
    public List<ApplicationReviewEntity> findByVersionIdAndStage(String applicationVersionId, String reviewStage) {
        return applicationReviewMapper.selectList(
                new LambdaQueryWrapper<ApplicationReviewEntity>()
                        .eq(ApplicationReviewEntity::getApplicationVersionId, applicationVersionId)
                        .eq(ApplicationReviewEntity::getReviewStage, reviewStage)
                        .orderByAsc(ApplicationReviewEntity::getReviewedAt));
    }
}
