package cn.edu.rpm.organization.api.dto;

import cn.edu.rpm.organization.infrastructure.entity.OrgUnitEntity;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 组织/部门下拉选项。
 *
 * @param id       组织 ID
 * @param parentId 父组织 ID；根节点为空
 * @param code     组织编码
 * @param name     组织名称
 * @param unitType 组织类型
 */
public record OrgUnitOptionView(
        @JsonProperty("id") String id,
        @JsonProperty("parent_id") String parentId,
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("unit_type") String unitType
) {

    /**
     * 由组织 PO 构造下拉选项。
     *
     * @param entity 组织 PO
     * @return 下拉选项
     */
    public static OrgUnitOptionView from(OrgUnitEntity entity) {
        return new OrgUnitOptionView(entity.getId(), entity.getParentId(), entity.getCode(),
                entity.getName(), entity.getUnitType());
    }
}
