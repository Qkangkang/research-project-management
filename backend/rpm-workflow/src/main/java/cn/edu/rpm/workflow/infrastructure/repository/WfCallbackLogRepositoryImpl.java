package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfCallbackLogEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfCallbackLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code wf_callback_log} 回调幂等仓储实现（T-WF-007）。
 *
 * <p>按 {@code request_id} 查询已处理回调；{@code selectOne} 依赖
 * uk_wf_callback_request 保证结果唯一。</p>
 */
@Repository
public class WfCallbackLogRepositoryImpl implements WfCallbackLogRepository {

    private final WfCallbackLogMapper wfCallbackLogMapper;

    public WfCallbackLogRepositoryImpl(WfCallbackLogMapper wfCallbackLogMapper) {
        this.wfCallbackLogMapper = wfCallbackLogMapper;
    }

    @Override
    public void insert(WfCallbackLogEntity log) {
        wfCallbackLogMapper.insert(log);
    }

    @Override
    public Optional<WfCallbackLogEntity> findByRequestId(String requestId) {
        return Optional.ofNullable(wfCallbackLogMapper.selectOne(
                new LambdaQueryWrapper<WfCallbackLogEntity>()
                        .eq(WfCallbackLogEntity::getRequestId, requestId)));
    }
}
