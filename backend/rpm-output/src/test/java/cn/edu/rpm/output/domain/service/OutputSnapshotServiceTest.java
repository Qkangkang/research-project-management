package cn.edu.rpm.output.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.output.domain.model.OutputSnapshot;
import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.repository.OutputEvidenceRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputVersionRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link OutputSnapshotService} 纯单元测试（T-OUT-015 先行测试；TC-OUT-021）。
 *
 * <p>覆盖：验收快照引用 V1 后成果修订到 V2，原快照仍指向 V1 与原证据哈希；
 * 版本不属于成果 → {@code RPM-COMMON-400}；成果/版本不存在 → 404。
 * 快照为不可变记录，本服务不写业务表、不修改成果。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputSnapshotServiceTest {

    private static final Instant FROZEN_AT = Instant.parse("2026-11-18T02:00:00Z");
    private static final String OUTPUT_ID = "11112222333344445555666677778888";
    private static final String V1_ID = "ccccddddeeeeffff0000111122223333";
    private static final String V2_ID = "dddddddddddddddddddddddddddddddd";
    private static final String FILE_ID = "bbbbccccddddeeeeffff000011112222";
    private static final String OWNER_ID = "99998888777766665555444433332222";
    private static final String DEPT_ID = "aaaabbbbccccddddeeeeffff00001111";
    private static final String HASH_V1 = "a".repeat(64);
    private static final String HASH_V2 = "b".repeat(64);

    @Mock
    private ResearchOutputRepository researchOutputRepository;

    @Mock
    private OutputVersionRepository outputVersionRepository;

    @Mock
    private OutputEvidenceRepository outputEvidenceRepository;

    private OutputSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new OutputSnapshotService(researchOutputRepository, outputVersionRepository,
                outputEvidenceRepository);
        lenient().when(researchOutputRepository.findById(OUTPUT_ID))
                .thenReturn(Optional.of(output()));
    }

    private static ResearchOutputEntity output() {
        ResearchOutputEntity output = ResearchOutputEntity.newDraft(
                ResearchOutputEntity.TYPE_PAPER, "复杂工况下的多模态感知方法", null, null,
                OWNER_ID, DEPT_ID, LocalDate.of(2026, 10, 1));
        output.setId(OUTPUT_ID);
        return output;
    }

    private static OutputVersionEntity version(String id, int versionNo, String hash) {
        OutputVersionEntity version = OutputVersionEntity.create(OUTPUT_ID, versionNo, "{}", hash,
                OWNER_ID, OffsetDateTime.parse("2026-11-18T10:00:00+08:00"));
        version.setId(id);
        return version;
    }

    @Test
    void frozenSnapshotKeepsVersionOneAfterRevisionCreatesVersionTwo() {
        when(outputVersionRepository.findById(V1_ID))
                .thenReturn(Optional.of(version(V1_ID, 1, HASH_V1)));
        when(outputEvidenceRepository.findByOutputVersionId(V1_ID)).thenReturn(List.of(
                OutputEvidenceEntity.create(V1_ID, FILE_ID, "PROOF", HASH_V1,
                        OffsetDateTime.now())));

        OutputSnapshot snapshotV1 = service.freeze(OUTPUT_ID, V1_ID, FROZEN_AT);

        // 成果修订到 V2 后再次冻结；原快照对象不被修改
        when(outputVersionRepository.findById(V2_ID))
                .thenReturn(Optional.of(version(V2_ID, 2, HASH_V2)));
        when(outputEvidenceRepository.findByOutputVersionId(V2_ID)).thenReturn(List.of(
                OutputEvidenceEntity.create(V2_ID, FILE_ID, "PROOF", HASH_V2,
                        OffsetDateTime.now())));
        OutputSnapshot snapshotV2 = service.freeze(OUTPUT_ID, V2_ID, FROZEN_AT);

        assertThat(snapshotV1.versionId()).isEqualTo(UuidUtils.parse(V1_ID));
        assertThat(snapshotV1.versionNo()).isEqualTo(1);
        assertThat(snapshotV1.contentHash()).isEqualTo(HASH_V1);
        assertThat(snapshotV1.evidenceHashes()).hasSize(1);
        assertThat(snapshotV1.evidenceHashes().get(0).sha256()).isEqualTo(HASH_V1);
        assertThat(snapshotV2.versionId()).isEqualTo(UuidUtils.parse(V2_ID));
        assertThat(snapshotV2.contentHash()).isEqualTo(HASH_V2);
    }

    @Test
    void freezeRejectsVersionOfAnotherOutputWithCommon400() {
        OutputVersionEntity foreign = version(V1_ID, 1, HASH_V1);
        foreign.setOutputId("ffffffffffffffffffffffffffffffff");
        when(outputVersionRepository.findById(V1_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.freeze(OUTPUT_ID, V1_ID, FROZEN_AT))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_400);
    }

    @Test
    void freezeRejectsMissingVersionWithCommon404() {
        when(outputVersionRepository.findById(V1_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.freeze(OUTPUT_ID, V1_ID, FROZEN_AT))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMMON_404);
    }
}
