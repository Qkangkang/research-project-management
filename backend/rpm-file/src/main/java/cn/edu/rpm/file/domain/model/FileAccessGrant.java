package cn.edu.rpm.file.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 文件访问授权结果（T-FILE-005）。
 *
 * <p>仅授权成功时返回；拒绝时抛 {@code RPM-COMMON-403} 且不返回任何对象存储地址
 * （TC-IAM-016）。{@code token} 为应用层短期签名，{@code downloadUrl} 为对象存储
 * 预签名 URL（≤5 分钟）。</p>
 *
 * @param token       短期签名 token
 * @param downloadUrl 对象存储预签名 URL
 * @param expiresAt   失效时刻
 */
public record FileAccessGrant(String token, String downloadUrl, Instant expiresAt) {

    /** 校验字段完整性。 */
    public FileAccessGrant {
        Objects.requireNonNull(token, "token 不能为空");
        Objects.requireNonNull(downloadUrl, "downloadUrl 不能为空");
        Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
    }
}
