package cn.edu.rpm.iam.infrastructure.mapper;

import cn.edu.rpm.iam.infrastructure.entity.IamRolePermissionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code iam_role_permission} 表 Mapper（Batch-4 / T-IAM-008）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，不声明注解 SQL
 * （DM-CHECK：SQL 一律外置 XML 或由 Wrapper 构造）。</p>
 */
@Mapper
public interface IamRolePermissionMapper extends BaseMapper<IamRolePermissionEntity> {
}
