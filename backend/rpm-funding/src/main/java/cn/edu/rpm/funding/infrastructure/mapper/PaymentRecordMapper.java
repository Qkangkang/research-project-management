package cn.edu.rpm.funding.infrastructure.mapper;

import cn.edu.rpm.funding.infrastructure.entity.PaymentRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code payment_record} 表 Mapper（T-FUND-007）。
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecordEntity> {
}
