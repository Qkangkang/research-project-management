package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfInstanceEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code wf_instance} 审批实例仓储实现（T-WF-004）。
 *
 * <p>运行中实例查询与部分唯一索引 {@code uk_wf_instance_running} 使用同一谓词；
 * 更新走 {@code updateById} 并携带实体 {@code version}（乐观锁）。</p>
 */
@Repository
public class WfInstanceRepositoryImpl implements WfInstanceRepository {

    private final WfInstanceMapper wfInstanceMapper;

    public WfInstanceRepositoryImpl(WfInstanceMapper wfInstanceMapper) {
        this.wfInstanceMapper = wfInstanceMapper;
    }

    @Override
    public void insert(WfInstanceEntity instance) {
        wfInstanceMapper.insert(instance);
    }

    @Override
    public Optional<WfInstanceEntity> findById(String id) {
        return Optional.ofNullable(wfInstanceMapper.selectById(id));
    }

    @Override
    public Optional<WfInstanceEntity> findRunningByBiz(String bizType, String bizId, String bizVersionId) {
        List<WfInstanceEntity> instances = wfInstanceMapper.selectList(
                new LambdaQueryWrapper<WfInstanceEntity>()
                        .eq(WfInstanceEntity::getBizType, bizType)
                        .eq(WfInstanceEntity::getBizId, bizId)
                        .eq(WfInstanceEntity::getBizVersionId, bizVersionId)
                        .eq(WfInstanceEntity::getStatus, WfInstanceEntity.STATUS_RUNNING));
        return instances.stream().findFirst();
    }

    @Override
    public Optional<WfInstanceEntity> findLatestByBiz(String bizType, String bizId, String bizVersionId) {
        return Optional.ofNullable(wfInstanceMapper.selectLatestByBiz(bizType, bizId, bizVersionId));
    }

    @Override
    public List<WfInstanceEntity> findInitiatedPage(String initiatorId, long offset, int limit) {
        return wfInstanceMapper.selectInitiatedPage(initiatorId, offset, limit);
    }

    @Override
    public long countInitiated(String initiatorId) {
        return wfInstanceMapper.countInitiated(initiatorId);
    }

    @Override
    public int update(WfInstanceEntity instance) {
        return wfInstanceMapper.updateById(instance);
    }
}
