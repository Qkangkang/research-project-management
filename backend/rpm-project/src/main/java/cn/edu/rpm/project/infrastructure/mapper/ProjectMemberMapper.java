package cn.edu.rpm.project.infrastructure.mapper;

import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code project_member} 表 Mapper（T-PRJ-002）。
 *
 * <p>条件查询由仓储以 {@code LambdaQueryWrapper} 构造，无手写 SQL。</p>
 */
@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMemberEntity> {
}
