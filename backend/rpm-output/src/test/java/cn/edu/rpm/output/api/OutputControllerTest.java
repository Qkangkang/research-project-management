package cn.edu.rpm.output.api;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.output.api.dto.CreateOutputRequest;
import cn.edu.rpm.output.api.dto.RelateOutputRequest;
import cn.edu.rpm.output.api.dto.WithdrawOutputRequest;
import cn.edu.rpm.output.application.dto.OutputCreated;
import cn.edu.rpm.output.application.dto.OutputLibraryItemVO;
import cn.edu.rpm.output.application.dto.OutputRelationVO;
import cn.edu.rpm.output.application.dto.OutputSubmission;
import cn.edu.rpm.output.application.dto.OutputWithdrawResult;
import cn.edu.rpm.output.application.service.OutputAppService;
import cn.edu.rpm.output.application.service.OutputQueryService;
import cn.edu.rpm.output.application.service.OutputRelationAppService;
import cn.edu.rpm.output.application.service.OutputReviewService;
import cn.edu.rpm.output.application.service.OutputSubmitAppService;
import cn.edu.rpm.output.application.service.OutputWithdrawAppService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputController} 纯单元测试（T-OUT-016 先行测试）。
 *
 * <p>覆盖接口契约：登记返回 {@code output_id}（含 {@code RPM-OUT-001} 提示透传）、
 * 提交返回状态、关联返回 {@code relation_id}、全院检索空结果 200 空列表、
 * 撤回审核返回状态、未登录 401、{@code duplicates} 缺少 outputId → 400。
 * 业务规则由应用服务测试覆盖，本类只验证请求映射与身份提取。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputControllerTest {

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String PROJECT_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String RELATION_ID = "ccccddddeeeeffff0000111122223333";

    @Mock
    private OutputAppService outputAppService;

    @Mock
    private OutputSubmitAppService submitAppService;

    @Mock
    private OutputReviewService reviewService;

    @Mock
    private OutputRelationAppService relationAppService;

    @Mock
    private OutputWithdrawAppService withdrawAppService;

    @Mock
    private OutputQueryService queryService;

    private OutputController controller;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        controller = new OutputController(outputAppService, submitAppService, reviewService,
                relationAppService, withdrawAppService, queryService);
        auth = new AuthContext(UUID.fromString("99998888-7777-6666-5555-444433332222"),
                null, "E1001", Set.of("output:create", "output:view", "output:library:view"),
                Set.of(), Instant.parse("2026-11-18T02:00:00Z"));
        AuthContextHolder.set(auth);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void createMapsRequestAndReturnsOutputId() {
        when(outputAppService.createDraft(any(), any())).thenReturn(new OutputCreated(
                OUTPUT_ID, "DRAFT", "version-id", 1, "RPM-OUT-001", "疑似重复"));

        Result<OutputCreated> result = controller.create(new CreateOutputRequest(
                "PAPER", "复杂工况下的多模态感知方法", "10.1000/example.2026.001", null,
                LocalDate.of(2026, 10, 15), null, "aaaabbbbccccddddeeeeffff00001111",
                Map.of("journal", "某学报"), List.of("bbbbccccddddeeeeffff000011112222")));

        assertThat(result.getCode()).isEqualTo(Result.CODE_OK);
        assertThat(result.getData().outputId()).isEqualTo(OUTPUT_ID);
        assertThat(result.getData().warningCode()).isEqualTo("RPM-OUT-001");

        ArgumentCaptor<cn.edu.rpm.output.application.dto.CreateOutputCmd> captor =
                ArgumentCaptor.forClass(cn.edu.rpm.output.application.dto.CreateOutputCmd.class);
        verify(outputAppService).createDraft(captor.capture(), eq(auth));
        assertThat(captor.getValue().outputType()).isEqualTo("PAPER");
        assertThat(captor.getValue().evidenceFileIds()).hasSize(1);
    }

    @Test
    void submitReturnsSubmittedStatus() {
        when(submitAppService.submit(OUTPUT_ID, auth)).thenReturn(new OutputSubmission(
                OUTPUT_ID, "SUBMITTED", "version-id", 1));

        Result<OutputSubmission> result = controller.submit(OUTPUT_ID);

        assertThat(result.getData().status()).isEqualTo("SUBMITTED");
        assertThat(result.getData().versionNo()).isEqualTo(1);
    }

    @Test
    void relateReturnsRelationId() {
        when(relationAppService.relate(eq(OUTPUT_ID), any(), eq(auth))).thenReturn(
                new OutputRelationVO(RELATION_ID, OUTPUT_ID, PROJECT_ID, "负责核心算法",
                        new BigDecimal("0.60"), "ACTIVE"));

        Result<OutputRelationVO> result = controller.relate(OUTPUT_ID,
                new RelateOutputRequest(PROJECT_ID, "负责核心算法", new BigDecimal("0.60")));

        assertThat(result.getData().relationId()).isEqualTo(RELATION_ID);
        assertThat(result.getData().status()).isEqualTo("ACTIVE");
    }

    @Test
    void withdrawReviewReturnsRevokedStatus() {
        when(withdrawAppService.reviewWithdraw(eq(OUTPUT_ID), any(), eq(auth)))
                .thenReturn(new OutputWithdrawResult(OUTPUT_ID, "REVOKED", false));

        Result<OutputWithdrawResult> result = controller.reviewWithdraw(OUTPUT_ID,
                new cn.edu.rpm.output.api.dto.WithdrawReviewRequest(true, "同意撤回"));

        assertThat(result.getData().status()).isEqualTo("REVOKED");
        assertThat(result.getData().withdrawReview()).isFalse();
    }

    @Test
    void withdrawMapsReason() {
        when(withdrawAppService.withdraw(eq(OUTPUT_ID), any(), eq(auth)))
                .thenReturn(new OutputWithdrawResult(OUTPUT_ID, "APPROVED", true));

        Result<OutputWithdrawResult> result = controller.withdraw(OUTPUT_ID,
                new WithdrawOutputRequest("论文重复登记"));

        assertThat(result.getData().withdrawReview()).isTrue();
        ArgumentCaptor<cn.edu.rpm.output.application.dto.WithdrawOutputCmd> captor =
                ArgumentCaptor.forClass(cn.edu.rpm.output.application.dto.WithdrawOutputCmd.class);
        verify(withdrawAppService).withdraw(eq(OUTPUT_ID), captor.capture(), eq(auth));
        assertThat(captor.getValue().reason()).isEqualTo("论文重复登记");
    }

    @Test
    void librarySearchReturnsEmptyPageForNoMatch() {
        when(queryService.librarySearch(any(), eq(auth)))
                .thenReturn(PageVO.of(List.of(), 1, 20, 0L));

        Result<PageVO<OutputLibraryItemVO>> result = controller.librarySearch(
                null, null, null, null, 1, 20);

        assertThat(result.getCode()).isEqualTo(Result.CODE_OK);
        assertThat(result.getData().getItems()).isEmpty();
        assertThat(result.getData().getTotal()).isZero();
    }

    @Test
    void duplicatesWithoutOutputIdReturnsCommon400() {
        assertThatThrownBy(() -> controller.duplicates(null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void unauthenticatedRequestReturnsCommon401() {
        AuthContextHolder.clear();

        assertThatThrownBy(() -> controller.detail(OUTPUT_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_401);
    }
}
