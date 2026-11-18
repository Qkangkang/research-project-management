package cn.edu.rpm.file.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FileStatus;
import cn.edu.rpm.file.domain.model.ScanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FileUploadGuard} 纯单元测试（T-FILE-004 先行测试；TC-IAM-026 / TC-IAM-027）。
 *
 * <p>断言扩展名、MIME、大小、魔数四类校验：伪造 {@code .pdf} 扩展名的可执行文件被拒绝并置
 * {@code REJECTED}（TC-IAM-026）；{@code QUARANTINED} 文件不得被业务引用（TC-IAM-027）；
 * 一期扫描占位（SKIPPED）按通过处理（决策 D-04）。</p>
 */
class FileUploadGuardTest {

    private static final UUID UPLOADER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final long MAX_SIZE = 1024L;

    private static final byte[] PDF_HEAD = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EXE_HEAD = new byte[]{'M', 'Z', (byte) 0x90, 0x00};
    private static final byte[] PNG_HEAD = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private FileUploadGuard guard;

    @BeforeEach
    void setUp() {
        guard = new FileUploadGuard(MAX_SIZE, Set.of("pdf", "png", "jpg", "txt"));
    }

    private static FileObject newFile(String name, String mime, long size) {
        return FileObject.newUpload("rpm-files", "2026/11/object-key", name, mime, size,
                "a".repeat(64), UPLOADER_ID);
    }

    @Test
    void validPdfIsQuarantinedForScanning() {
        FileObject file = newFile("申报书.pdf", "application/pdf", 512L);

        guard.validate(file, PDF_HEAD);

        assertThat(file.getStatus()).isEqualTo(FileStatus.QUARANTINED);
        assertThat(file.getScanStatus()).isEqualTo(ScanStatus.PENDING);
    }

    @Test
    void pdfExtensionWithExecutableMagicIsRejectedWithFile002() {
        FileObject file = newFile("伪造材料.pdf", "application/pdf", 512L);

        assertThatThrownBy(() -> guard.validate(file, EXE_HEAD))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FILE_002))
                .hasMessageContaining("文件内容与扩展名不匹配");
        assertThat(file.getStatus()).isEqualTo(FileStatus.REJECTED);
    }

    @Test
    void fileLargerThanConfiguredLimitIsRejected() {
        FileObject file = newFile("大文件.pdf", "application/pdf", MAX_SIZE + 1);

        assertThatThrownBy(() -> guard.validate(file, PDF_HEAD))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FILE_002))
                .hasMessageContaining("文件大小超出限制");
        assertThat(file.getStatus()).isEqualTo(FileStatus.REJECTED);
    }

    @Test
    void emptyFileIsRejected() {
        FileObject file = newFile("empty.pdf", "application/pdf", 0);

        assertThatThrownBy(() -> guard.validate(file, PDF_HEAD))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FILE_002));
        assertThat(file.getStatus()).isEqualTo(FileStatus.REJECTED);
    }

    @Test
    void filenameWithoutExtensionIsRejected() {
        FileObject file = newFile("no-extension", "application/pdf", 512L);

        assertThatThrownBy(() -> guard.validate(file, PDF_HEAD))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少扩展名");
        assertThat(file.getStatus()).isEqualTo(FileStatus.REJECTED);
    }

    @Test
    void extensionOutsideWhitelistIsRejected() {
        FileObject file = newFile("install.exe", "application/pdf", 512L);

        assertThatThrownBy(() -> guard.validate(file, PDF_HEAD))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("扩展名不在允许范围");
        assertThat(file.getStatus()).isEqualTo(FileStatus.REJECTED);
    }

    @Test
    void declaredMimeMustMatchExtension() {
        FileObject file = newFile("扫描件.pdf", "image/png", 512L);

        assertThatThrownBy(() -> guard.validate(file, PDF_HEAD))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("MIME 与扩展名不匹配");
        assertThat(file.getStatus()).isEqualTo(FileStatus.REJECTED);
    }

    @Test
    void magicHeaderShorterThanRequiredIsRejected() {
        FileObject file = newFile("broken.pdf", "application/pdf", 512L);

        assertThatThrownBy(() -> guard.validate(file, new byte[]{'%', 'P'}))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文件内容与扩展名不匹配");
        assertThat(file.getStatus()).isEqualTo(FileStatus.REJECTED);
    }

    @Test
    void extensionCheckIsCaseInsensitive() {
        FileObject file = newFile("REPORT.PDF", "application/pdf", 512L);

        guard.validate(file, PDF_HEAD);

        assertThat(file.getStatus()).isEqualTo(FileStatus.QUARANTINED);
    }

    @Test
    void pngWithMatchingMimeAndMagicPasses() {
        FileObject file = newFile("图.png", "image/png", 512L);

        guard.validate(file, PNG_HEAD);

        assertThat(file.getStatus()).isEqualTo(FileStatus.QUARANTINED);
    }

    @Test
    void requireAvailableRejectsQuarantinedFileWithFile001() {
        FileObject file = newFile("待扫描.pdf", "application/pdf", 512L);
        guard.validate(file, PDF_HEAD);

        assertThatThrownBy(() -> guard.requireAvailable(file))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FILE_001));
    }

    @Test
    void requireAvailablePassesForAvailableFile() {
        FileObject file = availableFile();

        assertThatCode(() -> guard.requireAvailable(file)).doesNotThrowAnyException();
    }

    @Test
    void scanPassedMakesFileAvailable() {
        FileObject file = newFile("申报书.pdf", "application/pdf", 512L);
        guard.validate(file, PDF_HEAD);

        guard.applyScanResult(file, ScanStatus.PASSED);

        assertThat(file.getStatus()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(file.getScanStatus()).isEqualTo(ScanStatus.PASSED);
    }

    @Test
    void scanSkippedPlaceholderAlsoMakesFileAvailable() {
        FileObject file = newFile("申报书.pdf", "application/pdf", 512L);
        guard.validate(file, PDF_HEAD);

        guard.applyScanResult(file, ScanStatus.SKIPPED);

        assertThat(file.getStatus()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(file.getScanStatus()).isEqualTo(ScanStatus.SKIPPED);
    }

    @Test
    void scanFailedKeepsFileQuarantined() {
        FileObject file = newFile("申报书.pdf", "application/pdf", 512L);
        guard.validate(file, PDF_HEAD);

        guard.applyScanResult(file, ScanStatus.FAILED);

        assertThat(file.getStatus()).isEqualTo(FileStatus.QUARANTINED);
        assertThat(file.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThatThrownBy(() -> guard.requireAvailable(file))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.FILE_001));
    }

    private static FileObject availableFile() {
        return FileObject.rehydrate(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "rpm-files", "2026/11/object-key", "申报书.pdf", "application/pdf", 512L,
                "a".repeat(64), FileStatus.AVAILABLE, ScanStatus.PASSED, UPLOADER_ID, null, 1);
    }
}
