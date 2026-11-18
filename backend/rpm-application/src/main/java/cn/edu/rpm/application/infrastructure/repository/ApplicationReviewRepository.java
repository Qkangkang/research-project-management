package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationReviewEntity;

import java.util.List;

/**
 * {@code application_review} 申报审查意见仓储（T-APP-006）。
 *
 * <p>纯单表数据访问契约，不含业务规则；供形式审查、专家评审与立项决策
 * 追加审查意见及审批轨迹展示使用。约定：</p>
 * <ul>
 *   <li>审查意见只追加不覆盖（历史结论不可篡改）；</li>
 *   <li>插入撞约束时抛出 Spring
 *       {@link org.springframework.dao.DataIntegrityViolationException}（含 CHECK /
 *       FK 违反），由全局异常处理器统一转换。</li>
 * </ul>
 */
public interface ApplicationReviewRepository {

    /**
     * 追加一条审查意见。
     *
     * @param review 待插入的审查意见 PO
     */
    void insert(ApplicationReviewEntity review);

    /**
     * 按申报版本与审查阶段查询意见，走 idx_application_review_version_stage。
     *
     * <p>同一阶段允许多位评审人提交，结果按 {@code reviewed_at} 升序形成审查轨迹。</p>
     *
     * @param applicationVersionId 申报版本 ID
     * @param reviewStage          审查阶段（FORMAL / EXPERT / DECISION）
     * @return 审查意见列表；无记录返回空列表
     */
    List<ApplicationReviewEntity> findByVersionIdAndStage(String applicationVersionId, String reviewStage);
}
