package cn.edu.rpm.iam.domain.model;

import java.util.UUID;

/**
 * 利益冲突声明的领域只读视图（与 {@code coi_declaration} 表解耦，供领域服务使用）。
 *
 * @param relatedUserId 与专家存在冲突关系的用户 ID
 * @param status        声明状态（{@link CoiDeclarationStatus}）
 */
public record CoiDeclarationView(UUID relatedUserId, String status) {
}
