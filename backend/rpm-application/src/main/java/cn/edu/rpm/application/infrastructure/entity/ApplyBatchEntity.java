package cn.edu.rpm.application.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code apply_batch} 申报批次 PO（T-APP-001）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code uk_apply_batch_no (batch_no)}：批次编号全局唯一；</li>
 *   <li>{@code idx_apply_batch_year_status (year, status)}：年度开放批次列表查询；</li>
 *   <li>{@code ck_apply_batch_status}：DRAFT / OPEN / CLOSED / ARCHIVED；</li>
 *   <li>{@code ck_apply_batch_time}：{@code end_at > start_at}。</li>
 * </ul>
 *
 * <p>{@code rules} 为 jsonb 文本（JDBC URL 的 {@code stringtype=unspecified} 使 String
 * 参数可直接写入 jsonb 列，读取时 pgjdbc 返回 JSON 文本）。审计字段由
 * {@link BaseEntity} 承载。</p>
 */
@TableName("apply_batch")
public class ApplyBatchEntity extends BaseEntity {

    /** 批次状态：草稿（未开放）。 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 批次状态：开放申报。 */
    public static final String STATUS_OPEN = "OPEN";

    /** 批次状态：已关闭（到点自动关闭或人工关闭）。 */
    public static final String STATUS_CLOSED = "CLOSED";

    /** 批次状态：已归档。 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 批次编号；全局唯一（uk_apply_batch_no）。 */
    @TableField("batch_no")
    private String batchNo;

    /** 申报年度。 */
    @TableField("year")
    private Integer year;

    /** 批次名称。 */
    @TableField("name")
    private String name;

    /** 申报开放起始时刻。 */
    @TableField("start_at")
    private OffsetDateTime startAt;

    /** 申报截止时刻；必须晚于 {@code start_at}（ck_apply_batch_time）。 */
    @TableField("end_at")
    private OffsetDateTime endAt;

    /** 批次状态：DRAFT / OPEN / CLOSED / ARCHIVED（ck_apply_batch_status）。 */
    @TableField("status")
    private String status;

    /** 批次规则（jsonb 文本，如限项数、可申报项目类型）。 */
    @TableField("rules")
    private String rules;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OffsetDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(OffsetDateTime startAt) {
        this.startAt = startAt;
    }

    public OffsetDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(OffsetDateTime endAt) {
        this.endAt = endAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }
}
