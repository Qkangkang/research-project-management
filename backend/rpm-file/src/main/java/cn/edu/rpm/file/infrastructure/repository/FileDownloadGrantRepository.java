package cn.edu.rpm.file.infrastructure.repository;

import cn.edu.rpm.file.domain.port.FileGrantPort;

/**
 * {@code file_download_grant} 仓储接口（T-FILE-005）。
 *
 * <p>继承 {@link FileGrantPort}，不额外暴露 PO 操作；签名为短期凭据，
 * 业务侧只通过领域端口签发/校验/撤销。</p>
 */
public interface FileDownloadGrantRepository extends FileGrantPort {
}
