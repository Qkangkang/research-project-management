package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfInstanceEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code wf_instance} 审批实例仓储（T-WF-004）。
 *
 * <p>同一业务版本同时只允许一个 RUNNING 实例（部分唯一索引
 * uk_wf_instance_running）；实例进入终态后可再次发起。</p>
 */
public interface WfInstanceRepository {

    /**
     * 插入审批实例。
     *
     * <p>若同一 {@code (biz_type, biz_id, biz_version_id)} 已有 RUNNING 实例，
     * 数据库部分唯一索引会抛出唯一冲突（TC-APP-009 的最终防线）。</p>
     *
     * @param instance 待插入的实例 PO
     */
    void insert(WfInstanceEntity instance);

    /**
     * 按主键查询审批实例。
     *
     * @param id 实例 ID
     * @return 实例；不存在返回 {@link Optional#empty()}
     */
    Optional<WfInstanceEntity> findById(String id);

    /**
     * 查询指定业务版本当前运行中的审批实例。
     *
     * <p>过滤条件：{@code biz_type}、{@code biz_id}、{@code biz_version_id}、
     * {@code status = RUNNING}（与 uk_wf_instance_running 的谓词一致）。</p>
     *
     * @param bizType       业务类型
     * @param bizId         业务对象 ID
     * @param bizVersionId  业务版本 ID
     * @return 运行中实例；无命中返回 {@link Optional#empty()}
     */
    Optional<WfInstanceEntity> findRunningByBiz(String bizType, String bizId, String bizVersionId);

    /**
     * 查询指定业务版本最近一次审批实例（含终态，按 {@code started_at} 倒序）。
     *
     * <p>供业务幂等回放读取首次启动的实例 ID；与
     * {@link #findRunningByBiz(String, String, String)} 的区别是不限实例状态。</p>
     *
     * @param bizType      业务类型
     * @param bizId        业务对象 ID
     * @param bizVersionId 业务版本 ID
     * @return 最近实例；无实例返回 {@link Optional#empty()}
     */
    Optional<WfInstanceEntity> findLatestByBiz(String bizType, String bizId, String bizVersionId);

    /**
     * 查询我发起的审批实例（审批中心「我发起的」页；started_at 倒序分页）。
     *
     * @param initiatorId 发起人用户 ID（32 位十六进制）
     * @param offset      偏移量
     * @param limit       每页条数
     * @return 实例列表；无命中返回空列表
     */
    List<WfInstanceEntity> findInitiatedPage(String initiatorId, long offset, int limit);

    /**
     * 统计我发起的审批实例总数。
     *
     * @param initiatorId 发起人用户 ID（32 位十六进制）
     * @return 总条数
     */
    long countInitiated(String initiatorId);

    /**
     * 更新审批实例（乐观锁：携带旧 {@code version}）。
     *
     * @param instance 待更新实例（version 为读取到的旧值）
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(WfInstanceEntity instance);
}
