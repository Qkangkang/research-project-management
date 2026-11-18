package cn.edu.rpm.output.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@code output_version} 不可变成果版本 PO（T-OUT-002）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.5 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code uk_output_version_no (output_id, version_no)}：同一成果内版本号唯一，从 1 递增；</li>
 *   <li>{@code ck_output_version_hash}：{@code content_hash} 为 64 位 SHA-256 十六进制；</li>
 *   <li>{@code ck_output_version_no}：{@code version_no >= 1}。</li>
 * </ul>
 *
 * <p>版本行一经写入不得原地修改；修订生成新版本，旧版本与旧审核意见保留。
 * {@code metadata} 为规范化 JSON 文本（jsonb 列）。</p>
 */
@TableName("output_version")
public class OutputVersionEntity {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    /** 主键；插入时由 MyBatis-Plus 生成 32 位 uuid 文本。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属成果 ID（research_output.id，ON DELETE RESTRICT）。 */
    @TableField("output_id")
    private String outputId;

    /** 版本号，从 1 递增（uk_output_version_no）。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 规范化元数据 JSON 文本（jsonb）。 */
    @TableField("metadata")
    private String metadata;

    /** 元数据内容 SHA-256（64 位十六进制）。 */
    @TableField("content_hash")
    private String contentHash;

    /** 创建人用户 ID（iam_user.id）。 */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间（timestamptz）。 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /**
     * 构造新的成果版本行（调用方负责持久化）。
     *
     * @param outputId    所属成果 ID（32 位 uuid 文本）
     * @param versionNo   版本号（从 1 起）
     * @param metadata    规范化元数据 JSON 文本
     * @param contentHash 元数据 SHA-256（64 位十六进制）
     * @param createdBy   创建人用户 ID（32 位 uuid 文本）
     * @param createdAt   创建时间
     * @return 版本 PO
     */
    public static OutputVersionEntity create(String outputId, int versionNo, String metadata,
                                             String contentHash, String createdBy,
                                             OffsetDateTime createdAt) {
        OutputVersionEntity version = new OutputVersionEntity();
        version.id = UUID.randomUUID().toString().replace("-", "");
        version.outputId = requireText(outputId, "outputId");
        if (versionNo < 1) {
            throw new IllegalArgumentException("versionNo 必须从 1 开始");
        }
        version.versionNo = versionNo;
        version.metadata = Objects.requireNonNull(metadata, "metadata 不能为空");
        if (contentHash == null || !SHA256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash 必须为 64 位十六进制 SHA-256");
        }
        version.contentHash = contentHash;
        version.createdBy = requireText(createdBy, "createdBy");
        version.createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        return version;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOutputId() {
        return outputId;
    }

    public void setOutputId(String outputId) {
        this.outputId = outputId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
