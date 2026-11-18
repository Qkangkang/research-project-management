package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfCallbackLogEntity;

import java.util.Optional;

/**
 * {@code wf_callback_log} 审批回调幂等仓储（T-WF-007）。
 *
 * <p>{@code request_id} 唯一（uk_wf_callback_request），用于回调去重：
 * 先按 request_id 查询，未命中才执行业务回调并落日志。</p>
 */
public interface WfCallbackLogRepository {

    /**
     * 插入回调日志。
     *
     * <p>重复 {@code request_id} 由数据库唯一约束拒绝（TC-APP-023 的最终防线）。</p>
     *
     * @param log 待插入的回调日志 PO
     */
    void insert(WfCallbackLogEntity log);

    /**
     * 按请求幂等键查询回调日志。
     *
     * @param requestId 请求幂等键
     * @return 已处理的回调日志；未处理返回 {@link Optional#empty()}
     */
    Optional<WfCallbackLogEntity> findByRequestId(String requestId);
}
