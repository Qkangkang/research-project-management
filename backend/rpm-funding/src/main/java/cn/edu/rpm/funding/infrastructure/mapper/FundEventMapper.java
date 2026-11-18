package cn.edu.rpm.funding.infrastructure.mapper;

import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code fund_event} 表 Mapper（T-FUND-006）。
 *
 * <p>仅继承通用插入/查询能力；Repository 不暴露更新与删除入口。</p>
 */
@Mapper
public interface FundEventMapper extends BaseMapper<FundEventEntity> {
}
