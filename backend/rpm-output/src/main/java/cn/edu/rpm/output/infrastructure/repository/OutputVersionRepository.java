package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code output_version} 成果版本仓储（T-OUT-002）。
 *
 * <p>已提交/已审核版本不可变（只追加）；草稿版本允许原地更新元数据。
 * 唯一键冲突按 {@link org.springframework.dao.DuplicateKeyException} 向上抛出，
 * 由应用服务决定语义。</p>
 */
public interface OutputVersionRepository {

    /**
     * 插入成果版本。
     *
     * @param version 待插入的版本 PO
     */
    void insert(OutputVersionEntity version);

    /**
     * 按主键查询版本。
     *
     * @param id 版本 ID
     * @return 命中的版本；不存在返回空
     */
    Optional<OutputVersionEntity> findById(String id);

    /**
     * 按成果与版本号查询（uk_output_version_no 唯一）。
     *
     * @param outputId  成果 ID
     * @param versionNo 版本号
     * @return 命中的版本；不存在返回空
     */
    Optional<OutputVersionEntity> findByOutputIdAndVersionNo(String outputId, int versionNo);

    /**
     * 查询成果全部版本（按版本号倒序）。
     *
     * @param outputId 成果 ID
     * @return 版本列表；无版本返回空列表
     */
    List<OutputVersionEntity> findByOutputId(String outputId);

    /**
     * 查询成果当前最大版本号，供修订生成 {@code max + 1}。
     *
     * @param outputId 成果 ID
     * @return 最大版本号；无版本返回 0
     */
    int findMaxVersionNo(String outputId);

    /**
     * 更新草稿版本元数据（仅 {@code DRAFT} 成果的当前版本允许；提交后版本不可变）。
     *
     * <p>应用服务保证调用边界：已提交/已审核版本禁止调用本方法。</p>
     *
     * @param version 携带主键与待更新字段的版本 PO
     * @return 受影响行数
     */
    int update(OutputVersionEntity version);
}
