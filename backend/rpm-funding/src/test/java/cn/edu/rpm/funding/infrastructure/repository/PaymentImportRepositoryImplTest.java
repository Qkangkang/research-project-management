package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.funding.infrastructure.entity.PaymentImportBatchEntity;
import cn.edu.rpm.funding.infrastructure.entity.PaymentImportRowEntity;
import cn.edu.rpm.funding.infrastructure.mapper.PaymentImportBatchMapper;
import cn.edu.rpm.funding.infrastructure.mapper.PaymentImportRowMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付导入批次与行仓储纯单元测试（T-FUND-008）。
 *
 * <p>覆盖：批次插入保存来源/文件 ID；{@code uk_payment_import_batch_no} 冲突；
 * 行插入保存原始行数据；{@code (batch_id, row_no)} 组合查询；按批次行号升序。</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentImportRepositoryImplTest {

    private static final String BATCH_ID = "99999999999999999999999999999999";
    private static final String FILE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String UPLOADER_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ROW_ID = "cccccccccccccccccccccccccccccccc";

    @Mock
    private PaymentImportBatchMapper paymentImportBatchMapper;

    @Mock
    private PaymentImportRowMapper paymentImportRowMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<PaymentImportBatchEntity>> batchWrapperCaptor;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<PaymentImportRowEntity>> rowWrapperCaptor;

    private PaymentImportBatchRepositoryImpl batchRepository;

    private PaymentImportRowRepositoryImpl rowRepository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PaymentImportBatchEntity.class);
        TableInfoHelper.initTableInfo(assistant, PaymentImportRowEntity.class);
    }

    @BeforeEach
    void setUp() {
        batchRepository = new PaymentImportBatchRepositoryImpl(paymentImportBatchMapper);
        rowRepository = new PaymentImportRowRepositoryImpl(paymentImportRowMapper);
    }

    @Test
    void insertBatchPersistsSourceTypeFileIdAndCounts() {
        PaymentImportBatchEntity batch = uploadedBatch();

        batchRepository.insert(batch);

        verify(paymentImportBatchMapper).insert(batch);
        assertThat(batch.getBatchNo()).isEqualTo("IMPORT-2026-0001");
        assertThat(batch.getSourceType()).isEqualTo(PaymentImportBatchEntity.SOURCE_EXCEL);
        assertThat(batch.getFileId()).isEqualTo(FILE_ID);
        assertThat(batch.getStatus()).isEqualTo(PaymentImportBatchEntity.STATUS_UPLOADED);
        assertThat(batch.getTotalRows()).isZero();
    }

    @Test
    void duplicateBatchNoPropagatesUniqueConstraintFailure() {
        PaymentImportBatchEntity batch = uploadedBatch();
        when(paymentImportBatchMapper.insert(batch))
                .thenThrow(new DuplicateKeyException("uk_payment_import_batch_no"));

        assertThatThrownBy(() -> batchRepository.insert(batch))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_payment_import_batch_no");
    }

    @Test
    void insertRowPersistsRawDataAmountAndPendingStatus() {
        PaymentImportRowEntity row = pendingRow(1);

        rowRepository.insert(row);

        verify(paymentImportRowMapper).insert(row);
        assertThat(row.getRowNo()).isEqualTo(1);
        assertThat(row.getRawData()).contains("\"payee\":\"测试收款人\"");
        assertThat(row.getAmount()).isEqualByComparingTo("12800.00");
        assertThat(row.getStatus()).isEqualTo(PaymentImportRowEntity.STATUS_PENDING);
    }

    @Test
    void findByBatchIdAndRowNoQueriesCompositeKey() {
        PaymentImportRowEntity row = pendingRow(2);
        when(paymentImportRowMapper.selectOne(
                ArgumentMatchers.<Wrapper<PaymentImportRowEntity>>any())).thenReturn(row);

        Optional<PaymentImportRowEntity> result = rowRepository.findByBatchIdAndRowNo(BATCH_ID, 2);

        assertThat(result).containsSame(row);
        verify(paymentImportRowMapper).selectOne(rowWrapperCaptor.capture());
        assertThat(rowWrapperCaptor.getValue().getSqlSegment()).contains("batch_id =", "row_no =");
        assertThat(rowWrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(BATCH_ID, 2);
    }

    @Test
    void findByBatchIdOrdersByRowNoAsc() {
        PaymentImportRowEntity first = pendingRow(1);
        PaymentImportRowEntity second = pendingRow(2);
        when(paymentImportRowMapper.selectList(
                ArgumentMatchers.<Wrapper<PaymentImportRowEntity>>any()))
                .thenReturn(Arrays.asList(first, second));

        List<PaymentImportRowEntity> result = rowRepository.findByBatchId(BATCH_ID);

        assertThat(result).extracting(PaymentImportRowEntity::getRowNo).containsExactly(1, 2);
        verify(paymentImportRowMapper).selectList(rowWrapperCaptor.capture());
        assertThat(rowWrapperCaptor.getValue().getSqlSegment())
                .contains("batch_id =")
                .containsIgnoringCase("order by row_no asc");
    }

    @Test
    void findByBatchNoQueriesUniqueColumn() {
        PaymentImportBatchEntity batch = uploadedBatch();
        when(paymentImportBatchMapper.selectOne(
                ArgumentMatchers.<Wrapper<PaymentImportBatchEntity>>any())).thenReturn(batch);

        Optional<PaymentImportBatchEntity> result = batchRepository.findByBatchNo("IMPORT-2026-0001");

        assertThat(result).containsSame(batch);
        verify(paymentImportBatchMapper).selectOne(batchWrapperCaptor.capture());
        assertThat(batchWrapperCaptor.getValue().getSqlSegment()).contains("batch_no =");
        assertThat(batchWrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("IMPORT-2026-0001");
    }

    private PaymentImportBatchEntity uploadedBatch() {
        PaymentImportBatchEntity batch = new PaymentImportBatchEntity();
        batch.setId(BATCH_ID);
        batch.setBatchNo("IMPORT-2026-0001");
        batch.setSourceType(PaymentImportBatchEntity.SOURCE_EXCEL);
        batch.setFileId(FILE_ID);
        batch.setStatus(PaymentImportBatchEntity.STATUS_UPLOADED);
        batch.setTotalRows(0);
        batch.setSuccessRows(0);
        batch.setErrorRows(0);
        batch.setUploadedBy(UPLOADER_ID);
        return batch;
    }

    private PaymentImportRowEntity pendingRow(int rowNo) {
        PaymentImportRowEntity row = new PaymentImportRowEntity();
        row.setId(ROW_ID);
        row.setBatchId(BATCH_ID);
        row.setRowNo(rowNo);
        row.setRawData("{\"payee\":\"测试收款人\",\"amount\":12800.00}");
        row.setExternalPaymentNo("PAY-2026-0009");
        row.setAmount(new BigDecimal("12800.00"));
        row.setStatus(PaymentImportRowEntity.STATUS_PENDING);
        return row;
    }
}
