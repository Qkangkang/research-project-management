package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code wf_template} 审批模板仓储实现（T-WF-002）。
 *
 * <p>基于 MyBatis-Plus {@link WfTemplateMapper} 的薄委托层，无手写 SQL。</p>
 */
@Repository
public class WfTemplateRepositoryImpl implements WfTemplateRepository {

    private final WfTemplateMapper wfTemplateMapper;

    public WfTemplateRepositoryImpl(WfTemplateMapper wfTemplateMapper) {
        this.wfTemplateMapper = wfTemplateMapper;
    }

    @Override
    public void insert(WfTemplateEntity template) {
        wfTemplateMapper.insert(template);
    }

    @Override
    public Optional<WfTemplateEntity> findById(String id) {
        return Optional.ofNullable(wfTemplateMapper.selectById(id));
    }

    @Override
    public Optional<WfTemplateEntity> findByCode(String code) {
        return Optional.ofNullable(wfTemplateMapper.selectOne(
                new LambdaQueryWrapper<WfTemplateEntity>()
                        .eq(WfTemplateEntity::getCode, code)));
    }

    @Override
    public List<WfTemplateEntity> findByProjectType(String projectType) {
        return wfTemplateMapper.selectList(new LambdaQueryWrapper<WfTemplateEntity>()
                .eq(WfTemplateEntity::getProjectType, projectType));
    }

    @Override
    public List<WfTemplateEntity> findAll() {
        return wfTemplateMapper.selectList(null);
    }

    @Override
    public int update(WfTemplateEntity template) {
        return wfTemplateMapper.updateById(template);
    }
}
