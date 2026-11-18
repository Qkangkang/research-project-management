package cn.edu.rpm.output.infrastructure.mapper;

import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code output_evidence} 表 Mapper（T-OUT-003）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；按版本查询与计数由仓储以参数化
 * {@code LambdaQueryWrapper} 生成。</p>
 */
@Mapper
public interface OutputEvidenceMapper extends BaseMapper<OutputEvidenceEntity> {
}
