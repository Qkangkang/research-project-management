package cn.edu.rpm.file.infrastructure.repository;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.file.domain.model.FileDownloadGrant;
import cn.edu.rpm.file.infrastructure.entity.FileDownloadGrantEntity;
import cn.edu.rpm.file.infrastructure.mapper.FileDownloadGrantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code file_download_grant} 仓储实现（T-FILE-005）。
 *
 * <p>签发为单条 INSERT；撤销为单条参数化 UPDATE（{@code file_id = ? AND revoked_at IS NULL}），
 * 在数据库侧一次性完成，不在应用层循环逐条更新。</p>
 */
@Repository
public class FileDownloadGrantRepositoryImpl implements FileDownloadGrantRepository {

    private static final ZoneId ZONE_ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final FileDownloadGrantMapper fileDownloadGrantMapper;

    public FileDownloadGrantRepositoryImpl(FileDownloadGrantMapper fileDownloadGrantMapper) {
        this.fileDownloadGrantMapper = fileDownloadGrantMapper;
    }

    @Override
    public void save(FileDownloadGrant grant) {
        Objects.requireNonNull(grant, "下载签名不能为空");
        FileDownloadGrantEntity entity = new FileDownloadGrantEntity();
        entity.setFileId(UuidUtils.formatCompact(grant.getFileId()));
        entity.setGrantedTo(UuidUtils.formatCompact(grant.getGrantedTo()));
        entity.setGrantToken(grant.getToken());
        entity.setCreatedAt(toOffset(grant.getCreatedAt()));
        entity.setExpiresAt(toOffset(grant.getExpiresAt()));
        entity.setRevokedAt(toOffset(grant.getRevokedAt()));
        fileDownloadGrantMapper.insert(entity);
    }

    @Override
    public Optional<FileDownloadGrant> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(fileDownloadGrantMapper.selectOne(
                        new LambdaQueryWrapper<FileDownloadGrantEntity>()
                                .eq(FileDownloadGrantEntity::getGrantToken, token)))
                .map(FileDownloadGrantRepositoryImpl::toDomain);
    }

    @Override
    public int revokeAllForFile(java.util.UUID fileId, Instant revokedAt) {
        Objects.requireNonNull(fileId, "文件 ID 不能为空");
        Objects.requireNonNull(revokedAt, "撤销时刻不能为空");
        return fileDownloadGrantMapper.update(null, new LambdaUpdateWrapper<FileDownloadGrantEntity>()
                .set(FileDownloadGrantEntity::getRevokedAt, toOffset(revokedAt))
                .eq(FileDownloadGrantEntity::getFileId, UuidUtils.formatCompact(fileId))
                .isNull(FileDownloadGrantEntity::getRevokedAt));
    }

    private static FileDownloadGrant toDomain(FileDownloadGrantEntity entity) {
        return FileDownloadGrant.rehydrate(
                UuidUtils.parse(entity.getId()),
                UuidUtils.parse(entity.getFileId()),
                UuidUtils.parse(entity.getGrantedTo()),
                entity.getGrantToken(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toInstant(),
                entity.getExpiresAt().toInstant(),
                entity.getRevokedAt() == null ? null : entity.getRevokedAt().toInstant());
    }

    private static java.time.OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE_ASIA_SHANGHAI).toOffsetDateTime();
    }
}
