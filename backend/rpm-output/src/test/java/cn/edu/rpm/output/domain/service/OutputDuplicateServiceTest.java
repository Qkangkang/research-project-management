package cn.edu.rpm.output.domain.service;

import cn.edu.rpm.output.domain.model.DuplicateMatchPolicy;
import cn.edu.rpm.output.infrastructure.entity.OutputDuplicateCandidateEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputDuplicateCandidateRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutputDuplicateService} 纯单元测试（T-OUT-010 先行测试）。
 *
 * <p>覆盖 TC-OUT-007（专利号相同生成 PATENT 候选，不删除任一成果）、
 * TC-OUT-008（名称相同但完成人不同生成低分 TITLE 候选，低于确认阈值）、
 * TC-OUT-013（无候选返回空列表）；同一候选重复检测按唯一键幂等，
 * 领域服务不提供删除/合并能力（BR-21）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputDuplicateServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-18T10:00:00+08:00");
    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String OTHER_ID = "99998888777766665555444433332222";
    private static final String OWNER_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String OTHER_OWNER_ID = "bbbbccccddddeeeeffff000011112222";
    private static final String DEPT_ID = "ccccddddeeeeffff0000111122223333";

    @Mock
    private ResearchOutputRepository researchOutputRepository;

    @Mock
    private OutputDuplicateCandidateRepository candidateRepository;

    private OutputDuplicateService service;

    @BeforeEach
    void setUp() {
        service = new OutputDuplicateService(researchOutputRepository, candidateRepository,
                new DuplicateMatchPolicy(new BigDecimal("1.00"), new BigDecimal("1.00"),
                        new BigDecimal("0.90"), new BigDecimal("0.60"), new BigDecimal("0.90")));
    }

    private static ResearchOutputEntity output(String id, String title, String doi,
                                               String patentNo, String ownerId) {
        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                ResearchOutputEntity.TYPE_PATENT, title, doi, patentNo, ownerId, DEPT_ID,
                LocalDate.of(2026, 10, 1));
        output.setId(id);
        return output;
    }

    @Test
    void findCandidatesDetectsSamePatentNoWithoutDeleting() {
        ResearchOutputEntity current = output(OUTPUT_ID, "一种感知装置", null, "ZL2026000001", OWNER_ID);
        ResearchOutputEntity other = output(OTHER_ID, "另一种感知装置", null, "ZL2026000001", OTHER_OWNER_ID);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(current));
        when(researchOutputRepository.findByPatentNo("ZL2026000001"))
                .thenReturn(List.of(current, other));
        when(candidateRepository.findByOutputIdAndStatus(OUTPUT_ID,
                OutputDuplicateCandidateEntity.STATUS_OPEN)).thenReturn(List.of());

        service.findCandidates(OUTPUT_ID, NOW);

        ArgumentCaptor<OutputDuplicateCandidateEntity> captor =
                ArgumentCaptor.forClass(OutputDuplicateCandidateEntity.class);
        verify(candidateRepository).insertIfAbsent(captor.capture());
        OutputDuplicateCandidateEntity candidate = captor.getValue();
        assertThat(candidate.getMatchType()).isEqualTo(OutputDuplicateCandidateEntity.MATCH_PATENT);
        assertThat(candidate.getCandidateOutputId()).isEqualTo(OTHER_ID);
        assertThat(candidate.getScore()).isEqualByComparingTo("1.00");
        assertThat(candidate.getStatus()).isEqualTo(OutputDuplicateCandidateEntity.STATUS_OPEN);
        assertThat(current.getId()).isEqualTo(OUTPUT_ID);
        verify(researchOutputRepository, never()).update(any());
    }

    @Test
    void findCandidatesFlagsSameTitleDifferentOwnerAsLowScore() {
        ResearchOutputEntity current = output(OUTPUT_ID, "复杂工况下的多模态感知方法", null, null, OWNER_ID);
        ResearchOutputEntity other = output(OTHER_ID, "复杂工况下的多模态感知方法", null, null, OTHER_OWNER_ID);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(current));
        when(researchOutputRepository.findByTitle("复杂工况下的多模态感知方法"))
                .thenReturn(List.of(current, other));
        when(candidateRepository.findByOutputIdAndStatus(anyString(), anyString()))
                .thenReturn(List.of());

        service.findCandidates(OUTPUT_ID, NOW);

        ArgumentCaptor<OutputDuplicateCandidateEntity> captor =
                ArgumentCaptor.forClass(OutputDuplicateCandidateEntity.class);
        verify(candidateRepository).insertIfAbsent(captor.capture());
        OutputDuplicateCandidateEntity candidate = captor.getValue();
        assertThat(candidate.getMatchType()).isEqualTo(OutputDuplicateCandidateEntity.MATCH_TITLE);
        assertThat(candidate.getScore()).isEqualByComparingTo("0.60");
        assertThat(service.requiresManualConfirmation(candidate)).isFalse();
    }

    @Test
    void findCandidatesFlagsSameTitleSameOwnerAsOwnerMatch() {
        ResearchOutputEntity current = output(OUTPUT_ID, "复杂工况下的多模态感知方法", null, null, OWNER_ID);
        ResearchOutputEntity other = output(OTHER_ID, "复杂工况下的多模态感知方法", null, null, OWNER_ID);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(current));
        when(researchOutputRepository.findByTitle(anyString())).thenReturn(List.of(current, other));
        when(candidateRepository.findByOutputIdAndStatus(anyString(), anyString()))
                .thenReturn(List.of());

        service.findCandidates(OUTPUT_ID, NOW);

        ArgumentCaptor<OutputDuplicateCandidateEntity> captor =
                ArgumentCaptor.forClass(OutputDuplicateCandidateEntity.class);
        verify(candidateRepository).insertIfAbsent(captor.capture());
        assertThat(captor.getValue().getMatchType())
                .isEqualTo(OutputDuplicateCandidateEntity.MATCH_OWNER);
        assertThat(service.requiresManualConfirmation(captor.getValue())).isTrue();
    }

    @Test
    void findCandidatesReturnsEmptyWhenNoMatch() {
        ResearchOutputEntity current = output(OUTPUT_ID, "独特标题", null, null, OWNER_ID);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(current));
        when(candidateRepository.findByOutputIdAndStatus(OUTPUT_ID,
                OutputDuplicateCandidateEntity.STATUS_OPEN)).thenReturn(List.of());

        assertThat(service.findCandidates(OUTPUT_ID, NOW)).isEmpty();
        verify(candidateRepository, never()).insertIfAbsent(any());
    }

    @Test
    void findCandidatesIsIdempotentWhenCandidateAlreadyExists() {
        ResearchOutputEntity current = output(OUTPUT_ID, "一种感知装置", null, "ZL2026000001", OWNER_ID);
        ResearchOutputEntity other = output(OTHER_ID, "另一种感知装置", null, "ZL2026000001", OTHER_OWNER_ID);
        when(researchOutputRepository.findById(OUTPUT_ID)).thenReturn(Optional.of(current));
        when(researchOutputRepository.findByPatentNo("ZL2026000001"))
                .thenReturn(List.of(current, other));
        when(candidateRepository.insertIfAbsent(any())).thenReturn(false, false);
        OutputDuplicateCandidateEntity existing = OutputDuplicateCandidateEntity.create(
                OUTPUT_ID, OTHER_ID, OutputDuplicateCandidateEntity.MATCH_PATENT,
                BigDecimal.ONE, NOW);
        when(candidateRepository.findByOutputIdAndStatus(OUTPUT_ID,
                OutputDuplicateCandidateEntity.STATUS_OPEN)).thenReturn(List.of(existing));

        service.findCandidates(OUTPUT_ID, NOW);
        service.findCandidates(OUTPUT_ID, NOW);

        verify(candidateRepository, org.mockito.Mockito.times(2)).insertIfAbsent(any());
        assertThat(service.findCandidates(OUTPUT_ID, NOW)).hasSize(1);
    }
}
