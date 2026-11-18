package cn.edu.rpm.file.domain.port;

import cn.edu.rpm.file.domain.model.FileDownloadGrant;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 下载签名记录端口（T-FILE-005；V06 {@code file_download_grant}）。
 *
 * <p>领域层只依赖本端口，由 {@code FileDownloadGrantRepositoryImpl} 实现。
 * 批量撤销按文件 ID 单条 SQL 完成，不在循环内更新（RULE-DB2）。</p>
 */
public interface FileGrantPort {

    /**
     * 保存新签发的下载签名。
     *
     * @param grant 签名记录
     */
    void save(FileDownloadGrant grant);

    /**
     * 按 token 查询签名记录（uk_file_download_grant 唯一）。
     *
     * @param token 签名 token
     * @return 命中记录；不存在返回空 {@link Optional}
     */
    Optional<FileDownloadGrant> findByToken(String token);

    /**
     * 撤销某文件的全部未撤销签名（业务授权撤回时调用）。
     *
     * @param fileId    文件 ID
     * @param revokedAt 撤销时刻
     * @return 受影响行数
     */
    int revokeAllForFile(UUID fileId, Instant revokedAt);
}
