package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputReviewEntity;

import java.util.Collection;
import java.util.List;

/**
 * {@code output_review} 成果审核仓储（T-OUT-004）。
 *
 * <p>审核记录只追加；按版本查询用于详情与历史意见展示。</p>
 */
public interface OutputReviewRepository {

    /**
     * 插入审核记录。
     *
     * @param review 待插入的审核记录 PO
     */
    void insert(OutputReviewEntity review);

    /**
     * 按成果版本查询审核记录（按审核时间倒序）。
     *
     * @param outputVersionId 成果版本 ID
     * @return 审核记录列表；无记录返回空列表
     */
    List<OutputReviewEntity> findByOutputVersionId(String outputVersionId);

    /**
     * 按多个成果版本批量查询审核记录（详情页历史意见，禁止循环单查，RULE-DB2）。
     *
     * @param outputVersionIds 成果版本 ID 集合
     * @return 审核记录列表（按审核时间倒序）；空集合返回空列表
     */
    List<OutputReviewEntity> findByOutputVersionIds(Collection<String> outputVersionIds);
}
