package cn.edu.rpm.file.infrastructure.repository;

import cn.edu.rpm.file.domain.model.FileRef;
import cn.edu.rpm.file.domain.port.FileRefQueryPort;

import java.util.List;
import java.util.UUID;

/**
 * {@code file_ref} 文件业务引用仓储接口（T-FILE-002）。
 *
 * <p>不提供更新方法（引用是追加/解除语义）；解除引用后文件成为孤立文件。
 * 继承 {@link FileRefQueryPort} 供下载鉴权查询引用。</p>
 */
public interface FileRefRepository extends FileRefQueryPort {

    /**
     * 新建文件业务引用。
     *
     * <p>同一 {@code file_id + biz_type + biz_id + purpose} 重复插入时抛
     * {@link org.springframework.dao.DuplicateKeyException}（uk_file_ref_biz）。</p>
     *
     * @param fileRef 引用（主键为空）
     * @return 数据库生成的主键（32 位十六进制）
     */
    String insert(FileRef fileRef);

    /**
     * 按业务对象查询引用（idx_file_ref_biz）。
     *
     * @param bizType 业务类型
     * @param bizId   业务对象 ID
     * @return 引用列表；无引用返回空列表
     */
    List<FileRef> findByBizTypeAndBizId(String bizType, UUID bizId);

    /**
     * 解除文件业务引用。
     *
     * @param refId 引用主键
     * @return 受影响行数；0 表示引用不存在
     */
    int deleteById(UUID refId);
}
