package cn.edu.rpm.boot.file;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.ScanStatus;
import cn.edu.rpm.file.domain.service.FileAccessService;
import cn.edu.rpm.file.domain.service.FileUploadGuard;
import cn.edu.rpm.file.infrastructure.config.FileProperties;
import cn.edu.rpm.file.infrastructure.repository.FileObjectRepository;
import cn.edu.rpm.file.infrastructure.storage.ObjectStorageService;
import cn.edu.rpm.iam.application.dto.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * 本地文件上传编排：校验、对象存储写入、元数据落库与一期扫描占位。
 *
 * <p>对象存储是外部调用，按项目硬规则不包在数据库事务中。</p>
 */
@Service
public class FileUploadAppService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileUploadGuard uploadGuard;

    private final FileProperties properties;

    private final FileObjectRepository fileObjectRepository;

    private final ObjectStorageService objectStorageService;

    private final FileAccessService fileAccessService;

    public FileUploadAppService(FileUploadGuard uploadGuard,
                                FileProperties properties,
                                FileObjectRepository fileObjectRepository,
                                ObjectStorageService objectStorageService,
                                FileAccessService fileAccessService) {
        this.uploadGuard = Objects.requireNonNull(uploadGuard, "uploadGuard 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.fileObjectRepository = Objects.requireNonNull(fileObjectRepository,
                "fileObjectRepository 不能为空");
        this.objectStorageService = Objects.requireNonNull(objectStorageService,
                "objectStorageService 不能为空");
        this.fileAccessService = Objects.requireNonNull(fileAccessService,
                "fileAccessService 不能为空");
    }

    /**
     * 上传文件并转为可用状态。
     */
    public UploadedFile upload(MultipartFile multipartFile, AuthContext auth) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_400, "上传文件不能为空");
        }
        String originalName = cleanFileName(multipartFile.getOriginalFilename());
        String mimeType = multipartFile.getContentType();
        byte[] content;
        try {
            content = multipartFile.getBytes();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.COMMON_500, "读取上传文件失败");
        }

        String objectKey = objectKey(originalName);
        FileObject file = FileObject.newUpload(
                properties.getMinio().getBucket(),
                objectKey,
                originalName,
                mimeType,
                content.length,
                sha256(content),
                auth.userId());
        uploadGuard.validate(file, head(content));

        boolean uploaded = false;
        try {
            objectStorageService.upload(objectKey, new ByteArrayInputStream(content),
                    content.length, mimeType);
            uploaded = true;
            String fileId = fileObjectRepository.insert(file);
            FileObject persisted = fileObjectRepository.findById(UuidUtils.parse(fileId))
                    .orElseThrow(() -> new BizException(ErrorCode.COMMON_500, "文件元数据保存失败"));
            uploadGuard.applyScanResult(persisted, ScanStatus.SKIPPED);
            if (fileObjectRepository.update(persisted) == 0) {
                throw new BizException(ErrorCode.COMMON_500, "文件状态更新失败");
            }
            return new UploadedFile(fileId, originalName, content.length, persisted.getSha256());
        } catch (RuntimeException ex) {
            if (uploaded) {
                try {
                    objectStorageService.delete(objectKey);
                } catch (RuntimeException ignored) {
                    // 保留原始异常，避免清理失败掩盖上传错误。
                }
            }
            throw ex;
        }
    }

    /**
     * 为上传人或可见业务资源申请短期下载 URL。
     */
    public DownloadUrl downloadUrl(String fileId, AuthContext auth) {
        UUID id;
        try {
            id = UuidUtils.parse(fileId);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, "fileId 非法");
        }
        var grant = fileAccessService.authorize(id, auth.userId(), null, auth);
        return new DownloadUrl(grant.token(), grant.downloadUrl(), grant.expiresAt());
    }

    private static String cleanFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "文件名不能为空");
        }
        String normalized = originalName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (name.isBlank() || name.length() > 255) {
            throw new BizException(ErrorCode.COMMON_400, "文件名非法");
        }
        return name;
    }

    private static String objectKey(String originalName) {
        int dot = originalName.lastIndexOf('.');
        String suffix = dot >= 0 ? originalName.substring(dot) : "";
        String datePath = DATE_PATH.format(java.time.LocalDate.now(ZoneId.systemDefault()));
        return datePath + "/" + UUID.randomUUID().toString().replace("-", "") + suffix;
    }

    private static byte[] head(byte[] content) {
        int size = Math.min(content.length, 16);
        byte[] head = new byte[size];
        System.arraycopy(content, 0, head, 0, size);
        return head;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.COMMON_500, "计算文件摘要失败");
        }
    }

    /**
     * 上传结果。
     */
    public record UploadedFile(String fileId, String originalName, long sizeBytes, String sha256) {
    }

    /**
     * 短期下载 URL。
     */
    public record DownloadUrl(String token, String downloadUrl, Instant expiresAt) {
    }
}
