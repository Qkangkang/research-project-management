package cn.edu.rpm.file.infrastructure.storage;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.file.domain.port.ObjectStoragePort;
import cn.edu.rpm.file.infrastructure.config.FileProperties;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;

/**
 * 对象存储基础服务（T-FILE-003；技术文档 06 §4、08 §4.2）。
 *
 * <p>封装 MinIO/S3 的上传、下载、删除与短期签名：</p>
 * <ul>
 *   <li>签名 TTL 必须为正且不超过 {@code rpm.file.signature-ttl}（默认 300 秒）；</li>
 *   <li>所有方法不吞异常：底层失败记录日志后统一转 {@code RPM-COMMON-500}，
 *       不向前端泄露端点/桶等内部细节；</li>
 *   <li>数据库只存元数据，文件本体不经过数据库。</li>
 * </ul>
 */
public class ObjectStorageService implements ObjectStoragePort {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectStorageService.class);

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MinioClient minioClient;

    private final String bucket;

    private final Duration maxSignatureTtl;

    public ObjectStorageService(MinioClient minioClient, FileProperties properties) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient 不能为空");
        Objects.requireNonNull(properties, "文件配置不能为空");
        this.bucket = requireText(properties.getMinio().getBucket(), "bucket");
        this.maxSignatureTtl = Objects.requireNonNull(properties.getSignatureTtl(), "signatureTtl 不能为空");
    }

    /**
     * 上传对象。
     *
     * @param objectKey   对象键
     * @param stream      输入流（由调用方负责关闭）
     * @param sizeBytes   对象字节数
     * @param contentType MIME；为空时使用 {@code application/octet-stream}
     * @return 对象键
     * @throws BizException 存储失败 {@code RPM-COMMON-500}
     */
    public String upload(String objectKey, InputStream stream, long sizeBytes, String contentType) {
        requireText(objectKey, "objectKey");
        Objects.requireNonNull(stream, "stream 不能为空");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes 不能为负数");
        }
        String resolvedContentType = contentType == null || contentType.isBlank()
                ? DEFAULT_CONTENT_TYPE : contentType;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, sizeBytes, -1)
                    .contentType(resolvedContentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            LOG.error("对象存储上传失败 bucket={} object={} size={}", bucket, objectKey, sizeBytes, e);
            throw new BizException(ErrorCode.COMMON_500, "对象存储上传失败");
        }
    }

    /**
     * 下载对象。
     *
     * @param objectKey 对象键
     * @return 对象输入流（由调用方关闭）
     * @throws BizException 存储失败 {@code RPM-COMMON-500}
     */
    public InputStream download(String objectKey) {
        requireText(objectKey, "objectKey");
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            LOG.error("对象存储下载失败 bucket={} object={}", bucket, objectKey, e);
            throw new BizException(ErrorCode.COMMON_500, "对象存储下载失败");
        }
    }

    /**
     * 删除对象。
     *
     * @param objectKey 对象键
     * @throws BizException 存储失败 {@code RPM-COMMON-500}
     */
    public void delete(String objectKey) {
        requireText(objectKey, "objectKey");
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            LOG.error("对象存储删除失败 bucket={} object={}", bucket, objectKey, e);
            throw new BizException(ErrorCode.COMMON_500, "对象存储删除失败");
        }
    }

    /**
     * 生成短期 GET 签名 URL（TTL ≤ 配置上限，默认 300 秒）。
     *
     * @param objectKey 对象键
     * @param ttl       有效期
     * @return 签名 URL
     * @throws IllegalArgumentException TTL 非正或超过上限
     * @throws BizException             签名生成失败 {@code RPM-COMMON-500}
     */
    @Override
    public String presignGet(String objectKey, Duration ttl) {
        requireText(objectKey, "objectKey");
        Objects.requireNonNull(ttl, "签名有效期不能为空");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("签名有效期必须大于 0");
        }
        if (ttl.compareTo(maxSignatureTtl) > 0) {
            throw new IllegalArgumentException("签名有效期不得超过 " + maxSignatureTtl.toSeconds() + " 秒");
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) ttl.toSeconds())
                    .build());
        } catch (Exception e) {
            LOG.error("对象存储签名失败 bucket={} object={} ttl={}", bucket, objectKey, ttl, e);
            throw new BizException(ErrorCode.COMMON_500, "对象存储签名失败");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
