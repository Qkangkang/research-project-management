package cn.edu.rpm.file.infrastructure.repository;

import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FileStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code file_object} 文件元数据仓储接口（T-FILE-001）。
 *
 * <p>对上层暴露领域对象 {@link FileObject}，PO 与领域对象转换在本层完成；
 * 不含业务规则。唯一约束：{@code uk_file_object_key}（对象键不可重复）。</p>
 */
public interface FileObjectRepository {

    /**
     * 插入文件元数据。
     *
     * @param file 新文件对象（无主键）
     * @return 数据库生成的主键（32 位十六进制文本）
     */
    String insert(FileObject file);

    /**
     * 按主键查询文件对象。
     *
     * @param fileId 文件 ID
     * @return 命中的文件对象；不存在返回空 {@link Optional}
     */
    Optional<FileObject> findById(UUID fileId);

    /**
     * 按主键批量查询文件对象（提交前附件可用性校验，RULE-DB2 禁止 N+1）。
     *
     * <p>单次 {@code IN} 查询；入参为空返回空列表。返回顺序不保证与入参一致，
     * 调用方应按 {@link FileObject#getId()} 建索引。</p>
     *
     * @param fileIds 文件 ID 集合
     * @return 命中的文件对象列表；无命中返回空列表
     */
    List<FileObject> findByIds(Collection<UUID> fileIds);

    /**
     * 按内容 SHA-256 查询已存在文件（去重）。
     *
     * <p>{@code sha256} 非唯一索引，按创建时间取最早一条作为去重结果。</p>
     *
     * @param sha256 内容摘要（64 位十六进制）
     * @return 命中的文件对象；未上传过返回空 {@link Optional}
     */
    Optional<FileObject> findBySha256(String sha256);

    /**
     * 按状态筛选文件（如清理任务扫描孤立文件）。
     *
     * @param status 文件状态
     * @return 匹配文件列表；无匹配返回空列表
     */
    List<FileObject> findByStatus(FileStatus status);

    /**
     * 按乐观锁更新文件元数据（实体 {@code version} 旧值作并发条件）。
     *
     * @param file 携带主键与旧版本的文件对象
     * @return 受影响行数；0 表示记录不存在或版本冲突
     */
    int update(FileObject file);
}
