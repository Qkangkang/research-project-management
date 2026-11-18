package cn.edu.rpm.application.infrastructure.mapper;

import cn.edu.rpm.application.infrastructure.entity.ApplyBatchEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code apply_batch} 表 Mapper（T-APP-001）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，不声明任何注解 SQL
 * （DM-CHECK：条件查询由 Repository 层以 Wrapper 构造）。</p>
 */
@Mapper
public interface ApplyBatchMapper extends BaseMapper<ApplyBatchEntity> {
}
