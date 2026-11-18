package cn.edu.rpm.iam.infrastructure.mapper;

import cn.edu.rpm.iam.infrastructure.entity.IamLoginLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper（V10 认证模块）。
 */
@Mapper
public interface IamLoginLogMapper extends BaseMapper<IamLoginLogEntity> {
}
