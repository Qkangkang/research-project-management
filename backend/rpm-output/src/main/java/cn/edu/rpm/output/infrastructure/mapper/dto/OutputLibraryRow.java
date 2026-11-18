package cn.edu.rpm.output.infrastructure.mapper.dto;

import java.time.LocalDate;

/**
 * 全院成果库检索扁平行（T-OUT-014；MyBatis 结果映射用）。
 *
 * <p>使用 JavaBean 而非 record，保证 MyBatis setter 映射稳定
 * （不依赖编译参数 {@code -parameters}）。</p>
 */
public class OutputLibraryRow {

    private String id;

    private String outputType;

    private String title;

    private String doi;

    private String patentNo;

    private String ownerId;

    private String deptId;

    private LocalDate completedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getPatentNo() {
        return patentNo;
    }

    public void setPatentNo(String patentNo) {
        this.patentNo = patentNo;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public LocalDate getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDate completedAt) {
        this.completedAt = completedAt;
    }
}
