package cn.edu.rpm.workflow.infrastructure.repository;

import java.time.OffsetDateTime;

/**
 * 待办任务联表投影（T-WF-009；{@code wf_task} JOIN {@code wf_instance}）。
 *
 * <p>仅用于待办分页查询：任务行补充实例的 {@code biz_type} / {@code biz_id}，
 * 避免逐条回查实例造成 N+1。MyBatis 通过列别名映射本类 setter。</p>
 */
public class WorkflowTaskRow {

    /** 任务 ID。 */
    private String taskId;

    /** 实例 ID。 */
    private String instanceId;

    /** 业务类型。 */
    private String bizType;

    /** 业务对象 ID。 */
    private String bizId;

    /** 节点 key。 */
    private String nodeKey;

    /** 节点名称。 */
    private String nodeName;

    /** 处理人用户 ID。 */
    private String assigneeId;

    /** 委托人用户 ID。 */
    private String delegatedFrom;

    /** 任务状态。 */
    private String status;

    /** 处理时限。 */
    private OffsetDateTime dueAt;

    /** 审批轮次。 */
    private Integer roundNo;

    /** 乐观锁版本。 */
    private Integer version;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
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

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
