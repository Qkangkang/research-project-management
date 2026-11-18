package cn.edu.rpm.iam.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 临时授权的领域只读视图（Batch-4 / T-IAM-009）。
 *
 * <p>只投影数据范围解析所需字段；有效期由 {@code GrantValidityGuard} 复核，
 * 不信任查询侧过滤（防御存储异常与时钟口径差异）。</p>
 *
 * @param resourceType   资源类型（见 {@link ResourceTypes}）
 * @param resourceId     资源 ID（如项目 ID）
 * @param permissionCode 授权的动作权限码
 * @param validFrom      生效时间
 * @param validTo        失效时间
 */
public record TempGrantView(String resourceType,
                            String resourceId,
                            String permissionCode,
                            OffsetDateTime validFrom,
                            OffsetDateTime validTo) {

    /**
     * 紧凑构造器：资源类型与资源 ID 必填。
     */
    public TempGrantView {
        Objects.requireNonNull(resourceType, "resourceType 不能为空");
        Objects.requireNonNull(resourceId, "resourceId 不能为空");
    }
}
