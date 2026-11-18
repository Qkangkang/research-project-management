package cn.edu.rpm.output.infrastructure.mapper;

import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code research_output} 表 Mapper（T-OUT-001）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；查询条件由仓储以参数化
 * {@code LambdaQueryWrapper} 生成，禁止注解 SQL（DM-CHECK）。</p>
 */
@Mapper
public interface ResearchOutputMapper extends BaseMapper<ResearchOutputEntity> {
}
