package cn.edu.rpm.organization.infrastructure.repository;

import cn.edu.rpm.organization.infrastructure.entity.OrgUnitEntity;
import cn.edu.rpm.organization.infrastructure.mapper.OrgUnitMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code org_unit} 组织树仓储实现（T-ORG-001）。
 *
 * <p>基于 MyBatis-Plus {@link OrgUnitMapper} 的薄委托层；查询条件以
 * {@link LambdaQueryWrapper} 按实体注解生成，无手写 SQL。</p>
 */
@Repository
public class OrgUnitRepositoryImpl implements OrgUnitRepository {

    private final OrgUnitMapper orgUnitMapper;

    public OrgUnitRepositoryImpl(OrgUnitMapper orgUnitMapper) {
        this.orgUnitMapper = orgUnitMapper;
    }

    @Override
    public void insert(OrgUnitEntity unit) {
        orgUnitMapper.insert(unit);
    }

    @Override
    public Optional<OrgUnitEntity> findById(String id) {
        return Optional.ofNullable(orgUnitMapper.selectById(id));
    }

    @Override
    public List<OrgUnitEntity> findChildrenByParentId(String parentId) {
        return orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnitEntity>()
                .eq(OrgUnitEntity::getParentId, parentId)
                .orderByAsc(OrgUnitEntity::getSortOrder));
    }

    @Override
    public List<OrgUnitEntity> findSubtreeByPath(String path) {
        return orgUnitMapper.selectList(
                new LambdaQueryWrapper<OrgUnitEntity>().likeRight(OrgUnitEntity::getPath, path));
    }

    @Override
    public List<OrgUnitEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return orgUnitMapper.selectBatchIds(ids);
    }

    @Override
    public List<OrgUnitEntity> findAllActive() {
        return orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnitEntity>()
                .eq(OrgUnitEntity::getStatus, OrgUnitEntity.STATUS_ACTIVE)
                .orderByAsc(OrgUnitEntity::getSortOrder)
                .orderByAsc(OrgUnitEntity::getCode));
    }

    @Override
    public List<OrgUnitEntity> findSubtreeByPaths(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<OrgUnitEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(nested -> {
            boolean first = true;
            for (String path : paths) {
                if (first) {
                    nested.likeRight(OrgUnitEntity::getPath, path);
                    first = false;
                } else {
                    nested.or().likeRight(OrgUnitEntity::getPath, path);
                }
            }
        });
        return orgUnitMapper.selectList(wrapper);
    }
}
