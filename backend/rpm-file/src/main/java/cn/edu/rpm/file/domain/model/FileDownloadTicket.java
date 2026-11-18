package cn.edu.rpm.file.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 下载凭据校验结果（T-FILE-005）。
 *
 * <p>签名可用且业务权限复核通过后返回，供文件应用服务流式响应或再次重定向；
 * 不包含对象存储签名的真实地址（避免日志/页面泄露）。</p>
 *
 * @param fileId    文件 ID
 * @param objectKey 对象键
 * @param expiresAt 签名失效时刻
 */
public record FileDownloadTicket(UUID fileId, String objectKey, Instant expiresAt) {

    /** 校验字段完整性。 */
    public FileDownloadTicket {
        Objects.requireNonNull(fileId, "fileId 不能为空");
        Objects.requireNonNull(objectKey, "objectKey 不能为空");
        Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
    }
}
