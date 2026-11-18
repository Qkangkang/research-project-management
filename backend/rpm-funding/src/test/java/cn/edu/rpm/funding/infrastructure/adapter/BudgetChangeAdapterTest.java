package cn.edu.rpm.funding.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.application.dto.BudgetVersionDetail;
import cn.edu.rpm.funding.application.service.BudgetAppService;
import cn.edu.rpm.project.domain.port.BudgetChangePort.BudgetChangeCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BudgetChangeAdapter} 纯单元测试（T-FUND-017；TC-FUND-006、TC-CHG-107）。
 *
 * <p>覆盖：合法载荷解析 {@code budgetVersionDraftId} 并委托预算服务；
 * 缺字段/非法 JSON 返回 {@code RPM-COMMON-400}。</p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetChangeAdapterTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String DRAFT_ID = "33333333333333333333333333333333";
    private static final String CHANGE_REQUEST_ID = "66666666666666666666666666666666";
    private static final String CHANGE_VERSION_ID = "67676767676767676767676767676767";
    private static final UUID OPERATOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private BudgetAppService budgetAppService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validPayloadDelegatesToBudgetService() {
        BudgetChangeAdapter adapter = new BudgetChangeAdapter(budgetAppService, objectMapper);
        BudgetVersionDetail detail = new BudgetVersionDetail(DRAFT_ID, PROJECT_ID, 2,
                new java.math.BigDecimal("1200000.00"), "EFFECTIVE", "BLOCK", null, null, java.util.List.of());
        when(budgetAppService.applyApprovedChange(PROJECT_ID, DRAFT_ID, CHANGE_REQUEST_ID,
                CHANGE_VERSION_ID, OPERATOR_ID)).thenReturn(detail);

        String versionId = adapter.applyBudgetChange(new BudgetChangeCommand(PROJECT_ID,
                CHANGE_REQUEST_ID, CHANGE_VERSION_ID,
                "{\"budgetVersionDraftId\":\"" + DRAFT_ID + "\"}", OPERATOR_ID));

        assertThat(versionId).isEqualTo(DRAFT_ID);
        verify(budgetAppService).applyApprovedChange(PROJECT_ID, DRAFT_ID, CHANGE_REQUEST_ID,
                CHANGE_VERSION_ID, OPERATOR_ID);
    }

    @Test
    void missingDraftIdRejectedWithCommon400() {
        BudgetChangeAdapter adapter = new BudgetChangeAdapter(budgetAppService, objectMapper);

        assertThatThrownBy(() -> adapter.applyBudgetChange(new BudgetChangeCommand(PROJECT_ID,
                CHANGE_REQUEST_ID, CHANGE_VERSION_ID, "{\"other\":1}", OPERATOR_ID)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void malformedJsonRejectedWithCommon400() {
        BudgetChangeAdapter adapter = new BudgetChangeAdapter(budgetAppService, objectMapper);

        assertThatThrownBy(() -> adapter.applyBudgetChange(new BudgetChangeCommand(PROJECT_ID,
                CHANGE_REQUEST_ID, CHANGE_VERSION_ID, "not-json", OPERATOR_ID)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }
}
