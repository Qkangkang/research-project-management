package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamUserRoleEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code iam_user_role} 用户-角色-组织关联仓储接口（T-IAM-004）。
 *
 * <p>纯单表 CRUD 数据访问契约；有效期过滤语义与 Batch-4 授权判定一致：
 * {@code status = ACTIVE} 且 {@code valid_from <= 查询时点 < valid_to}（valid_to 为空视为长期有效）。</p>
 */
public interface IamUserRoleRepository {

    /**
     * 插入用户-角色关联。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成（V01 权威 DDL 为代理主键 id）。</p>
     *
     * @param association 待插入的用户-角色关联 PO
     */
    void insert(IamUserRoleEntity association);

    /**
     * 按用户查询全部角色关联（含已过期/已撤销，由调用方按 status 自行判定）。
     *
     * @param userId 用户 ID
     * @return 该用户的全部角色关联；无命中返回空列表
     */
    List<IamUserRoleEntity> findByUserId(String userId);

    /**
     * 按用户查询在指定时点有效的角色关联。
     *
     * <p>过滤条件：{@code status = ACTIVE}、{@code valid_from <= at} 且
     * {@code valid_to IS NULL OR valid_to > at}；对应索引
     * idx_iam_user_role_user / idx_iam_user_role_valid。</p>
     *
     * @param userId 用户 ID
     * @param at     授权判定时点
     * @return 该时点有效的角色关联；无命中返回空列表
     */
    List<IamUserRoleEntity> findValidByUserId(String userId, OffsetDateTime at);
}
