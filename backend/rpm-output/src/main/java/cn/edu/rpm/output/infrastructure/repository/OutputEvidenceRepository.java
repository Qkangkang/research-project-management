package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;

import java.util.List;

/**
 * {@code output_evidence} 成果证据仓储（T-OUT-003）。
 *
 * <p>证据行以版本为边界；草稿保存允许替换当前草稿版本的证据，
 * 已提交/已审核版本证据不可变（由应用服务保证）。</p>
 */
public interface OutputEvidenceRepository {

    /**
     * 批量插入证据（单批 ≤ 500 条，RULE-DB3）。
     *
     * @param evidences 待插入证据列表
     */
    void insertBatch(List<OutputEvidenceEntity> evidences);

    /**
     * 按成果版本查询证据。
     *
     * @param outputVersionId 成果版本 ID
     * @return 证据列表；无证据返回空列表
     */
    List<OutputEvidenceEntity> findByOutputVersionId(String outputVersionId);

    /**
     * 统计成果版本证据数量（提交前校验至少一份，TC-OUT-005）。
     *
     * @param outputVersionId 成果版本 ID
     * @return 证据数量
     */
    long countByOutputVersionId(String outputVersionId);

    /**
     * 删除指定版本的证据（仅用于草稿保存时的替换，不用于已提交版本）。
     *
     * @param outputVersionId 成果版本 ID
     * @return 删除行数
     */
    int deleteByOutputVersionId(String outputVersionId);
}
