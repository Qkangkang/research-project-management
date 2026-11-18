package cn.edu.rpm.workflow.infrastructure.mapper;

import cn.edu.rpm.workflow.infrastructure.entity.WfDelegationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code wf_delegation} 表 Mapper（T-WF-006）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，条件查询由 Repository 构造。</p>
 */
@Mapper
public interface WfDelegationMapper extends BaseMapper<WfDelegationEntity> {
}
