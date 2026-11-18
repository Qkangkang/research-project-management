package cn.edu.rpm.workflow.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import cn.edu.rpm.workflow.domain.dto.ApprovalAction;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code wf_task} 审批任务 PO（T-WF-005）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01/V04 基线 DDL：</p>
 * <ul>
 *   <li>{@code assignee_id} + {@code status} 走 idx_wf_task_assignee_status 查询待办；</li>
 *   <li>{@code version} 由 {@link BaseEntity} 的乐观锁承载，同一任务只能被一个处理人完成
 *       （TC-APP-024：携带旧 expectedVersion 更新返回 0 行，由应用服务映射 RPM-WF-004）；</li>
 *   <li>{@code action_request_id} 唯一（部分唯一索引），保证同一 request_id 重复处理幂等
 *       （TC-APP-023）；</li>
 *   <li>V04 追加 {@code exception_reason}/{@code exception_at}，并允许异常任务
 *       {@code assignee_id} 为空。</li>
 * </ul>
 */
@TableName("wf_task")
public class WfTaskEntity extends BaseEntity {

    /** 任务状态：待处理。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 任务状态：处理中。 */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /** 任务状态：已通过。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 任务状态：已退回。 */
    public static final String STATUS_RETURNED = "RETURNED";

    /** 任务状态：已拒绝。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 任务状态：已取消。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 任务状态：已超时。 */
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    /** 任务状态：异常（V04：无审批人/规则执行失败等）。 */
    public static final String STATUS_EXCEPTION = "EXCEPTION";

    /** 处理动作：通过。 */
    public static final String ACTION_APPROVED = "APPROVED";

    /** 处理动作：退回。 */
    public static final String ACTION_RETURNED = "RETURNED";

    /** 处理动作：拒绝。 */
    public static final String ACTION_REJECTED = "REJECTED";

    /** 处理动作：取消。 */
    public static final String ACTION_CANCELLED = "CANCELLED";

    /** 处理动作：超时。 */
    public static final String ACTION_TIMEOUT = "TIMEOUT";

    /** 所属审批实例 ID（wf_instance.id）。 */
    @TableField("instance_id")
    private String instanceId;

    /** 节点 key（模板 definition 中的节点标识）。 */
    @TableField("node_key")
    private String nodeKey;

    /** 节点名称。 */
    @TableField("node_name")
    private String nodeName;

    /** 处理人用户 ID；异常任务可为空（V04）。 */
    @TableField("assignee_id")
    private String assigneeId;

    /** 代理来源用户 ID（委托人）；非代理任务为空。 */
    @TableField("delegated_from")
    private String delegatedFrom;

    /** 任务状态：PENDING / PROCESSING / APPROVED / RETURNED / REJECTED /
     *  CANCELLED / TIMEOUT / EXCEPTION。 */
    @TableField("status")
    private String status;

    /** 处理时限；为空表示不设时限。 */
    @TableField("due_at")
    private OffsetDateTime dueAt;

    /** 认领时间。 */
    @TableField("claimed_at")
    private OffsetDateTime claimedAt;

    /** 完成时间。 */
    @TableField("completed_at")
    private OffsetDateTime completedAt;

    /** 处理动作：APPROVED / RETURNED / REJECTED / CANCELLED / TIMEOUT / EXCEPTION_RETRY。 */
    @TableField("action")
    private String action;

    /** 处理意见（最长 2048）。 */
    @TableField("comment")
    private String comment;

    /** 处理请求幂等键（uk_wf_task_action_request，部分唯一索引）。 */
    @TableField("action_request_id")
    private String actionRequestId;

    /** 审批轮次（退回补正后重新提交递增，ck_wf_task_round 要求 &gt;= 1）。 */
    @TableField("round_no")
    private Integer roundNo;

    /** 异常原因（V04：无审批人/规则执行失败/超时等）。 */
    @TableField("exception_reason")
    private String exceptionReason;

    /** 异常发生时刻（V04）。 */
    @TableField("exception_at")
    private OffsetDateTime exceptionAt;

