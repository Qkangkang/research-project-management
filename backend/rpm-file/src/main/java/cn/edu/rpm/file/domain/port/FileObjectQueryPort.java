package cn.edu.rpm.file.domain.port;

import cn.edu.rpm.file.domain.model.FileObject;

import java.util.Optional;
import java.util.UUID;

/**
 * 文件对象查询端口（T-FILE-005）。
 *
 * <p>领域服务只依赖本端口读取文件元数据，由 infrastructure 的
 * {@code FileObjectRepositoryImpl} 实现，避免 domain 层依赖 MyBatis/PO。</p>
 */
public interface FileObjectQueryPort {

    /**
     * 按主键查询文件对象。
     *
     * @param fileId 文件 ID
     * @return 命中文件；不存在返回空 {@link Optional}
     */
    Optional<FileObject> findById(UUID fileId);
}
