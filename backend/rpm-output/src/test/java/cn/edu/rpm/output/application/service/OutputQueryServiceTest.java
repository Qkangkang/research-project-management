package cn.edu.rpm.output.application.service;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.LibrarySearchQuery;
import cn.edu.rpm.output.application.dto.OutputDetailVO;
import cn.edu.rpm.output.application.dto.OutputLibraryItemVO;
import cn.edu.rpm.output.application.dto.OutputSearchQuery;
import cn.edu.rpm.output.application.dto.OutputStatItem;
import cn.edu.rpm.output.application.dto.OutputStatsQuery;
import cn.edu.rpm.output.application.dto.OutputSummaryVO;
import cn.edu.rpm.output.domain.model.ProjectAccessInfo;
import cn.edu.rpm.output.domain.port.OutputProjectAccessPort;
import cn.edu.rpm.output.domain.service.OutputDuplicateService;
import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputProjectRelationEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputReviewEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputLibraryRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputSearchRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputStatRow;
import cn.edu.rpm.output.infrastructure.repository.OutputEvidenceRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputPage;
import cn.edu.rpm.output.infrastructure.repository.OutputProjectRelationRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputQueryRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputReviewRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputVersionRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputQueryService} 纯单元测试（T-OUT-014 先行测试）。
 *
 * <p>覆盖 TC-OUT-012（详情不存在 404）、TC-OUT-013（无候选空列表）、
 * TC-OUT-014（无数据范围 403；检索 SQL 固定 owner OR APPROVED）、
 * TC-OSR-001～006（全院库仅已审核、详情元数据、size>100 → RPM-RPT-001、
 * 统计去重映射、空结果 200 空列表）、项目成果列表过滤已撤回成果。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String OWNER_ID = "99998888777766665555444433332222";
    private static final String DEPT_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String VERSION_ID = "ccccddddeeeeffff0000111122223333";
    private static final String HASH = "a".repeat(64);
    private static final String FILE_ID = "bbbbccccddddeeeeffff000011112222";

    @Mock
    private ResearchOutputRepository researchOutputRepository;

    @Mock
    private OutputVersionRepository outputVersionRepository;

    @Mock
    private OutputEvidenceRepository outputEvidenceRepository;

    @Mock
    private OutputReviewRepository outputReviewRepository;

    @Mock
    private OutputProjectRelationRepository relationRepository;

    @Mock
    private OutputQueryRepository outputQueryRepository;

    @Mock
    private OutputDuplicateService duplicateService;

    @Mock
    private OutputProjectAccessPort projectAccessPort;

    @Mock
    private AuthorizationService authorizationService;

    private OutputQueryService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new OutputQueryService(researchOutputRepository, outputVersionRepository,
                outputEvidenceRepository, outputReviewRepository, relationRepository,
                outputQueryRepository, duplicateService, projectAccessPort, authorizationService,
                new ObjectMapper());
        auth = new AuthContext(UUID.fromString("99998888-7777-6666-5555-444433332222"),
                null, "E1001", Set.of("output:view", "output:library:view", "project:view"),
                Set.of(), NOW);
        lenient().when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow());
        lenient().when(authorizationService.mask(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ResearchOutputEntity output(String status) {
        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                ResearchOutputEntity.TYPE_PAPER, "复杂工况下的多模态感知方法",
                "10.1000/example.2026.001", null, OWNER_ID, DEPT_ID, LocalDate.of(2026, 10, 15));
        output.setId(OUTPUT_ID);
        output.setCurrentVersionId(VERSION_ID);
        output.setStatus(status);
        return output;
    }

    private static OutputVersionEntity version() {
        OutputVersionEntity version = OutputVersionEntity.create(OUTPUT_ID, 1,
                "{\"journal\":\"某学报\",\"authors\":[\"张三\"]}", HASH,
                OWNER_ID, OffsetDateTime.parse("2026-11-18T10:00:00+08:00"));
        version.setId(VERSION_ID);
        return version;
    }

    @Test
    void detailAssemblesVersionEvidenceReviewsAndRelations() {
        ResearchOutputEntity output = output(ResearchOutputEntity.STATUS_APPROVED);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(outputVersionRepository.findByOutputId(OUTPUT_ID)).thenReturn(List.of(version()));
        when(outputEvidenceRepository.findByOutputVersionId(VERSION_ID)).thenReturn(List.of(
                OutputEvidenceEntity.create(VERSION_ID, FILE_ID, "PROOF", HASH,
                        OffsetDateTime.now())));
        when(outputReviewRepository.findByOutputVersionIds(List.of(VERSION_ID))).thenReturn(List.of(
                OutputReviewEntity.create(VERSION_ID, OWNER_ID,
                        OutputReviewEntity.DECISION_APPROVED, "材料齐全", OffsetDateTime.now())));
        when(relationRepository.findByOutputId(OUTPUT_ID)).thenReturn(List.of());
        when(relationRepository.countByOutputIdAndStatus(OUTPUT_ID,
                OutputProjectRelationEntity.STATUS_ACTIVE)).thenReturn(0L);

        OutputDetailVO detail = service.detail(OUTPUT_ID, auth);

        assertThat(detail.outputId()).isEqualTo(OUTPUT_ID);
        assertThat(detail.status()).isEqualTo(ResearchOutputEntity.STATUS_APPROVED);
        assertThat(detail.versionNo()).isEqualTo(1);
        assertThat(detail.contentHash()).isEqualTo(HASH);
        assertThat(detail.metadata()).containsEntry("journal", "某学报");
        assertThat(detail.evidence()).hasSize(1);
        assertThat(detail.evidence().get(0).sha256()).isEqualTo(HASH);
        assertThat(detail.reviews()).hasSize(1);
        assertThat(detail.relationCount()).isZero();
        verify(authorizationService).mask(any(), any(), any());
    }

    @Test
    void detailRejectsMissingOutputWithCommon404() {
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(OUTPUT_ID, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_404);
    }

    @Test
    void detailRejectsOutOfScopeOutputWithCommon403() {
        when(researchOutputRepository.findById(OUTPUT_ID))
                .thenReturn(Optional.of(output(ResearchOutputEntity.STATUS_DRAFT)));
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.detail(OUTPUT_ID, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
    }

    @Test
    void searchDelegatesVisibleQueryWithCurrentUserAndMapsRows() {
        OutputSearchRow row = new OutputSearchRow();
        row.setId(OUTPUT_ID);
        row.setOutputType(ResearchOutputEntity.TYPE_PAPER);
        row.setTitle("复杂工况下的多模态感知方法");
        row.setStatus(ResearchOutputEntity.STATUS_APPROVED);
        row.setOwnerId(OWNER_ID);
        row.setDeptId(DEPT_ID);
        row.setCompletedAt(LocalDate.of(2026, 10, 15));
        when(outputQueryRepository.searchVisible(eq(OWNER_ID), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(new OutputPage<>(List.of(row), 1L));

        PageVO<OutputSummaryVO> page = service.search(
                new OutputSearchQuery(null, null, null, null, null, 1, 20), auth);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).outputId()).isEqualTo(OUTPUT_ID);
    }

    @Test
    void searchRejectsOversizedPageWithRpt001() {
        assertThatThrownBy(() -> service.search(
                new OutputSearchQuery(null, null, null, null, null, 1, 101), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RPT_001);
        verify(outputQueryRepository, never()).searchVisible(anyString(), any(), any(), any(),
                any(), any(), anyInt(), anyInt());
    }

    @Test
    void librarySearchMapsApprovedRowsAndRequiresLibraryPermission() {
        OutputLibraryRow row = new OutputLibraryRow();
        row.setId(OUTPUT_ID);
        row.setOutputType(ResearchOutputEntity.TYPE_PAPER);
        row.setTitle("复杂工况下的多模态感知方法");
        row.setDeptId(DEPT_ID);
        row.setCompletedAt(LocalDate.of(2026, 10, 15));
        when(outputQueryRepository.searchLibrary(isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(new OutputPage<>(List.of(row), 1L));

        PageVO<OutputLibraryItemVO> page = service.librarySearch(
                new LibrarySearchQuery(null, null, null, null, 1, 20), auth);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(1L);

        AuthContext noLibrary = new AuthContext(auth.userId(), null, "E1001",
                Set.of("output:view"), Set.of(), NOW);
        assertThatThrownBy(() -> service.librarySearch(
                new LibrarySearchQuery(null, null, null, null, 1, 20), noLibrary))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
    }

    @Test
    void libraryDetailHidesUnapprovedOutputAs404() {
        when(researchOutputRepository.findById(OUTPUT_ID))
                .thenReturn(Optional.of(output(ResearchOutputEntity.STATUS_DRAFT)));

        assertThatThrownBy(() -> service.libraryDetail(OUTPUT_ID, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_404);
    }

    @Test
    void libraryDetailReturnsMetadataAndRelationCount() {
        when(researchOutputRepository.findById(OUTPUT_ID))
                .thenReturn(Optional.of(output(ResearchOutputEntity.STATUS_APPROVED)));
        when(relationRepository.countByOutputIdAndStatus(OUTPUT_ID,
                OutputProjectRelationEntity.STATUS_ACTIVE)).thenReturn(2L);

        var detail = service.libraryDetail(OUTPUT_ID, auth);

        assertThat(detail.title()).isEqualTo("复杂工况下的多模态感知方法");
        assertThat(detail.relationCount()).isEqualTo(2L);
    }

    @Test
    void statsMapsDeduplicatedCounts() {
        OutputStatRow row = new OutputStatRow();
        row.setGroupKey(ResearchOutputEntity.TYPE_PAPER);
        row.setOutputCount(5L);
        when(outputQueryRepository.stats("TYPE", null, 2026)).thenReturn(List.of(row));

        List<OutputStatItem> stats = service.stats(
                new OutputStatsQuery("type", null, 2026), auth);

        assertThat(stats).containsExactly(new OutputStatItem(ResearchOutputEntity.TYPE_PAPER, 5L));
    }

    @Test
    void statsRejectsUnknownGroupWithCommon400() {
        assertThatThrownBy(() -> service.stats(new OutputStatsQuery("PROJECT", null, null), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
        verify(outputQueryRepository, never()).stats(anyString(), any(), any());
    }

    @Test
    void duplicatesReturnsEmptyPageWhenNoCandidate() {
        when(researchOutputRepository.findById(OUTPUT_ID))
                .thenReturn(Optional.of(output(ResearchOutputEntity.STATUS_DRAFT)));
        when(duplicateService.pending(OUTPUT_ID)).thenReturn(List.of());

        PageVO<?> page = service.duplicates(OUTPUT_ID, auth);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotal()).isZero();
    }

    @Test
    void projectOutputsDropsRevokedOutputsAndUsesSingleBatchQuery() {
        ProjectAccessInfo info = new ProjectAccessInfo(
                UuidUtils.parse("aaaabbbbccccddddeeeeffff00001111"),
                UuidUtils.parse(OWNER_ID), UuidUtils.parse(DEPT_ID),
                Set.of(UuidUtils.parse(OWNER_ID)));
        when(projectAccessPort.find("aaaabbbbccccddddeeeeffff00001111"))
                .thenReturn(Optional.of(info));
        OutputProjectRelationEntity active = OutputProjectRelationEntity.create(
                OUTPUT_ID, "aaaabbbbccccddddeeeeffff00001111", "核心算法", new BigDecimal("0.5"),
                OWNER_ID);
        when(relationRepository.findByProjectIdAndStatus("aaaabbbbccccddddeeeeffff00001111",
                OutputProjectRelationEntity.STATUS_ACTIVE)).thenReturn(List.of(active));
        ResearchOutputEntity approved = output(ResearchOutputEntity.STATUS_APPROVED);
        ResearchOutputEntity revoked = output(ResearchOutputEntity.STATUS_REVOKED);
        revoked.setId("ffffffffffffffffffffffffffffffff");
        when(researchOutputRepository.findByIds(List.of(OUTPUT_ID)))
                .thenReturn(List.of(approved, revoked));

        List<OutputSummaryVO> items = service.projectOutputs(
                "aaaabbbbccccddddeeeeffff00001111", auth);

        assertThat(items).extracting(OutputSummaryVO::outputId).containsExactly(OUTPUT_ID);
        verify(researchOutputRepository).findByIds(List.of(OUTPUT_ID));
    }

    @Test
    void projectOutputsRejectsInvisibleProjectWithCommon403() {
        when(projectAccessPort.find("aaaabbbbccccddddeeeeffff00001111"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.projectOutputs(
                "aaaabbbbccccddddeeeeffff00001111", auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
    }
}
