package cn.edu.rpm.file.domain.model;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件对象聚合根（T-FILE-001 / T-FILE-004；技术文档 02 §3.6、08 §4）。
 *
 * <p>承载文件生命周期不变量：只有 {@link FileStatus#AVAILABLE} 的文件允许业务引用与下载；
 * 状态迁移必须通过本类显式方法（禁止裸改状态），非法迁移抛 {@link IllegalStateException}。</p>
 */
public class FileObject {

    private final UUID id;

    private final String bucket;

    private final String objectKey;

    private final String originalName;

    private final String mimeType;

    private final long sizeBytes;

    private final String sha256;

    private final UUID uploadedBy;

    private final Instant createdAt;

    private final int version;

    private FileStatus status;

    private ScanStatus scanStatus;

    private FileObject(UUID id, String bucket, String objectKey, String originalName, String mimeType,
                       long sizeBytes, String sha256, FileStatus status, ScanStatus scanStatus,
                       UUID uploadedBy, Instant createdAt, int version) {
        this.id = id;
        this.bucket = requireText(bucket, "bucket");
        this.objectKey = requireText(objectKey, "objectKey");
        this.originalName = requireText(originalName, "originalName");
        this.mimeType = requireText(mimeType, "mimeType");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes 不能为负数");
        }
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.status = Objects.requireNonNull(status, "status 不能为空");
        this.scanStatus = Objects.requireNonNull(scanStatus, "scanStatus 不能为空");
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
        this.version = version;
    }

    /**
     * 创建新上传文件对象（状态 {@code UPLOADING}，扫描状态 {@code PENDING}）。
     *
     * @param bucket       对象存储桶
     * @param objectKey    对象键（不可猜测）
     * @param originalName 原始文件名
     * @param mimeType     声明的 MIME
     * @param sizeBytes    字节数
     * @param sha256       内容 SHA-256（可空，上传完成后计算）
     * @param uploadedBy   上传人用户 ID
     * @return 新文件对象（尚无数据库主键）
     */
    public static FileObject newUpload(String bucket, String objectKey, String originalName,
                                       String mimeType, long sizeBytes, String sha256, UUID uploadedBy) {
        return new FileObject(null, bucket, objectKey, originalName, mimeType, sizeBytes, sha256,
                FileStatus.UPLOADING, ScanStatus.PENDING, uploadedBy, null, 0);
    }

    /**
     * 从持久化数据重建文件对象（仓储层专用）。
     *
     * @param id           主键
     * @param bucket       桶
     * @param objectKey    对象键
     * @param originalName 文件名
     * @param mimeType     MIME
     * @param sizeBytes    字节数
     * @param sha256       内容摘要
     * @param status       文件状态
     * @param scanStatus   扫描状态
     * @param uploadedBy   上传人
     * @param createdAt    创建时间
     * @param version      乐观锁版本
     * @return 重建的文件对象
     */
    public static FileObject rehydrate(UUID id, String bucket, String objectKey, String originalName,
                                       String mimeType, long sizeBytes, String sha256, FileStatus status,
                                       ScanStatus scanStatus, UUID uploadedBy, Instant createdAt, int version) {
        return new FileObject(Objects.requireNonNull(id, "id 不能为空"), bucket, objectKey, originalName,
                mimeType, sizeBytes, sha256, status, scanStatus, uploadedBy, createdAt, version);
    }

    /**
     * 上传完成，进入隔离待扫描状态（重置扫描状态为 {@code PENDING}）。
     *
     * @throws IllegalStateException 当前状态不是 UPLOADING/QUARANTINED
     */
    public void markQuarantined() {
        requireStatus(FileStatus.UPLOADING, FileStatus.QUARANTINED);
        this.status = FileStatus.QUARANTINED;
        this.scanStatus = ScanStatus.PENDING;
    }

    /**
     * 静态校验失败，标记为拒绝（TC-IAM-026）。
     *
     * @throws IllegalStateException 当前状态不是 UPLOADING/QUARANTINED
     */
    public void markRejected() {
        requireStatus(FileStatus.UPLOADING, FileStatus.QUARANTINED);
        this.status = FileStatus.REJECTED;
    }

    /**
     * 应用病毒扫描结果。
     *
     * <p>{@code PASSED}/{@code SKIPPED} → {@code AVAILABLE}；{@code FAILED} → 保持
     * {@code QUARANTINED}（技术文档 08 §4.2：未通过置 QUARANTINED 并通知上传人）。</p>
     *
     * @param result 扫描结果
     * @throws IllegalStateException    当前状态不是 QUARANTINED
     * @throws IllegalArgumentException 结果为 PENDING（未完成扫描不得应用）
     */
    public void applyScanResult(ScanStatus result) {
        Objects.requireNonNull(result, "扫描结果不能为空");
        if (result == ScanStatus.PENDING) {
            throw new IllegalArgumentException("PENDING 不是可应用的扫描结论");
        }
        requireStatus(FileStatus.QUARANTINED);
        this.scanStatus = result;
        if (result == ScanStatus.FAILED) {
            this.status = FileStatus.QUARANTINED;
            return;
        }
        this.status = FileStatus.AVAILABLE;
    }

    /**
     * 要求文件可用（业务绑定附件前调用）。
     *
     * @throws BizException 文件未就绪 {@code RPM-FILE-001}（TC-IAM-027）
     */
    public void requireAvailable() {
        if (status != FileStatus.AVAILABLE) {
            throw new BizException(ErrorCode.FILE_001);
        }
    }

    /**
     * 逻辑删除（置 {@code DELETED}；幂等）。
     */
    public void markDeleted() {
        if (status != FileStatus.DELETED) {
            this.status = FileStatus.DELETED;
        }
    }

    /**
     * 是否可用（业务可引用/可下载）。
     *
     * @return 状态为 AVAILABLE 返回 {@code true}
     */
    public boolean isAvailable() {
        return status == FileStatus.AVAILABLE;
    }

    private void requireStatus(FileStatus... allowed) {
        for (FileStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("非法文件状态迁移：当前 " + status + "，允许 " + java.util.Arrays.toString(allowed));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getBucket() {
        return bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getVersion() {
        return version;
    }

    public FileStatus getStatus() {
        return status;
    }

    public ScanStatus getScanStatus() {
        return scanStatus;
    }
}
