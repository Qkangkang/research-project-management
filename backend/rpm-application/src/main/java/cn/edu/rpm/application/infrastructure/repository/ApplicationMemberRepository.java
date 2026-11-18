package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationMemberEntity;

import java.util.List;

/**
 * {@code application_member} 申报团队成员仓储（T-APP-005）。
 *
 * <p>纯单表数据访问契约，不含业务规则；成员快照随申报版本固化，
 * 只插入与查询、不更新历史版本。约定：</p>
 * <ul>
 *   <li>批量插入按 RULE-DB3 分批（每批 ≤ 500 条），单批为一条多值 INSERT；</li>
 *   <li>插入撞唯一键（uk_application_member）时抛出 Spring
 *       {@link org.springframework.dao.DuplicateKeyException}。</li>
 * </ul>
 */
public interface ApplicationMemberRepository {

    /**
     * 批量插入成员快照。
     *
     * <p>缺失主键的元素在写入前分配 32 位 uuid 文本（与 ASSIGN_UUID 同格式）；
     * 超过 500 条时自动分批（RULE-DB3）。空列表或 {@code null} 时不做任何操作。</p>
     *
     * @param members 成员快照列表
     */
    void insertBatch(List<ApplicationMemberEntity> members);

    /**
     * 按申报版本查询成员快照，走 idx_application_member_version。
     *
     * <p>结果按 {@code sort_order} 升序返回，供详情页直接展示。</p>
     *
     * @param applicationVersionId 申报版本 ID
     * @return 成员列表；无成员返回空列表
     */
    List<ApplicationMemberEntity> findByVersionId(String applicationVersionId);
}
