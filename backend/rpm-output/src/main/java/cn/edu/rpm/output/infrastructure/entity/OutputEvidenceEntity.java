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
 * {@code output_evidence} 成果证明材料 PO（T-OUT-003）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.5 与 V01 基线 DDL。关键约束：</p>
 * <ul>
 *   <li>{@code file_id} 外键指向 {@code file_object}，文件本体不落业务表；</li>
 *   <li>{@code ck_output_evidence_sha}：{@code sha256} 为空或 64 位十六进制；</li>
 *   <li>{@code idx_output_evidence_version}：按版本查询证据。</li>
 * </ul>
 */
@TableName("output_evidence")
public class OutputEvidenceEntity {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    /** 主键；插入时由 MyBatis-Plus 生成 32 位 uuid 文本。 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属成果版本 ID（output_version.id，ON DELETE RESTRICT）。 */
    @TableField("output_version_id")
    private String outputVersionId;

    /** 文件对象 ID（file_object.id，ON DELETE RESTRICT）。 */
    @TableField("file_id")
    private String fileId;

    /** 证据类型（如 PROOF；由成果登记链路按业务默认值写入）。 */
    @TableField("evidence_type")
    private String evidenceType;

    /** 文件内容 SHA-256（原样保存，可空）。 */
    @TableField("sha256")
    private String sha256;

    /** 创建时间（timestamptz）。 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /**
     * 构造证据行。
     *
     * @param outputVersionId 成果版本 ID（32 位 uuid 文本）
     * @param fileId          文件对象 ID（32 位 uuid 文本）
     * @param evidenceType    证据类型
     * @param sha256          文件内容 SHA-256（可空）
     * @param createdAt       创建时间
     * @return 证据 PO
     */
    public static OutputEvidenceEntity create(String outputVersionId, String fileId,
                                              String evidenceType, String sha256,
                                              OffsetDateTime createdAt) {
        OutputEvidenceEntity evidence = new OutputEvidenceEntity();
        evidence.id = UUID.randomUUID().toString().replace("-", "");
        evidence.outputVersionId = requireText(outputVersionId, "outputVersionId");
        evidence.fileId = requireText(fileId, "fileId");
        evidence.evidenceType = requireText(evidenceType, "evidenceType");
        if (sha256 != null && !SHA256_HEX.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 必须为 64 位十六进制");
        }
        evidence.sha256 = sha256;
        evidence.createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        return evidence;
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

    public String getOutputVersionId() {
        return outputVersionId;
    }

    public void setOutputVersionId(String outputVersionId) {
        this.outputVersionId = outputVersionId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
