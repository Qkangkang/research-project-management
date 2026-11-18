package cn.edu.rpm.workflow.infrastructure.mapper;

import cn.edu.rpm.workflow.infrastructure.entity.WfCallbackLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code wf_callback_log} 表 Mapper（T-WF-007）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD；{@code request_id}
 * 唯一约束由 uk_wf_callback_request 保证。</p>
 */
@Mapper
public interface WfCallbackLogMapper extends BaseMapper<WfCallbackLogEntity> {
}
