package cn.edu.rpm.file.domain.port;

import java.time.Duration;

/**
 * 对象存储端口（T-FILE-003 / T-FILE-005）。
 *
 * <p>领域层只依赖"生成短期下载签名"这一能力，由 {@code ObjectStorageService} 适配
 * MinIO/S3；上传、删除等生命周期操作由文件应用服务直接使用基础设施实现。</p>
 */
public interface ObjectStoragePort {

    /**
     * 生成对象的短期 GET 签名 URL。
     *
     * @param objectKey 对象键
     * @param ttl       有效期（不得超过配置上限，技术文档 08 §4.2 要求 ≤5 分钟）
     * @return 签名 URL
     */
    String presignGet(String objectKey, Duration ttl);
}
