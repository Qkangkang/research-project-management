package cn.edu.rpm.file.domain.port;

import cn.edu.rpm.file.domain.model.FileRef;

import java.util.List;
import java.util.UUID;

/**
 * 文件业务引用查询端口（T-FILE-005）。
 *
 * <p>下载鉴权需要判断文件是否被业务引用、引用属于哪个业务对象；领域层只依赖本端口，
 * 由 {@code FileRefRepositoryImpl} 实现。</p>
 */
public interface FileRefQueryPort {

    /**
     * 查询文件被哪些业务对象引用。
     *
     * @param fileId 文件 ID
     * @return 引用列表；未被引用返回空列表（孤立文件）
     */
    List<FileRef> findByFileId(UUID fileId);
}
