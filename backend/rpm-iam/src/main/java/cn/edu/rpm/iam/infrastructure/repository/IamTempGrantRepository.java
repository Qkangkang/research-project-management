package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamTempGrantEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code iam_temp_grant} 临时授权仓储接口（T-IAM-005）。
 *
 * <p>纯单表 CRUD 数据访问契约；有效期过滤语义与 Batch-4 GrantValidityGuard 一致：
 * {@code status = ACTIVE} 且 {@code valid_from <= 查询时点 < valid_to}（valid_to 非空）。</p>
 */
public interface IamTempGrantRepository {

    /**
     * 插入临时授权。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成；
     * 调用方必须保证 valid_to &gt; valid_from（ck_temp_grant_valid 校验兜底）。</p>
     *
     * @param grant 待插入的临时授权 PO
     */
    void insert(IamTempGrantEntity grant);

    /**
     * 按被授权人查询在指定时点有效的临时授权列表。
     *
     * <p>过滤条件：{@code status = ACTIVE}、{@code valid_from <= at} 且 {@code valid_to > at}；
     * 查询列与 idx_temp_grant_grantee_valid（grantee_id, valid_from, valid_to）对齐。
     * 过期/撤销记录不返回。</p>
     *
     * @param granteeId 被授权人用户 ID
     * @param at        授权判定时点
     * @return 该时点有效的临时授权列表；无命中返回空列表
     */
    List<IamTempGrantEntity> findValidByGrantee(String granteeId, OffsetDateTime at);
}
