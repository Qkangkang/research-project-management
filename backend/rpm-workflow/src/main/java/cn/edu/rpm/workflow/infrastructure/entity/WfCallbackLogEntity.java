package cn.edu.rpm.workflow.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@code wf_callback_log} 审批回调幂等日志 PO（T-WF-007）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL：{@code request_id}
 * 唯一（uk_wf_callback_request），同一任务处理请求重复到达时返回首次结果，
 * 不产生第二条审批记录（TC-APP-023）。</p>
 *
 * <p>该表只有 created_at，没有 updated_at / created_by / updated_by / version
 * （回调日志只追加），因此<b>不继承</b>
 * {@link cn.edu.rpm.common.entity.BaseEntity}；审计主体由 {@code action} 与
 * 关联实例/任务承载。</p>
 */
@TableName("wf_callback_log")
public class WfCallbackLogEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 处理状态：已处理（默认值）。 */
    public static final String STATUS_PROCESSED = "PROCESSED";

    /** 主键；插入时由 MyBatis-Plus 生成 uuid。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 审批实例 ID（wf_instance.id，ON DELETE RESTRICT）。 */
    @TableField("instance_id")
    private String instanceId;

    /** 审批任务 ID（wf_task.id）；实例级回调可为空。 */
    @TableField("task_id")
    private String taskId;

    /** 请求幂等键（uk_wf_callback_request）；重复请求据此去重。 */
    @TableField("request_id")
    private String requestId;

    /** 回调动作：APPROVED / RETURNED / REJECTED / CANCELLED 等。 */
    @TableField("action")
    private String action;

    /** 回调载荷（jsonb 文本，含 ApprovalResult 全量字段）。 */
    @TableField("payload")
    private String payload;

    /** 处理状态，默认 PROCESSED。 */
    @TableField("status")
    private String status;

    /** 处理完成时刻（默认 now()）。 */
    @TableField("processed_at")
    private OffsetDateTime processedAt;

    /** 落库时刻；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
