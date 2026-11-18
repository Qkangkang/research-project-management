package cn.edu.rpm.workflow.infrastructure.mapper;

import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code wf_template} 表 Mapper（T-WF-002）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，不声明任何注解 SQL；
 * 条件查询由 Repository 层以 Wrapper 构造（DM-CHECK）。</p>
 */
@Mapper
public interface WfTemplateMapper extends BaseMapper<WfTemplateEntity> {
}
