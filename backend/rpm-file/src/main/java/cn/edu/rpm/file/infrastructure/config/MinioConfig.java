package cn.edu.rpm.file.infrastructure.config;

import cn.edu.rpm.file.infrastructure.storage.ObjectStorageService;
import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 客户端装配（T-FILE-003）。
 *
 * <p>启动期校验端点/凭据/桶齐全（生产缺失即 fail-fast，密钥不落代码库）。
 * HTTP 客户端显式设置连接、读取、写入超时（RULE-H1/C-CHECK），禁止无限等待；
 * 不在此处创建桶，桶由部署脚本或平台预先创建。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileProperties.class)
public class MinioConfig {

    /**
     * MinIO 客户端。
     *
     * @param properties 文件模块配置
     * @return 配置好凭据与超时的客户端
     */
    @Bean
    public MinioClient minioClient(FileProperties properties) {
        FileProperties.Minio minio = properties.getMinio();
        Objects.requireNonNull(minio, "rpm.file.minio 配置不能为空");
        requireText(minio.getEndpoint(), "rpm.file.minio.endpoint");
        requireText(minio.getAccessKey(), "rpm.file.minio.access-key");
        requireText(minio.getSecretKey(), "rpm.file.minio.secret-key");
        requireText(minio.getBucket(), "rpm.file.minio.bucket");

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(minio.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(minio.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(minio.getWriteTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .httpClient(httpClient)
                .build();
    }

    /**
     * 对象存储基础服务（上传/下载/删除/短期签名）。
     *
     * @param minioClient MinIO 客户端
     * @param properties  文件模块配置
     * @return 对象存储服务
     */
    @Bean
    public ObjectStorageService objectStorageService(MinioClient minioClient, FileProperties properties) {
        return new ObjectStorageService(minioClient, properties);
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少必需配置 " + key);
        }
    }
}
