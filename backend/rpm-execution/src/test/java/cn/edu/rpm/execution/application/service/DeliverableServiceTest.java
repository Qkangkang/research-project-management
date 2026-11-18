package cn.edu.rpm.execution.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.execution.application.dto.DeliverableCreated;
import cn.edu.rpm.execution.application.dto.DeliverableView;
import cn.edu.rpm.execution.application.dto.UploadDeliverableCmd;
import cn.edu.rpm.execution.domain.model.ExecutionPermissions;
import cn.edu.rpm.execution.infrastructure.entity.DeliverableEntity;
import cn.edu.rpm.execution.infrastructure.repository.DeliverableRepository;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FileStatus;
import cn.edu.rpm.file.domain.model.ScanStatus;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DeliverableService} 纯单元测试（T-EXEC-008）。
 *
 * <p>覆盖 TC-EXEC-013（PDF 交付物上传成功并记录 file_id/sha256/上传人）、
 * TC-EXEC-014（哈希不一致 RPM-EXEC-004 且不生成引用）、
 * TC-EXEC-015（无交付物返回空 items 与 total=0）。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeliverableServiceTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String MILESTONE_ID = "99999999999999999999999999999999";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PI_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DEPT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID FILE_UUID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final String SHA = "a".repeat(64);

    @Mock
    private DeliverableRepository deliverableRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private FileObjectQueryPort fileObjectQueryPort;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditRecorder auditRecorder;

    private DeliverableService service;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        service = new DeliverableService(deliverableRepository, projectRepository,
                projectMemberRepository, fileObjectQueryPort, authorizationService, auditRecorder,
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        auth = new AuthContext(ACTOR_ID, null, "E1001",
                Set.of(ExecutionPermissions.VIEW, ExecutionPermissions.DELIVERABLE_UPLOAD),
                Set.of(), NOW);
        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setPiId(UuidUtils.formatCompact(PI_ID));
        project.setDeptId(UuidUtils.formatCompact(DEPT_ID));
        project.setStatus(ProjectEntity.STATUS_EXECUTING);
        lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setProjectId(PROJECT_ID);
        member.setUserId(UuidUtils.formatCompact(ACTOR_ID));
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        lenient().when(projectMemberRepository.findActiveByProjectId(any(), any()))
                .thenReturn(List.of(member));
        lenient().when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.allow());
    }

    @Test
    void uploadPdfCreatesActiveDeliverableWithHashAndUploader() {
        when(fileObjectQueryPort.findById(FILE_UUID))
                .thenReturn(Optional.of(fileObject(SHA, FileStatus.AVAILABLE)));

        DeliverableCreated result = service.upload(PROJECT_ID,
                new UploadDeliverableCmd(UuidUtils.formatCompact(FILE_UUID), "中期检查报告.pdf",
                        MILESTONE_ID, null, SHA.toUpperCase()), auth);

        ArgumentCaptor<DeliverableEntity> captor = ArgumentCaptor.forClass(DeliverableEntity.class);
        verify(deliverableRepository).insert(captor.capture());
        DeliverableEntity saved = captor.getValue();
        assertThat(saved.getId()).hasSize(32);
        assertThat(saved.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getMilestoneId()).isEqualTo(MILESTONE_ID);
        assertThat(saved.getFileId()).isEqualTo(UuidUtils.formatCompact(FILE_UUID));
        assertThat(saved.getTitle()).isEqualTo("中期检查报告.pdf");
        assertThat(saved.getSha256()).isEqualTo(SHA.toLowerCase());
        assertThat(saved.getStatus()).isEqualTo(DeliverableEntity.STATUS_ACTIVE);
        assertThat(saved.getUploadedBy()).isEqualTo(UuidUtils.formatCompact(ACTOR_ID));

        assertThat(result.deliverableId()).isEqualTo(saved.getId());
        assertThat(result.status()).isEqualTo(DeliverableEntity.STATUS_ACTIVE);
        assertThat(result.sha256()).isEqualTo(SHA);
        assertThat(result.version()).isZero();

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action())
                .isEqualTo(DeliverableService.AUDIT_DELIVERABLE_UPLOADED);
        assertThat(auditCaptor.getValue().actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void hashMismatchThrowsExec004AndCreatesNoReference() {
        when(fileObjectQueryPort.findById(FILE_UUID))
                .thenReturn(Optional.of(fileObject("b".repeat(64), FileStatus.AVAILABLE)));

        assertThatThrownBy(() -> service.upload(PROJECT_ID,
                new UploadDeliverableCmd(UuidUtils.formatCompact(FILE_UUID), "论文.pdf",
                        null, null, SHA), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_004));

        verify(deliverableRepository, never()).insert(any());
    }

    @Test
    void unavailableFileThrowsExec004() {
        when(fileObjectQueryPort.findById(FILE_UUID))
                .thenReturn(Optional.of(fileObject(SHA, FileStatus.QUARANTINED)));

        assertThatThrownBy(() -> service.upload(PROJECT_ID,
                new UploadDeliverableCmd(UuidUtils.formatCompact(FILE_UUID), "论文.pdf",
                        null, null, SHA), auth))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXEC_004));

        verify(deliverableRepository, never()).insert(any());
    }

    @Test
    void emptyListReturnsEmptyItemsAndZeroTotal() {
        when(deliverableRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());

        PageVO<DeliverableView> page = service.list(PROJECT_ID, null, 1, 20, auth);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotal()).isZero();
        verify(authorizationService).check(any(),
                eq(cn.edu.rpm.iam.application.dto.Action.of(ExecutionPermissions.VIEW)), any());
    }

    @Test
    void listFiltersByMilestoneAndMapsViews() {
        DeliverableEntity matching = deliverable(MILESTONE_ID, SHA);
        DeliverableEntity other = deliverable("77777777777777777777777777777777", "c".repeat(64));
        when(deliverableRepository.findByProjectId(PROJECT_ID))
                .thenReturn(List.of(matching, other));

        PageVO<DeliverableView> page = service.list(PROJECT_ID, MILESTONE_ID, 1, 20, auth);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).deliverableId()).isEqualTo(matching.getId());
        assertThat(page.getItems().get(0).milestoneId()).isEqualTo(MILESTONE_ID);
        assertThat(page.getItems().get(0).sha256()).isEqualTo(SHA);
    }

    private static DeliverableEntity deliverable(String milestoneId, String sha256) {
        DeliverableEntity entity = new DeliverableEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setProjectId(PROJECT_ID);
        entity.setMilestoneId(milestoneId);
        entity.setFileId(UuidUtils.formatCompact(FILE_UUID));
        entity.setTitle("报告.pdf");
        entity.setSha256(sha256);
        entity.setStatus(DeliverableEntity.STATUS_ACTIVE);
        entity.setUploadedBy(UuidUtils.formatCompact(ACTOR_ID));
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }

    private static FileObject fileObject(String sha256, FileStatus status) {
        return FileObject.rehydrate(FILE_UUID, "rpm-files", "2026/11/report.pdf", "报告.pdf",
                "application/pdf", 1024L, sha256, status, ScanStatus.PASSED, ACTOR_ID, NOW, 0);
    }
}
