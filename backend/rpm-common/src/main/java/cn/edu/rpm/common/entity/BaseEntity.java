package cn.edu.rpm.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 业务 PO 通用审计字段基类（T-DB-008）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§1 原则 4：所有业务表包含
 * {@code created_at} / {@code updated_at} / {@code created_by} / {@code updated_by} / {@code version}。
 * 审计字段由 rpm-boot 的 {@code AuditMetaObjectHandler} 在 INSERT/UPDATE 时自动填充，
 * {@code version} 由乐观锁拦截器（OptimisticLockerInnerInterceptor）做并发版本控制。</p>
 *
 * <p>类型约定：</p>
 * <ul>
 *   <li>主键 {@code id} 为数据库 {@code uuid} 列；Java 侧用 {@link String} 承载，
 *       {@link IdType#ASSIGN_UUID} 在插入时生成 32 位十六进制串（PostgreSQL 可直接解析为 uuid）。
 *       JDBC URL 必须带 {@code stringtype=unspecified}，使 String 参数可写入 uuid 列
 *       （dev 默认 URL 已带；prod 由 {@code DB_URL} 环境变量组装，见 application-prod.yml 注释）。</li>
 *   <li>时间统一 {@link OffsetDateTime}（与 AuditMetaObjectHandler 填充类型一致，对应 timestamptz）。</li>
 *   <li>操作人为用户 ID 字符串（按人员 ID 判断，不按账号名；后台任务不写操作人）。</li>
 *   <li>{@code version} 初始为 0（与 DDL {@code DEFAULT 0} 一致），更新时充当乐观锁条件。</li>
 * </ul>
 *
 * <p>审计日志（audit_log）与资金事件（fund_event）只追加、不继承本基类。</p>
 */
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键；插入时由 MyBatis-Plus 生成 uuid（无连字符 32 位十六进制）。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建时间；INSERT 时由审计填充器写入，业务代码无需赋值。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 最后更新时间；INSERT/UPDATE 时由审计填充器在字段为 null 时写入。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /** 创建人（用户 ID）；INSERT 时由审计填充器写入。 */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /** 最后更新人（用户 ID）；INSERT/UPDATE 时由审计填充器在字段为 null 时写入。 */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 乐观锁版本号；更新语句以此字段旧值作为并发条件，禁止业务代码手工修改。 */
    @Version
    @TableField(value = "version")
    private Integer version = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
