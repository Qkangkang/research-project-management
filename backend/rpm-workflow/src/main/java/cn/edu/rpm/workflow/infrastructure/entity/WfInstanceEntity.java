package cn.edu.rpm.workflow.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code wf_instance} 审批实例 PO（T-WF-004）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL，承载一次业务审批的
 * 实例主档。关键约束：</p>
 * <ul>
 *   <li>部分唯一索引 {@code uk_wf_instance_running (biz_type, biz_id, biz_version_id)
 *       WHERE status = 'RUNNING'}：同一业务版本同时只允许一个运行中实例
 *       （并发提交的第二请求由数据库兜底拒绝，TC-APP-009）；</li>
 *   <li>终态（APPROVED / REJECTED / RETURNED / CANCELLED / FAILED）后不再受该索引约束，
 *       同一业务版本可再次发起审批。任务拆解中的 {@code COMPLETED} 为 Flowable 侧
 *       流程实例状态，本表以业务审批状态为准（无 COMPLETED 枚举值）。</li>
 * </ul>
 */
@TableName("wf_instance")
public class WfInstanceEntity extends BaseEntity {

    /** 实例状态：运行中（uk_wf_instance_running 仅约束该状态）。 */
    public static final String STATUS_RUNNING = "RUNNING";

    /** 实例状态：已通过（终态）。 */
    public static final String STATUS_APPROVED = "APPROVED";

    /** 实例状态：已拒绝（终态）。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 实例状态：已退回（终态）。 */
    public static final String STATUS_RETURNED = "RETURNED";

    /** 实例状态：已取消（终态）。 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 实例状态：处理失败（终态）。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 业务类型（APPLICATION / EXPENSE / CHANGE / ACCEPTANCE / HANDOVER /
     *  MILESTONE_DELAY / WAIVER / SPECIAL_APPROVAL / ARCHIVE_CORRECTION / EXPERT_REVIEW，
     *  以 V06/V07 约束为准）。 */
    @TableField("biz_type")
    private String bizType;

    /** 业务对象 ID（如申报主档 ID）。 */
    @TableField("biz_id")
    private String bizId;

    /** 本次审批针对的业务版本 ID（如 application_version.id）。 */
    @TableField("biz_version_id")
    private String bizVersionId;

    /** 绑定的模板版本 ID（wf_template_version.id）；运行中实例不随模板新版本变化。 */
    @TableField("template_version_id")
    private String templateVersionId;

    /** Flowable 流程实例 ID（uk_wf_instance_flowable；由适配器启动后回填）。 */
    @TableField("flowable_instance_id")
    private String flowableInstanceId;

    /** 审批实例状态：RUNNING / APPROVED / REJECTED / RETURNED / CANCELLED / FAILED。 */
    @TableField("status")
    private String status;

    /** 发起人用户 ID（iam_user.id）。 */
    @TableField("initiator_id")
    private String initiatorId;

    /** 启动时刻（默认 now()）。 */
    @TableField("started_at")
    private OffsetDateTime startedAt;

    /** 结束时刻；运行中为空。 */
    @TableField("ended_at")
    private OffsetDateTime endedAt;

    /**
     * 回填 Flowable 流程实例 ID（启动成功后调用）。
     *
     * @param flowableInstanceId Flowable 流程实例 ID
     */
    public void attachFlowableInstance(String flowableInstanceId) {
        if (flowableInstanceId == null || flowableInstanceId.isBlank()) {
            throw new IllegalArgumentException("flowableInstanceId 不能为空");
        }
        this.flowableInstanceId = flowableInstanceId;
    }

    /**
     * 结束实例：RUNNING → 终态，并记录结束时刻。
     *
     * <p>显式状态迁移方法，终态限定为 APPROVED / REJECTED / RETURNED / CANCELLED / FAILED；
     * 已处于相同终态时幂等返回（不改变结束时刻）。</p>
     *
     * @param terminalStatus 终态
     * @param at             结束时刻
     * @throws IllegalArgumentException 非法终态
     * @throws IllegalStateException    当前状态既非 RUNNING 也非目标终态
     */
    public void finish(String terminalStatus, OffsetDateTime at) {
        boolean legalTerminal = STATUS_APPROVED.equals(terminalStatus)
                || STATUS_REJECTED.equals(terminalStatus)
                || STATUS_RETURNED.equals(terminalStatus)
                || STATUS_CANCELLED.equals(terminalStatus)
                || STATUS_FAILED.equals(terminalStatus);
        if (!legalTerminal) {
            throw new IllegalArgumentException("非法审批终态: " + terminalStatus);
        }
        if (terminalStatus.equals(status)) {
            return;
        }
        if (!STATUS_RUNNING.equals(status)) {
            throw new IllegalStateException("实例已处于终态 " + status + "，不能再转为 " + terminalStatus);
        }
        this.status = terminalStatus;
        this.endedAt = at;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getBizVersionId() {
        return bizVersionId;
    }

    public void setBizVersionId(String bizVersionId) {
        this.bizVersionId = bizVersionId;
    }

    public String getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(String templateVersionId) {
        this.templateVersionId = templateVersionId;
    }

    public String getFlowableInstanceId() {
        return flowableInstanceId;
    }

    public void setFlowableInstanceId(String flowableInstanceId) {
        this.flowableInstanceId = flowableInstanceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(String initiatorId) {
        this.initiatorId = initiatorId;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }
}
