package cn.edu.rpm.file.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.file.domain.model.FileAccessLevel;
import cn.edu.rpm.file.domain.model.FileDownloadGrant;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FileRef;
import cn.edu.rpm.file.domain.model.FileStatus;
import cn.edu.rpm.file.domain.model.ScanStatus;
import cn.edu.rpm.file.domain.port.FileGrantPort;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.file.domain.port.FileRefQueryPort;
import cn.edu.rpm.file.domain.port.ObjectStoragePort;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link FileAccessService} 纯单元测试（T-FILE-005 先行测试；
 * TC-IAM-016 / TC-IAM-017 / TC-IAM-018 / TC-FILE-105～107）。
 *
 * <p>断言：无业务权限拒绝且不泄露对象存储地址（TC-IAM-016）；签名 300 秒，
 * 第 301 秒失效（TC-IAM-017）；撤销授权后旧签名立即拒绝（TC-IAM-018）；
 * 签名只签发给被授权用户；孤立文件仅上传人可访问。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileAccessServiceTest {

    private static final UUID FILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UPLOADER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PROJECT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BIZ_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant T0 = Instant.parse("2026-11-17T06:00:00Z");
    private static final String PRESIGNED_URL = "http://minio.local/rpm-files/key?signature=x";
    private static final Duration TTL = Duration.ofSeconds(300);

    @Mock
    private FileObjectQueryPort fileObjectQueryPort;

    @Mock
    private FileRefQueryPort fileRefQueryPort;

    @Mock
    private FileGrantPort fileGrantPort;

    @Mock
    private ObjectStoragePort objectStoragePort;

    @Mock
    private AuthorizationService authorizationService;

    @Captor
    private ArgumentCaptor<FileDownloadGrant> grantCaptor;

    private FileAccessService service;

    @BeforeEach
    void setUp() {
        service = new FileAccessService(fileObjectQueryPort, fileRefQueryPort, fileGrantPort,
                objectStoragePort, authorizationService, TTL, Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static AuthContext ctx(UUID userId) {
        return new AuthContext(userId, null, "EMP-0001", Set.of("project:view"), Set.of(), T0);
    }

    private static ResourceRef projectRef() {
        return new ResourceRef(ResourceTypes.PROJECT, PROJECT_ID, UPLOADER_ID, null, Set.of(UPLOADER_ID));
    }

    private static FileObject file(FileStatus status, UUID uploader) {
        return FileObject.rehydrate(FILE_ID, "rpm-files", "2026/11/key", "申报书.pdf", "application/pdf",
                2048L, "a".repeat(64), status, ScanStatus.PASSED, uploader, T0.minusSeconds(3600), 3);
    }

    private static FileRef ref() {
        return new FileRef(UUID.fromString("66666666-6666-6666-6666-666666666666"), FILE_ID,
                "APPLICATION", BIZ_ID, "APPLICATION_ATTACHMENT", FileAccessLevel.PROJECT, UPLOADER_ID, T0);
    }

    @Test
    void authorizeDeniesWhenBusinessPermissionMissingWithoutLeakingStorageUrl() {
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.of(file(FileStatus.AVAILABLE, UPLOADER_ID)));
        when(fileRefQueryPort.findByFileId(FILE_ID)).thenReturn(List.of(ref()));
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.authorize(FILE_ID, OTHER_USER_ID, projectRef(), ctx(OTHER_USER_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
        verifyNoInteractions(objectStoragePort);
        verify(fileGrantPort, never()).save(any());
    }

    @Test
    void authorizeDeniesMissingFileWithoutRevealingExistence() {
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(FILE_ID, OTHER_USER_ID, projectRef(), ctx(OTHER_USER_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
        verifyNoInteractions(objectStoragePort);
    }

    @Test
    void authorizeDeniesQuarantinedFileEvenForUploader() {
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.of(file(FileStatus.QUARANTINED, UPLOADER_ID)));

        assertThatThrownBy(() -> service.authorize(FILE_ID, UPLOADER_ID, null, ctx(UPLOADER_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
        verifyNoInteractions(objectStoragePort);
    }

    @Test
    void authorizeDeniesOrphanFileForNonUploader() {
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.of(file(FileStatus.AVAILABLE, UPLOADER_ID)));
        when(fileRefQueryPort.findByFileId(FILE_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.authorize(FILE_ID, OTHER_USER_ID, projectRef(), ctx(OTHER_USER_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
        verifyNoInteractions(objectStoragePort, authorizationService);
    }

    @Test
    void authorizeAllowsUploaderOfOrphanFileAndIssuesGrantWithExactTtl() {
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.of(file(FileStatus.AVAILABLE, UPLOADER_ID)));
        when(fileRefQueryPort.findByFileId(FILE_ID)).thenReturn(List.of());
        when(objectStoragePort.presignGet("2026/11/key", TTL)).thenReturn(PRESIGNED_URL);

        var grant = service.authorize(FILE_ID, UPLOADER_ID, null, ctx(UPLOADER_ID));

        assertThat(grant.token()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(grant.downloadUrl()).isEqualTo(PRESIGNED_URL);
        assertThat(grant.expiresAt()).isEqualTo(T0.plusSeconds(300));
        verify(fileGrantPort).save(grantCaptor.capture());
        FileDownloadGrant saved = grantCaptor.getValue();
        assertThat(saved.getFileId()).isEqualTo(FILE_ID);
        assertThat(saved.getGrantedTo()).isEqualTo(UPLOADER_ID);
        assertThat(saved.getExpiresAt()).isEqualTo(T0.plusSeconds(300));
        assertThat(saved.isUsableAt(T0.plusSeconds(299))).isTrue();
        verify(authorizationService, never()).check(any(), any(), any());
    }

    @Test
    void authorizeUsesProjectViewPermissionForReferencedFile() {
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.of(file(FileStatus.AVAILABLE, UPLOADER_ID)));
        when(fileRefQueryPort.findByFileId(FILE_ID)).thenReturn(List.of(ref()));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());
        when(objectStoragePort.presignGet(anyString(), eq(TTL))).thenReturn(PRESIGNED_URL);

        service.authorize(FILE_ID, OTHER_USER_ID, projectRef(), ctx(OTHER_USER_ID));

        ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
        ArgumentCaptor<ResourceRef> resourceCaptor = ArgumentCaptor.forClass(ResourceRef.class);
        verify(authorizationService).check(any(), actionCaptor.capture(), resourceCaptor.capture());
        assertThat(actionCaptor.getValue().code()).isEqualTo("project:view");
        assertThat(resourceCaptor.getValue().resourceId()).isEqualTo(PROJECT_ID);
    }

    @Test
    void authorizeStillAllowsUploaderOfReferencedFileWithoutResourceContext() {
        // 修复回归用例：文件已被业务引用（refs 非空）且调用方未提供资源上下文时，
        // 上传人本人仍可下载——绑定不剥夺其既有访问，避免业务接入 file_ref 后回退性 403
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.of(file(FileStatus.AVAILABLE, UPLOADER_ID)));
        when(fileRefQueryPort.findByFileId(FILE_ID)).thenReturn(List.of(ref()));
        when(objectStoragePort.presignGet(anyString(), eq(TTL))).thenReturn(PRESIGNED_URL);

        var grant = service.authorize(FILE_ID, UPLOADER_ID, null, ctx(UPLOADER_ID));

        assertThat(grant.downloadUrl()).isEqualTo(PRESIGNED_URL);
        verify(authorizationService, never()).check(any(), any(), any());
    }

    @Test
    void authorizeDeniesNonUploaderOfReferencedFileWithoutResourceContext() {
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.of(file(FileStatus.AVAILABLE, UPLOADER_ID)));
        when(fileRefQueryPort.findByFileId(FILE_ID)).thenReturn(List.of(ref()));

        assertThatThrownBy(() -> service.authorize(FILE_ID, OTHER_USER_ID, null, ctx(OTHER_USER_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
        verifyNoInteractions(objectStoragePort, authorizationService);
    }

    @Test
    void authorizeDeniesWhenUserIdDoesNotMatchAuthContext() {
        assertThatThrownBy(() -> service.authorize(FILE_ID, OTHER_USER_ID, projectRef(), ctx(UPLOADER_ID)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
        verifyNoInteractions(fileObjectQueryPort);
    }

    @Test
    void validateRejectsGrantAtSecond301() {
        FileDownloadGrant grant = FileDownloadGrant.issue(FILE_ID, UPLOADER_ID, T0, T0.plusSeconds(300));
        when(fileGrantPort.findByToken(grant.getToken())).thenReturn(Optional.of(grant));

        FileAccessService lateService = new FileAccessService(fileObjectQueryPort, fileRefQueryPort,
                fileGrantPort, objectStoragePort, authorizationService, TTL,
                Clock.fixed(T0.plusSeconds(301), ZoneOffset.UTC));

        assertThatThrownBy(() -> lateService.validate(grant.getToken(), ctx(UPLOADER_ID), null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
        verify(fileObjectQueryPort, never()).findById(any());
    }

    @Test
    void validateRejectsGrantExactlyAtExpiry() {
        FileDownloadGrant grant = FileDownloadGrant.issue(FILE_ID, UPLOADER_ID, T0, T0.plusSeconds(300));
        when(fileGrantPort.findByToken(grant.getToken())).thenReturn(Optional.of(grant));

        FileAccessService boundaryService = new FileAccessService(fileObjectQueryPort, fileRefQueryPort,
                fileGrantPort, objectStoragePort, authorizationService, TTL,
                Clock.fixed(T0.plusSeconds(300), ZoneOffset.UTC));

        assertThatThrownBy(() -> boundaryService.validate(grant.getToken(), ctx(UPLOADER_ID), null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validateRejectsRevokedGrantImmediately() {
        FileDownloadGrant grant = FileDownloadGrant.issue(FILE_ID, UPLOADER_ID, T0, T0.plusSeconds(300));
        grant.revoke(T0.plusSeconds(10));
        when(fileGrantPort.findByToken(grant.getToken())).thenReturn(Optional.of(grant));
        FileAccessService later = new FileAccessService(fileObjectQueryPort, fileRefQueryPort,
                fileGrantPort, objectStoragePort, authorizationService, TTL,
                Clock.fixed(T0.plusSeconds(20), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.validate(grant.getToken(), ctx(UPLOADER_ID), null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
    }

    @Test
    void validateRechecksBusinessPermissionOnEveryDownload() {
        FileDownloadGrant grant = FileDownloadGrant.issue(FILE_ID, OTHER_USER_ID, T0, T0.plusSeconds(300));
        when(fileGrantPort.findByToken(grant.getToken())).thenReturn(Optional.of(grant));
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.of(file(FileStatus.AVAILABLE, UPLOADER_ID)));
        when(fileRefQueryPort.findByFileId(FILE_ID)).thenReturn(List.of(ref()));
        when(authorizationService.check(any(), any(), any()))
                .thenReturn(AuthDecision.deny(ErrorCode.COMMON_403));

        assertThatThrownBy(() -> service.validate(grant.getToken(), ctx(OTHER_USER_ID), projectRef()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_403));
    }

    @Test
    void validateReturnsTicketForAuthorizedUser() {
        FileDownloadGrant grant = FileDownloadGrant.issue(FILE_ID, OTHER_USER_ID, T0, T0.plusSeconds(300));
        when(fileGrantPort.findByToken(grant.getToken())).thenReturn(Optional.of(grant));
        when(fileObjectQueryPort.findById(FILE_ID)).thenReturn(Optional.of(file(FileStatus.AVAILABLE, UPLOADER_ID)));
        when(fileRefQueryPort.findByFileId(FILE_ID)).thenReturn(List.of(ref()));
        when(authorizationService.check(any(), any(), any())).thenReturn(AuthDecision.allow());

        var ticket = service.validate(grant.getToken(), ctx(OTHER_USER_ID), projectRef());

        assertThat(ticket.fileId()).isEqualTo(FILE_ID);
        assertThat(ticket.objectKey()).isEqualTo("2026/11/key");
        assertThat(ticket.expiresAt()).isEqualTo(T0.plusSeconds(300));
    }

    @Test
    void validateRejectsGrantIssuedToAnotherUser() {
        FileDownloadGrant grant = FileDownloadGrant.issue(FILE_ID, OTHER_USER_ID, T0, T0.plusSeconds(300));
        when(fileGrantPort.findByToken(grant.getToken())).thenReturn(Optional.of(grant));

        assertThatThrownBy(() -> service.validate(grant.getToken(), ctx(UPLOADER_ID), projectRef()))
                .isInstanceOf(BizException.class);
        verify(fileObjectQueryPort, never()).findById(any());
    }

    @Test
    void revokeForFileDelegatesBatchRevokeAtCurrentTime() {
        when(fileGrantPort.revokeAllForFile(FILE_ID, T0)).thenReturn(2);

        assertThat(service.revokeForFile(FILE_ID)).isEqualTo(2);
    }

    @Test
    void constructorRejectsSignatureTtlAboveFiveMinutes() {
        assertThatThrownBy(() -> new FileAccessService(fileObjectQueryPort, fileRefQueryPort,
                fileGrantPort, objectStoragePort, authorizationService, Duration.ofSeconds(301),
                Clock.fixed(T0, ZoneOffset.UTC)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得超过 300 秒");
    }
}
