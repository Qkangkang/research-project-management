package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.mapper.ResearchOutputMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code research_output} 成果主档仓储实现（T-OUT-001）。
 *
 * <p>基于 MyBatis-Plus {@link ResearchOutputMapper} 的薄委托层；条件查询以
 * {@link LambdaQueryWrapper} 按实体注解生成，全部参数化（无 SQL 拼接）。</p>
 */
@Repository
public class ResearchOutputRepositoryImpl implements ResearchOutputRepository {

    private final ResearchOutputMapper researchOutputMapper;

    public ResearchOutputRepositoryImpl(ResearchOutputMapper researchOutputMapper) {
        this.researchOutputMapper = researchOutputMapper;
    }

    @Override
    public void insert(ResearchOutputEntity output) {
        researchOutputMapper.insert(output);
    }

    @Override
    public Optional<ResearchOutputEntity> findById(String id) {
        return Optional.ofNullable(researchOutputMapper.selectById(id));
    }

    @Override
    public List<ResearchOutputEntity> findByDoi(String doi) {
        if (doi == null || doi.isBlank()) {
            return List.of();
        }
        return researchOutputMapper.selectList(
                new LambdaQueryWrapper<ResearchOutputEntity>()
                        .eq(ResearchOutputEntity::getDoi, doi.trim())
                        .orderByAsc(ResearchOutputEntity::getCreatedAt));
    }

    @Override
    public List<ResearchOutputEntity> findByPatentNo(String patentNo) {
        if (patentNo == null || patentNo.isBlank()) {
            return List.of();
        }
        return researchOutputMapper.selectList(
                new LambdaQueryWrapper<ResearchOutputEntity>()
                        .eq(ResearchOutputEntity::getPatentNo, patentNo.trim())
                        .orderByAsc(ResearchOutputEntity::getCreatedAt));
    }

    @Override
    public List<ResearchOutputEntity> findByTitle(String title) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        return researchOutputMapper.selectList(
                new LambdaQueryWrapper<ResearchOutputEntity>()
                        .eq(ResearchOutputEntity::getTitle, title.trim())
                        .orderByAsc(ResearchOutputEntity::getCreatedAt));
    }

    @Override
    public List<ResearchOutputEntity> findByOwnerIdAndStatus(String ownerId, String status) {
        return researchOutputMapper.selectList(
                new LambdaQueryWrapper<ResearchOutputEntity>()
                        .eq(ResearchOutputEntity::getOwnerId, ownerId)
                        .eq(ResearchOutputEntity::getStatus, status)
                        .orderByDesc(ResearchOutputEntity::getUpdatedAt));
    }

    @Override
    public List<ResearchOutputEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return researchOutputMapper.selectBatchIds(ids);
    }

    @Override
    public int update(ResearchOutputEntity output) {
        return researchOutputMapper.updateById(output);
    }
}
