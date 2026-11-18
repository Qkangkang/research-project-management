package cn.edu.rpm.organization.infrastructure.repository;

import cn.edu.rpm.organization.infrastructure.entity.OrgMembershipEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code org_membership} 人员归属仓储接口（T-ORG-002）。
 *
 * <p>纯单表 CRUD 数据访问契约；供 Batch-4 数据范围解析（部门维度）与通知寻址使用。
 * 口径说明：主部门唯一性（拆解验收"is_primary 唯一"）在 V01～V07 无 DB 唯一约束，
 * 由 {@link #findPrimaryByUserId} 取单条语义与数据治理保证。</p>
 */
public interface OrgMembershipRepository {

    /**
     * 插入人员归属。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成；
     * 调用方须保证 valid_to &gt; valid_from（ck_org_membership_valid 校验兜底）。</p>
     *
     * @param membership 待插入的人员归属 PO
     */
    void insert(OrgMembershipEntity membership);

    /**
     * 按人员查询全部归属关系（idx_org_membership_user），不做有效期过滤，
     * 由调用方按业务时点自行判定。
     *
     * @param userId 人员用户 ID
     * @return 归属关系列表；无命中返回空列表
     */
    List<OrgMembershipEntity> findByUserId(String userId);

    /**
     * 按人员查询其主要部门归属（{@code is_primary = true}）。
     *
     * <p>过滤条件：{@code user_id = ? AND is_primary = TRUE}；
     * 一人至多一个主要部门（数据治理保证），多行命中时由底层抛多结果异常暴露脏数据。</p>
     *
     * @param userId 人员用户 ID
     * @return 主要部门归属；不存在返回空 {@link Optional}
     */
    Optional<OrgMembershipEntity> findPrimaryByUserId(String userId);
}
