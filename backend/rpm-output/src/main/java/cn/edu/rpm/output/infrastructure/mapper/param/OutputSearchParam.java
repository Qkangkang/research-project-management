package cn.edu.rpm.output.infrastructure.mapper.param;

/**
 * 「我的成果 + 已公开成果」检索 SQL 参数（T-OUT-014；TC-OUT-014）。
 *
 * <p>{@code ownerId} 固定注入当前请求人，SQL 条件为
 * {@code owner_id = #{ownerId} OR status = 'APPROVED'}，保证他人未公开成果不可见。</p>
 */
public class OutputSearchParam {

    private String ownerId;

    private String keyword;

    private String outputType;

    private String status;

    private String deptId;

    private Integer year;

    private int size;

    private int offset;

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
