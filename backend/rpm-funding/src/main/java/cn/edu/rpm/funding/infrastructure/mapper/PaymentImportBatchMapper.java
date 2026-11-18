package cn.edu.rpm.funding.infrastructure.mapper;

import cn.edu.rpm.funding.infrastructure.entity.PaymentImportBatchEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code payment_import_batch} 表 Mapper（T-FUND-008）。
 */
@Mapper
public interface PaymentImportBatchMapper extends BaseMapper<PaymentImportBatchEntity> {
}
