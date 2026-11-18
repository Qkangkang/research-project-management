package cn.edu.rpm.file.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 文件模块配置（T-FILE-003 / T-FILE-004；前缀 {@code rpm.file}）。
 *
 * <p>配置项全部外置到 {@code application*.yml}（禁止在业务代码写死）：
 * </p>
 * <ul>
 *   <li>{@code max-size-bytes}：单文件大小上限，默认 200MB（技术文档 06 §6.1）；</li>
 *   <li>{@code signature-ttl}：下载签名上限，默认 300 秒（技术文档 08 §4.2）；</li>
 *   <li>{@code allowed-extensions}：允许上传的扩展名白名单；</li>
 *   <li>{@code minio.*}：对象存储连接与桶。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "rpm.file")
public class FileProperties {

    /** 单文件大小上限（字节），默认 200MB。 */
    private long maxSizeBytes = 209_715_200L;

    /** 下载签名有效期上限，默认 300 秒；业务不得申请超过该值的签名。 */
    private Duration signatureTtl = Duration.ofSeconds(300);

    /** 允许上传的扩展名（小写、不含点）。 */
    private Set<String> allowedExtensions = new LinkedHashSet<>(Set.of(
            "pdf", "png", "jpg", "jpeg", "doc", "docx", "xls", "xlsx",
            "ppt", "pptx", "zip", "txt", "csv"));

    /** 对象存储（MinIO/S3 兼容）配置。 */
    private Minio minio = new Minio();

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public Duration getSignatureTtl() {
        return signatureTtl;
    }

    public void setSignatureTtl(Duration signatureTtl) {
        this.signatureTtl = signatureTtl;
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(Set<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public Minio getMinio() {
        return minio;
    }

    public void setMinio(Minio minio) {
        this.minio = minio;
    }

    /**
     * 对象存储连接配置。
     */
    public static class Minio {

        /** 服务端点（如 {@code http://minio:9000}）。 */
        private String endpoint;

        /** 访问密钥。 */
        private String accessKey;

        /** 密钥。 */
        private String secretKey;

        /** 文件桶。 */
        private String bucket;

        /** 连接超时，默认 3 秒（C-CHECK：外部调用必须有超时）。 */
        private Duration connectTimeout = Duration.ofSeconds(3);

        /** 读取超时，默认 30 秒。 */
        private Duration readTimeout = Duration.ofSeconds(30);

        /** 写入超时，默认 30 秒。 */
        private Duration writeTimeout = Duration.ofSeconds(30);

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Duration getWriteTimeout() {
            return writeTimeout;
        }

        public void setWriteTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
        }
    }
}
