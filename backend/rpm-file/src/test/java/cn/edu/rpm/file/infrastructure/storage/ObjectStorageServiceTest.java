package cn.edu.rpm.file.infrastructure.storage;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.file.infrastructure.config.FileProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ObjectStorageService} 纯单元测试（T-FILE-003 先行测试；TC-IAM-017 签名边界）。
 *
 * <p>断言：短期签名 TTL 精确等于 300 秒、超过上限直接拒绝（不产生任何 MinIO 调用）、
 * 上传/删除委托到配置桶、存储异常统一转 {@code RPM-COMMON-500} 且不泄露底层细节。</p>
 */
@ExtendWith(MockitoExtension.class)
class ObjectStorageServiceTest {

    private static final String BUCKET = "rpm-files";
    private static final String OBJECT_KEY = "2026/11/abcdef";
    private static final String PRESIGNED_URL = "http://minio.local/rpm-files/2026/11/abcdef?X-Amz-Signature=x";

    @Mock
    private MinioClient minioClient;

    private FileProperties properties;

    private ObjectStorageService service;

    @BeforeEach
    void setUp() {
        properties = new FileProperties();
        properties.setMaxSizeBytes(209_715_200L);
        properties.setSignatureTtl(Duration.ofSeconds(300));
        properties.getMinio().setBucket(BUCKET);
        service = new ObjectStorageService(minioClient, properties);
    }

    @Test
    void presignGetUsesConfiguredBucketObjectAndExactTtl() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(PRESIGNED_URL);

        String url = service.presignGet(OBJECT_KEY, Duration.ofSeconds(300));

        assertThat(url).isEqualTo(PRESIGNED_URL);
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        GetPresignedObjectUrlArgs args = captor.getValue();
        assertThat(args.method()).isEqualTo(Method.GET);
        assertThat(args.bucket()).isEqualTo(BUCKET);
        assertThat(args.object()).isEqualTo(OBJECT_KEY);
        assertThat(args.expiry()).isEqualTo(300);
    }

    @Test
    void presignRejectsTtlAboveFiveMinutes() {
        assertThatThrownBy(() -> service.presignGet(OBJECT_KEY, Duration.ofSeconds(301)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("签名有效期不得超过 300 秒");
        verifyNoInteractions(minioClient);
    }

    @Test
    void presignRejectsNonPositiveTtl() {
        assertThatThrownBy(() -> service.presignGet(OBJECT_KEY, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("签名有效期必须大于 0");
        verifyNoInteractions(minioClient);
    }

    @Test
    void uploadDelegatesToConfiguredBucketWithContentType() throws Exception {
        byte[] payload = "pdf-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String key = service.upload(OBJECT_KEY, new ByteArrayInputStream(payload), payload.length,
                "application/pdf");

        assertThat(key).isEqualTo(OBJECT_KEY);
        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        PutObjectArgs args = captor.getValue();
        assertThat(args.bucket()).isEqualTo(BUCKET);
        assertThat(args.object()).isEqualTo(OBJECT_KEY);
        assertThat(args.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void uploadDefaultsMissingContentTypeToOctetStream() throws Exception {
        byte[] payload = new byte[]{1, 2, 3};

        service.upload(OBJECT_KEY, new ByteArrayInputStream(payload), payload.length, null);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void uploadWrapsStorageFailureIntoBizException() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new IOException("minio down"));

        assertThatThrownBy(() -> service.upload(OBJECT_KEY, new ByteArrayInputStream(new byte[]{1}), 1L, "text/plain"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_500))
                .hasMessageContaining("对象存储上传失败");
    }

    @Test
    void downloadWrapsStorageFailureIntoBizException() throws Exception {
        when(minioClient.getObject(any(io.minio.GetObjectArgs.class))).thenThrow(new IOException("minio down"));

        assertThatThrownBy(() -> service.download(OBJECT_KEY))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_500))
                .hasMessageContaining("对象存储下载失败");
    }

    @Test
    void deleteDelegatesToConfiguredBucket() throws Exception {
        service.delete(OBJECT_KEY);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void deleteWrapsStorageFailureIntoBizException() throws Exception {
        org.mockito.Mockito.doThrow(new IOException("minio down"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> service.delete(OBJECT_KEY))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.COMMON_500))
                .hasMessageContaining("对象存储删除失败");
    }
}
