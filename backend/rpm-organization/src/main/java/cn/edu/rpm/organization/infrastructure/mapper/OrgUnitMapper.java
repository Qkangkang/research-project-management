package cn.edu.rpm.organization.infrastructure.mapper;

import cn.edu.rpm.organization.infrastructure.entity.OrgUnitEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code org_unit} 表 Mapper（T-ORG-001）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，不声明任何注解 SQL；
 * 条件查询（子节点、子树前缀）由 Repository 层以 Wrapper 构造。</p>
 */
@Mapper
public interface OrgUnitMapper extends BaseMapper<OrgUnitEntity> {
}
