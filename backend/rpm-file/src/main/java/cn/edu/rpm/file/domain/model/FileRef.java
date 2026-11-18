package cn.edu.rpm.file.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件业务引用（T-FILE-002；V01 {@code file_ref}）。
 *
 * <p>业务模块只保存 {@code file_id} 并建立引用；{@code biz_type + biz_id + purpose}
 * 表达"哪个业务对象的什么用途引用了该文件"。解除引用后文件成为孤立文件，
 * 按保留策略清理（技术文档 02 §1 原则 10）。</p>
 *
 * @param id          引用主键（新建时为空）
 * @param fileId      文件对象 ID
 * @param bizType     业务类型（如 {@code APPLICATION}、{@code EXPENSE_REQUEST}）
 * @param bizId       业务对象 ID
 * @param purpose     用途（如 {@code APPLICATION_ATTACHMENT}）
 * @param accessLevel 访问级别
 * @param createdBy   创建人用户 ID
 * @param createdAt   创建时间
 */
public record FileRef(
        UUID id,
        UUID fileId,
        String bizType,
        UUID bizId,
        String purpose,
        FileAccessLevel accessLevel,
        UUID createdBy,
        Instant createdAt
) {

    /** 校验必填字段。 */
    public FileRef {
        Objects.requireNonNull(fileId, "fileId 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        if (bizType == null || bizType.isBlank()) {
            throw new IllegalArgumentException("bizType 不能为空");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose 不能为空");
        }
        Objects.requireNonNull(accessLevel, "accessLevel 不能为空");
    }
}
