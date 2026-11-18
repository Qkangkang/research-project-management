package cn.edu.rpm.output.infrastructure.mapper.dto;

import java.time.LocalDate;

/**
 * 「我的成果 + 已公开成果」检索扁平行（T-OUT-014；TC-OUT-014）。
 *
 * <p>查询条件固定为 {@code owner_id = 当前用户 OR status = 'APPROVED'}，
 * 保证他人未公开成果不进入结果。</p>
 */
public class OutputSearchRow {

    private String id;

    private String outputType;

    private String title;

    private String doi;

    private String patentNo;

    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
