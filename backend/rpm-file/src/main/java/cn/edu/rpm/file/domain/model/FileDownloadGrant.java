package cn.edu.rpm.file.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 短期下载签名记录（T-FILE-005；V06 {@code file_download_grant}）。
 *
 * <p>签名有效期 ≤ 5 分钟；业务撤销授权时批量置 {@code revoked_at}，校验时拒绝已撤销 token
 * （技术文档 08 §4.2）。token 为 32 位小写十六进制随机值，不可猜测。</p>
 */
public class FileDownloadGrant {

    private final UUID id;

    private final UUID fileId;

    private final UUID grantedTo;

    private final String token;

    private final Instant createdAt;

    private final Instant expiresAt;

    private Instant revokedAt;

    private FileDownloadGrant(UUID id, UUID fileId, UUID grantedTo, String token,
                              Instant createdAt, Instant expiresAt, Instant revokedAt) {
        this.id = id;
        this.fileId = Objects.requireNonNull(fileId, "fileId 不能为空");
        this.grantedTo = Objects.requireNonNull(grantedTo, "grantedTo 不能为空");
        this.token = requireText(token, "token");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 createdAt");
        }
        this.revokedAt = revokedAt;
    }

    /**
     * 签发新的下载签名。
     *
     * @param fileId    文件 ID
     * @param grantedTo 被授权用户 ID
     * @param now       签发时刻
     * @param expiresAt 失效时刻（必须晚于签发时刻）
     * @return 新签名记录（尚无数据库主键）
     */
    public static FileDownloadGrant issue(UUID fileId, UUID grantedTo, Instant now, Instant expiresAt) {
        String token = UUID.randomUUID().toString().replace("-", "");
        return new FileDownloadGrant(null, fileId, grantedTo, token, now, expiresAt, null);
    }

    /**
     * 从持久化数据重建签名记录（仓储层专用）。
     *
     * @param id        主键
     * @param fileId    文件 ID
     * @param grantedTo 被授权用户 ID
     * @param token     签名 token
     * @param createdAt 签发时刻
     * @param expiresAt 失效时刻
     * @param revokedAt 撤销时刻；未撤销为空
     * @return 重建的签名记录
     */
    public static FileDownloadGrant rehydrate(UUID id, UUID fileId, UUID grantedTo, String token,
                                              Instant createdAt, Instant expiresAt, Instant revokedAt) {
        return new FileDownloadGrant(Objects.requireNonNull(id, "id 不能为空"), fileId, grantedTo, token,
                createdAt, expiresAt, revokedAt);
    }

    /**
     * 判断签名在指定时刻是否可用。
     *
     * @param now 校验时刻
     * @return 未撤销且未到期返回 {@code true}；到期时刻本身视为失效
     */
    public boolean isUsableAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /**
     * 撤销签名（幂等：已撤销时保留首次撤销时间）。
     *
     * @param now 撤销时刻
     */
    public void revoke(Instant now) {
        Objects.requireNonNull(now, "撤销时刻不能为空");
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getFileId() {
        return fileId;
    }

    public UUID getGrantedTo() {
        return grantedTo;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
