package cn.edu.rpm.workflow.infrastructure.mapper;

import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code wf_template_version} 表 Mapper（T-WF-003）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，条件查询由 Repository 构造。</p>
 */
@Mapper
public interface WfTemplateVersionMapper extends BaseMapper<WfTemplateVersionEntity> {
}
