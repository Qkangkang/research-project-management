package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.CoiDeclarationEntity;
import cn.edu.rpm.iam.infrastructure.mapper.CoiDeclarationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code coi_declaration} 利益冲突声明仓储实现（T-IAM-006）。
 *
 * <p>基于 MyBatis-Plus {@link CoiDeclarationMapper} 的薄委托层；查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。</p>
 */
@Repository
public class CoiDeclarationRepositoryImpl implements CoiDeclarationRepository {

    private final CoiDeclarationMapper coiDeclarationMapper;

    public CoiDeclarationRepositoryImpl(CoiDeclarationMapper coiDeclarationMapper) {
        this.coiDeclarationMapper = coiDeclarationMapper;
    }

    @Override
    public void insert(CoiDeclarationEntity declaration) {
        coiDeclarationMapper.insert(declaration);
    }

    @Override
    public List<CoiDeclarationEntity> findByBizAndExpert(String bizType, String bizId, String expertId) {
        return coiDeclarationMapper.selectList(new LambdaQueryWrapper<CoiDeclarationEntity>()
                .eq(CoiDeclarationEntity::getBizType, bizType)
                .eq(CoiDeclarationEntity::getBizId, bizId)
                .eq(CoiDeclarationEntity::getExpertId, expertId));
    }

    @Override
    public List<CoiDeclarationEntity> findByExpertId(String expertId) {
        return coiDeclarationMapper.selectList(
                new LambdaQueryWrapper<CoiDeclarationEntity>().eq(CoiDeclarationEntity::getExpertId, expertId));
    }
}
