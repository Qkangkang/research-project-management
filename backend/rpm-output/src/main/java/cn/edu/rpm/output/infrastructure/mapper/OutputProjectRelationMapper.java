package cn.edu.rpm.output.infrastructure.mapper;

import cn.edu.rpm.output.infrastructure.entity.OutputProjectRelationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code output_project_relation} 表 Mapper（T-OUT-005）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；撤销只做状态迁移（REVOKED），不物理删除。</p>
 */
@Mapper
public interface OutputProjectRelationMapper extends BaseMapper<OutputProjectRelationEntity> {
}
