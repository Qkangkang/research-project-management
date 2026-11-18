package cn.edu.rpm.output.infrastructure.mapper.param;

/**
 * 全院成果库检索 SQL 参数（T-OUT-014；技术文档 09 §5.1）。
 *
 * <p>使用 JavaBean 以便 MyBatis 动态 SQL 通过 getter 访问属性；
 * 分页由仓储计算 {@code offset = (page - 1) * size}，SQL 只接收 size/offset。</p>
 */
public class OutputLibraryQueryParam {

    private String keyword;

    private String outputType;

    private String deptId;

    private Integer year;

    private int size;

    private int offset;

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
