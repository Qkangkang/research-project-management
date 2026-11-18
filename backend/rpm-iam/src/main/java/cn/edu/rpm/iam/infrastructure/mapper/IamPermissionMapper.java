package cn.edu.rpm.iam.infrastructure.mapper;

import cn.edu.rpm.iam.infrastructure.entity.IamPermissionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code iam_permission} 表 Mapper（T-IAM-003）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，不声明任何注解 SQL；
 * 条件查询由 Repository 层以 Wrapper 构造。</p>
 */
@Mapper
public interface IamPermissionMapper extends BaseMapper<IamPermissionEntity> {
}
