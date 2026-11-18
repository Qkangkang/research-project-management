package cn.edu.rpm.project.application.dto;

/**
 * 项目列表查询参数（T-PRJ-011 / T-PRJ-017；技术文档 04 §3.6）。
 *
 * <p>对应 {@code GET /projects} 的 Query 参数：
 * {@code keyword/projectType/deptId/piId/status/year/page/size}。
 * {@code page} 从 1 开始，{@code size} 默认 20、上限 200（与《04》§1.2 一致）；
 * 非法分页参数在此归一，避免把负数/超大值带入 SQL。</p>
 *
 * <p>{@code year} 为项目年度：优先取 {@code project.start_date} 的年份，
 * 未定开始日期时取 {@code approved_at} 的年份（技术文档未细化，按此口径落地并在
 * Batch-11 报告登记）。{@code keyword} 按项目编号或名称模糊匹配，通配符在此转义。</p>
 */
public class ProjectListQuery {

    /** 默认每页条数。 */
    public static final int DEFAULT_SIZE = 20;

    /** 每页条数上限。 */
    public static final int MAX_SIZE = 200;

    private String keyword;

    private String projectType;

    private String deptId;

    private String piId;

    private String status;

    private Integer year;

    private int page = 1;

    private int size = DEFAULT_SIZE;

    public String getKeyword() {
        return keyword;
    }

    /**
     * 设置关键字（去首尾空格并转义 LIKE 通配符，按字面量包含匹配）。
     *
     * @param keyword 项目编号/名称关键字；空白视为未设置
     */
    public void setKeyword(String keyword) {
        this.keyword = escapeLike(blankToNull(keyword));
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = blankToNull(projectType);
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = blankToNull(deptId);
    }

    public String getPiId() {
        return piId;
    }

    public void setPiId(String piId) {
        this.piId = blankToNull(piId);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = blankToNull(status);
    }

    public Integer getYear() {
        return year;
    }

    /**
     * 设置项目年度。
     *
     * @param year 四位年份；越界值视为未设置
     */
    public void setYear(Integer year) {
        this.year = year != null && year >= 1900 && year <= 9999 ? year : null;
    }

    public int getPage() {
        return page;
    }

    /**
     * 设置页码。
     *
     * @param page 页码（小于 1 归一为 1）
     */
    public void setPage(int page) {
        this.page = Math.max(page, 1);
    }

    public int getSize() {
        return size;
    }

    /**
     * 设置每页条数。
     *
     * @param size 每页条数（小于 1 归一为默认值，超过 200 截断为 200）
     */
    public void setSize(int size) {
        this.size = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /**
     * SQL 偏移量。
     *
     * @return {@code (page - 1) * size}
     */
    public long getOffset() {
        return (long) (page - 1) * size;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