    /**
     * 认领任务：PENDING → PROCESSING，并记录认领时刻（TC-APP-019）。
     *
     * @param at 认领时刻
     * @throws IllegalStateException 当前状态不是 PENDING
     */
    public void claim(OffsetDateTime at) {
        if (!STATUS_PENDING.equals(status)) {
            throw new IllegalStateException("仅待处理任务可认领，当前状态: " + status);
        }
        this.status = STATUS_PROCESSING;
        this.claimedAt = at;
    }

    /**
     * 完成任务：PENDING/PROCESSING → 动作对应状态，写入意见与幂等键。
     *
     * <p>显式状态迁移方法，禁止调用方直接 {@code setStatus}（CLAUDE.local 规则 5）。
     * 乐观锁由 {@code updateById} 的 {@code version} 条件保证（TC-APP-024）。</p>
     *
     * @param action    处理动作
     * @param comment   处理意见
     * @param requestId 请求幂等键（32 位无连字符文本）
     * @param at        完成时刻
     * @throws IllegalStateException 当前状态不可处理
     */
    public void complete(ApprovalAction action, String comment, String requestId, OffsetDateTime at) {
        if (!STATUS_PENDING.equals(status) && !STATUS_PROCESSING.equals(status)) {
            throw new IllegalStateException("任务不可处理，当前状态: " + status);
        }
        this.status = action.name();
        this.action = action.name();
        this.comment = comment;
        this.actionRequestId = requestId;
        this.completedAt = at;
    }

    /**
     * 取消任务：PENDING/PROCESSING → CANCELLED（TC-APP-027）。
     *
     * @param at 取消时刻
     * @throws IllegalStateException 当前状态不可取消
     */
    public void cancel(OffsetDateTime at) {
        if (!STATUS_PENDING.equals(status) && !STATUS_PROCESSING.equals(status)) {
            throw new IllegalStateException("任务不可取消，当前状态: " + status);
        }
        this.status = STATUS_CANCELLED;
        this.action = ACTION_CANCELLED;
        this.completedAt = at;
    }

    /**
     * 重派任务：TIMEOUT/EXCEPTION → PENDING，并更换处理人（TC-APP-026）。
     *
     * <p>原处理人留痕由审计日志承载（审计装配在后续批次接入），
     * 本方法不覆盖历史任务的 {@code completed_at}。</p>
     *
     * @param newAssigneeId 新处理人用户 ID（32 位无连字符文本）
     * @param at            重派时刻（写入 {@code updated_at} 由审计填充器负责）
     * @throws IllegalStateException 当前状态不可重派
     */
    public void reassign(String newAssigneeId, OffsetDateTime at) {
        if (!STATUS_TIMEOUT.equals(status) && !STATUS_EXCEPTION.equals(status)) {
            throw new IllegalStateException("仅超时或异常任务可重派，当前状态: " + status);
        }
        if (newAssigneeId == null || newAssigneeId.isBlank()) {
            throw new IllegalArgumentException("新处理人不能为空");
        }
        this.assigneeId = newAssigneeId;
        this.delegatedFrom = null;
        this.status = STATUS_PENDING;
        this.exceptionAt = null;
        setUpdatedAt(at);
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getDelegatedFrom() {
        return delegatedFrom;
    }

    public void setDelegatedFrom(String delegatedFrom) {
        this.delegatedFrom = delegatedFrom;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(OffsetDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(OffsetDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getActionRequestId() {
        return actionRequestId;
    }

    public void setActionRequestId(String actionRequestId) {
        this.actionRequestId = actionRequestId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getExceptionReason() {
        return exceptionReason;
    }

    public void setExceptionReason(String exceptionReason) {
        this.exceptionReason = exceptionReason;
    }

    public OffsetDateTime getExceptionAt() {
        return exceptionAt;
    }

    public void setExceptionAt(OffsetDateTime exceptionAt) {
        this.exceptionAt = exceptionAt;
    }
}
