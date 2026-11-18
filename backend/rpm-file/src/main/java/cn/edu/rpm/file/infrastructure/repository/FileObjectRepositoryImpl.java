package cn.edu.rpm.file.infrastructure.repository;

import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FileStatus;
import cn.edu.rpm.file.domain.model.ScanStatus;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.file.infrastructure.entity.FileObjectEntity;
import cn.edu.rpm.file.infrastructure.mapper.FileObjectMapper;
import cn.edu.rpm.common.util.UuidUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code file_object} 仓储实现（T-FILE-001）。
 *
 * <p>基于 MyBatis-Plus {@link FileObjectMapper} 的薄委托层：PO ↔ 领域对象转换在本层
 * 显式字段投影（技术栈未引入 MapStruct）。查询条件均走实体注解生成的参数化 Wrapper。</p>
 */
@Repository
public class FileObjectRepositoryImpl implements FileObjectRepository, FileObjectQueryPort {

    private static final ZoneId ZONE_ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final FileObjectMapper fileObjectMapper;

    public FileObjectRepositoryImpl(FileObjectMapper fileObjectMapper) {
        this.fileObjectMapper = fileObjectMapper;
    }

    @Override
    public String insert(FileObject file) {
        Objects.requireNonNull(file, "文件对象不能为空");
        FileObjectEntity entity = new FileObjectEntity();
        entity.setBucket(file.getBucket());
        entity.setObjectKey(file.getObjectKey());
        entity.setOriginalName(file.getOriginalName());
        entity.setMimeType(file.getMimeType());
        entity.setSizeBytes(file.getSizeBytes());
        entity.setSha256(file.getSha256());
        entity.setStatus(file.getStatus().name());
        entity.setScanStatus(file.getScanStatus().name());
        entity.setUploadedBy(uuidText(file.getUploadedBy()));
        entity.setVersion(0);
        fileObjectMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public Optional<FileObject> findById(UUID fileId) {
        Objects.requireNonNull(fileId, "文件 ID 不能为空");
        return Optional.ofNullable(fileObjectMapper.selectById(UuidUtils.formatCompact(fileId)))
                .map(FileObjectRepositoryImpl::toDomain);
    }

    @Override
    public List<FileObject> findByIds(Collection<UUID> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = fileIds.stream()
                .filter(Objects::nonNull)
                .map(UuidUtils::formatCompact)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return fileObjectMapper.selectByIds(ids).stream()
                .map(FileObjectRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public Optional<FileObject> findBySha256(String sha256) {
        List<FileObjectEntity> matches = fileObjectMapper.selectList(new LambdaQueryWrapper<FileObjectEntity>()
                .eq(FileObjectEntity::getSha256, sha256)
                .orderByAsc(FileObjectEntity::getCreatedAt));
        return matches.stream().findFirst().map(FileObjectRepositoryImpl::toDomain);
    }

    @Override
    public List<FileObject> findByStatus(FileStatus status) {
        Objects.requireNonNull(status, "文件状态不能为空");
        return fileObjectMapper.selectList(new LambdaQueryWrapper<FileObjectEntity>()
                        .eq(FileObjectEntity::getStatus, status.name())
                        .orderByAsc(FileObjectEntity::getCreatedAt))
                .stream()
                .map(FileObjectRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public int update(FileObject file) {
        Objects.requireNonNull(file, "文件对象不能为空");
        Objects.requireNonNull(file.getId(), "更新文件必须携带主键");
        FileObjectEntity entity = new FileObjectEntity();
        entity.setId(UuidUtils.formatCompact(file.getId()));
        entity.setStatus(file.getStatus().name());
        entity.setScanStatus(file.getScanStatus().name());
        entity.setSha256(file.getSha256());
        entity.setVersion(file.getVersion());
        return fileObjectMapper.updateById(entity);
    }

    /**
     * PO → 领域对象的显式字段投影。
     *
     * @param entity 文件元数据 PO
     * @return 领域文件对象
     */
    private static FileObject toDomain(FileObjectEntity entity) {
        return FileObject.rehydrate(
                UuidUtils.parse(entity.getId()),
                entity.getBucket(),
                entity.getObjectKey(),
                entity.getOriginalName(),
                entity.getMimeType(),
                entity.getSizeBytes() == null ? 0L : entity.getSizeBytes(),
                entity.getSha256(),
                FileStatus.valueOf(entity.getStatus()),
                ScanStatus.valueOf(entity.getScanStatus()),
                entity.getUploadedBy() == null ? null : UuidUtils.parse(entity.getUploadedBy()),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().atZoneSameInstant(ZONE_ASIA_SHANGHAI).toInstant(),
                entity.getVersion() == null ? 0 : entity.getVersion());
    }

    private static String uuidText(UUID value) {
        return value == null ? null : UuidUtils.formatCompact(value);
    }
}
