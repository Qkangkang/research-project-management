package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateVersionEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code wf_template_version} 模板版本仓储（T-WF-003）。
 *
 * <p>版本号在模板内唯一（uk_wf_template_version_no）；生效版本按
 * {@code status = PUBLISHED} 与 {@code [effective_from, effective_to)} 区间判定，
 * 已运行实例绑定旧版本（技术文档 03 §3.3）。</p>
 */
public interface WfTemplateVersionRepository {

    /**
     * 插入模板版本。
     *
     * @param version 待插入的模板版本 PO
     */
    void insert(WfTemplateVersionEntity version);

    /**
     * 按模板 ID + 版本号查询版本（uk_wf_template_version_no）。
     *
     * @param templateId 模板 ID
     * @param versionNo  模板内版本号（从 1 开始）
     * @return 模板版本；不存在返回 {@link Optional#empty()}
     */
    Optional<WfTemplateVersionEntity> findByTemplateIdAndVersionNo(String templateId, int versionNo);

    /**
     * 查询指定时点生效的模板版本。
     *
     * <p>过滤条件：{@code template_id = ?}、{@code status = PUBLISHED}、
     * {@code effective_from <= at}、{@code effective_to IS NULL OR effective_to > at}；
     * 若历史数据存在区间重叠，取 {@code effective_from} 最新的一条。</p>
     *
     * @param templateId 模板 ID
     * @param at         业务时点
     * @return 生效版本；无命中返回 {@link Optional#empty()}
     */
    Optional<WfTemplateVersionEntity> findEffectiveByTemplateId(String templateId, OffsetDateTime at);

    /**
     * 查询模板的最新版本（按 {@code version_no} 降序取第一条）。
     *
     * @param templateId 模板 ID
     * @return 最新版本；模板尚无版本返回 {@link Optional#empty()}
     */
    Optional<WfTemplateVersionEntity> findLatestByTemplateId(String templateId);

    /**
     * 查询模板下全部已发布版本（发布时做金额条件重叠检测）。
     *
     * @param templateId 模板 ID
     * @return 已发布版本列表；无命中返回空列表
     */
    List<WfTemplateVersionEntity> findPublishedByTemplateId(String templateId);

    /**
     * 更新模板版本（乐观锁：携带旧 {@code version}）。
     *
     * @param version 待更新版本（version 为读取到的旧值）
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(WfTemplateVersionEntity version);
}
