package cn.edu.rpm.iam.infrastructure.mapper;

import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code iam_user} 表 Mapper（T-IAM-001）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，不声明任何注解 SQL
 * （DM-CHECK：SQL 由框架按实体注解生成）；条件查询由 Repository 层以 Wrapper 构造。</p>
 */
@Mapper
public interface IamUserMapper extends BaseMapper<IamUserEntity> {
}
