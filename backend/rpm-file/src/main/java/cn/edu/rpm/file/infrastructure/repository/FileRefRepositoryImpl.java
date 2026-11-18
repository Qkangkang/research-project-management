package cn.edu.rpm.file.infrastructure.repository;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.file.domain.model.FileAccessLevel;
import cn.edu.rpm.file.domain.model.FileRef;
import cn.edu.rpm.file.infrastructure.entity.FileRefEntity;
import cn.edu.rpm.file.infrastructure.mapper.FileRefMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code file_ref} 文件业务引用仓储实现（T-FILE-002）。
 *
 * <p>基于 MyBatis-Plus {@link FileRefMapper} 的薄委托层；PO ↔ 领域对象显式字段投影。
 * 解除引用使用单条 {@code deleteById}，不在循环内逐条操作（无 N+1）。</p>
 */
@Repository
public class FileRefRepositoryImpl implements FileRefRepository {

    private final FileRefMapper fileRefMapper;

    public FileRefRepositoryImpl(FileRefMapper fileRefMapper) {
        this.fileRefMapper = fileRefMapper;
    }

    @Override
    public String insert(FileRef fileRef) {
        Objects.requireNonNull(fileRef, "文件引用不能为空");
        FileRefEntity entity = new FileRefEntity();
        entity.setFileId(UuidUtils.formatCompact(fileRef.fileId()));
        entity.setBizType(fileRef.bizType());
        entity.setBizId(UuidUtils.formatCompact(fileRef.bizId()));
        entity.setPurpose(fileRef.purpose());
        entity.setAccessLevel(fileRef.accessLevel().name());
        entity.setCreatedBy(fileRef.createdBy() == null ? null : UuidUtils.formatCompact(fileRef.createdBy()));
        fileRefMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public List<FileRef> findByBizTypeAndBizId(String bizType, UUID bizId) {
        Objects.requireNonNull(bizId, "bizId 不能为空");
        return fileRefMapper.selectList(new LambdaQueryWrapper<FileRefEntity>()
                        .eq(FileRefEntity::getBizType, bizType)
                        .eq(FileRefEntity::getBizId, UuidUtils.formatCompact(bizId))
                        .orderByAsc(FileRefEntity::getCreatedAt))
                .stream()
                .map(FileRefRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<FileRef> findByFileId(UUID fileId) {
        Objects.requireNonNull(fileId, "文件 ID 不能为空");
        return fileRefMapper.selectList(new LambdaQueryWrapper<FileRefEntity>()
                        .eq(FileRefEntity::getFileId, UuidUtils.formatCompact(fileId))
                        .orderByAsc(FileRefEntity::getCreatedAt))
                .stream()
                .map(FileRefRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public int deleteById(UUID refId) {
        Objects.requireNonNull(refId, "引用 ID 不能为空");
        return fileRefMapper.deleteById(UuidUtils.formatCompact(refId));
    }

    private static FileRef toDomain(FileRefEntity entity) {
        return new FileRef(
                UuidUtils.parse(entity.getId()),
                UuidUtils.parse(entity.getFileId()),
                entity.getBizType(),
                UuidUtils.parse(entity.getBizId()),
                entity.getPurpose(),
                FileAccessLevel.valueOf(entity.getAccessLevel()),
                entity.getCreatedBy() == null ? null : UuidUtils.parse(entity.getCreatedBy()),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toInstant());
    }
}
