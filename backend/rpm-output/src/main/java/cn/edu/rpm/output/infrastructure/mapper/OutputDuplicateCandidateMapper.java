package cn.edu.rpm.output.infrastructure.mapper;

import cn.edu.rpm.output.infrastructure.entity.OutputDuplicateCandidateEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code output_duplicate_candidate} 表 Mapper（T-OUT-006）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；唯一键
 * {@code uk_output_duplicate} 由仓储以幂等插入语义使用。</p>
 */
@Mapper
public interface OutputDuplicateCandidateMapper extends BaseMapper<OutputDuplicateCandidateEntity> {
}
