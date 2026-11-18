package cn.edu.rpm.output.infrastructure.mapper;

import cn.edu.rpm.output.infrastructure.entity.OutputReviewEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code output_review} 表 Mapper（T-OUT-004）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；审核表只追加，不提供 UPDATE 语句。</p>
 */
@Mapper
public interface OutputReviewMapper extends BaseMapper<OutputReviewEntity> {
}
