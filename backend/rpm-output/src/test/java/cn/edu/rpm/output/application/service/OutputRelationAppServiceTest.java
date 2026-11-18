package cn.edu.rpm.output.application.service;

import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.OutputRelationVO;
import cn.edu.rpm.output.application.dto.RelateOutputCmd;
import cn.edu.rpm.output.domain.model.ProjectAccessInfo;
import cn.edu.rpm.output.domain.port.OutputProjectAccessPort;
import cn.edu.rpm.output.infrastructure.entity.OutputProjectRelationEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputProjectRelationRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputRelationAppService} 纯单元测试（T-OUT-012 先行测试）。
 *
 * <p>覆盖 TC-OUT-015（关联后 ACTIVE，项目成果列表可见）、TC-OUT-016（一成果
 * 关联两项目）、TC-OUT-017（贡献说明为空 → 400）、TC-OUT-018（项目不可见 →
 * 403）、TC-OUT-019（重复关联 → RPM-OUT-003，关系数仍为 1）、TC-OUT-020
 * （撤销后 REVOKED 且记录保留）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputRelationAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));

    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String PROJECT_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String PROJECT_ID_2 = "ddddccccbbbbaaaa0000111122223333";
    private static final String OWNER_ID = "99998888777766665555444433332222";
    private static final String DEPT_ID = "bbbbccccddddeeeeffff000011112222";
    private static final String RELATION_ID = "eeeeffff000011112222333344445555";

    @Mock
    private ResearchOutputRepository researchOutputRepository;

    @Mock
    private OutputProjectRelationRepository relationRepository;

    @Mock
    private OutputProjectAccessPort projectAccessPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private OutputRelationAppService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new OutputRelationAppService(researchOutputRepository, relationRepository,
                projectAccessPort, authorizationService, auditRecorder, CLOCK);
        auth = new AuthContext(UUID.fromString("99998888-7777-6666-5555-444433332222"),
                null, "E1001", Set.of("output:relate", "project:view"), Set.of(), NOW);
        lenient().when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(output()));
        lenient().when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow());
    }

    private static ResearchOutputEntity output() {
        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                ResearchOutputEntity.TYPE_PAPER, "复杂工况下的多模态感知方法", null, null,
                OWNER_ID, DEPT_ID, LocalDate.of(2026, 10, 1));
        output.setId(OUTPUT_ID);
        return output;
    }

    private static ProjectAccessInfo projectInfo(String projectId) {
        return new ProjectAccessInfo(UuidUtils.parse(projectId), UuidUtils.parse(OWNER_ID),
                UuidUtils.parse(DEPT_ID), Set.of(UuidUtils.parse(OWNER_ID)));
    }

    private static OutputProjectRelationEntity relation(String projectId, String status) {
        OutputProjectRelationEntity relation = OutputProjectRelationEntity.create(
                OUTPUT_ID, projectId, "负责核心算法设计与验证", new BigDecimal("0.60"), OWNER_ID);
        relation.setId(RELATION_ID);
        if (OutputProjectRelationEntity.STATUS_REVOKED.equals(status)) {
            relation.revoke(OWNER_ID, java.time.OffsetDateTime.now(CLOCK));
        }
        return relation;
    }

    @Test
    void relateCreatesActiveRelationAndAudit() {
        when(projectAccessPort.find(PROJECT_ID)).thenReturn(Optional.of(projectInfo(PROJECT_ID)));
        when(relationRepository.findByOutputIdAndProjectId(OUTPUT_ID, PROJECT_ID))
                .thenReturn(Optional.empty());

        OutputRelationVO vo = service.relate(OUTPUT_ID,
                new RelateOutputCmd(PROJECT_ID, "负责核心算法设计与验证", new BigDecimal("0.60")), auth);

        assertThat(vo.status()).isEqualTo(OutputProjectRelationEntity.STATUS_ACTIVE);
        assertThat(vo.projectId()).isEqualTo(PROJECT_ID);
        assertThat(vo.contributionDesc()).isEqualTo("负责核心算法设计与验证");
        assertThat(vo.attributionRatio()).isEqualByComparingTo("0.60");

        ArgumentCaptor<OutputProjectRelationEntity> captor =
                ArgumentCaptor.forClass(OutputProjectRelationEntity.class);
        verify(relationRepository).insert(captor.capture());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(OutputProjectRelationEntity.STATUS_ACTIVE);
        verify(auditRecorder).record(any());
    }

    @Test
    void relateAllowsSecondProjectForSameOutput() {
        when(projectAccessPort.find(PROJECT_ID_2)).thenReturn(Optional.of(projectInfo(PROJECT_ID_2)));
        when(relationRepository.findByOutputIdAndProjectId(OUTPUT_ID, PROJECT_ID_2))
                .thenReturn(Optional.empty());

        OutputRelationVO vo = service.relate(OUTPUT_ID,
                new RelateOutputCmd(PROJECT_ID_2, "另一项目的数据支撑", null), auth);

        assertThat(vo.status()).isEqualTo(OutputProjectRelationEntity.STATUS_ACTIVE);
        assertThat(vo.projectId()).isEqualTo(PROJECT_ID_2);
        verify(relationRepository).insert(any());
    }

    @Test
    void relateRejectsBlankContributionWithCommon400() {
        when(projectAccessPort.find(PROJECT_ID)).thenReturn(Optional.of(projectInfo(PROJECT_ID)));

        assertThatThrownBy(() -> service.relate(OUTPUT_ID,
                new RelateOutputCmd(PROJECT_ID, "   ", null), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
        verify(relationRepository, never()).insert(any());
    }

    @Test
    void relateRejectsDuplicateActiveRelationWithOut003() {
        when(projectAccessPort.find(PROJECT_ID)).thenReturn(Optional.of(projectInfo(PROJECT_ID)));
        when(relationRepository.findByOutputIdAndProjectId(OUTPUT_ID, PROJECT_ID))
                .thenReturn(Optional.of(relation(PROJECT_ID,
                        OutputProjectRelationEntity.STATUS_ACTIVE)));

        assertThatThrownBy(() -> service.relate(OUTPUT_ID,
                new RelateOutputCmd(PROJECT_ID, "重复关联", null), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OUT_003);
        verify(relationRepository, never()).insert(any());
    }

    @Test
    void relateRejectsInvisibleProjectWithCommon403() {
        when(projectAccessPort.find(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.relate(OUTPUT_ID,
                new RelateOutputCmd(PROJECT_ID, "负责核心算法", null), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
        verify(relationRepository, never()).insert(any());
    }

    @Test
    void relateRejectsProjectOutsideDataScopeWithCommon403() {
        when(projectAccessPort.find(PROJECT_ID)).thenReturn(Optional.of(projectInfo(PROJECT_ID)));
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow(), AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.relate(OUTPUT_ID,
                new RelateOutputCmd(PROJECT_ID, "负责核心算法", null), auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_403);
    }

    @Test
    void relateRestoresRevokedRelationInsteadOfCreatingDuplicateRow() {
        when(projectAccessPort.find(PROJECT_ID)).thenReturn(Optional.of(projectInfo(PROJECT_ID)));
        OutputProjectRelationEntity revoked = relation(PROJECT_ID,
                OutputProjectRelationEntity.STATUS_REVOKED);
        when(relationRepository.findByOutputIdAndProjectId(OUTPUT_ID, PROJECT_ID))
                .thenReturn(Optional.of(revoked));
        when(relationRepository.update(revoked)).thenReturn(1);

        OutputRelationVO vo = service.relate(OUTPUT_ID,
                new RelateOutputCmd(PROJECT_ID, "恢复关联", null), auth);

        assertThat(vo.status()).isEqualTo(OutputProjectRelationEntity.STATUS_ACTIVE);
        verify(relationRepository).update(revoked);
        verify(relationRepository, never()).insert(any());
        verify(relationRepository, never()).findById(anyString());
        assertThat(revoked.getRevokedAt()).isNull();
    }

    @Test
    void revokeMarksRelationRevokedAndKeepsRow() {
        OutputProjectRelationEntity active = relation(PROJECT_ID,
                OutputProjectRelationEntity.STATUS_ACTIVE);
        when(relationRepository.findById(RELATION_ID)).thenReturn(Optional.of(active));
        when(relationRepository.update(active)).thenReturn(1);

        OutputRelationVO vo = service.revoke(OUTPUT_ID, RELATION_ID, auth);

        assertThat(vo.status()).isEqualTo(OutputProjectRelationEntity.STATUS_REVOKED);
        assertThat(active.getStatus()).isEqualTo(OutputProjectRelationEntity.STATUS_REVOKED);
        assertThat(active.getRevokedBy()).isEqualTo(OWNER_ID);
        verify(relationRepository, never()).insert(any());
    }

    @Test
    void revokeIsIdempotentForAlreadyRevokedRelation() {
        OutputProjectRelationEntity revoked = relation(PROJECT_ID,
                OutputProjectRelationEntity.STATUS_REVOKED);
        when(relationRepository.findById(RELATION_ID)).thenReturn(Optional.of(revoked));

        OutputRelationVO vo = service.revoke(OUTPUT_ID, RELATION_ID, auth);

        assertThat(vo.status()).isEqualTo(OutputProjectRelationEntity.STATUS_REVOKED);
        verify(relationRepository, never()).update(any());
    }

    @Test
    void revokeRejectsRelationOfAnotherOutputWithCommon404() {
        OutputProjectRelationEntity foreign = relation(PROJECT_ID,
                OutputProjectRelationEntity.STATUS_ACTIVE);
        foreign.setOutputId(PROJECT_ID_2);
        when(relationRepository.findById(RELATION_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.revoke(OUTPUT_ID, RELATION_ID, auth))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_404);
    }
}
