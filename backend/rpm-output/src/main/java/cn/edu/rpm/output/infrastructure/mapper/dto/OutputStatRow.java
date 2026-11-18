package cn.edu.rpm.output.infrastructure.mapper.dto;

/**
 * 全院成果统计扁平行（T-OUT-014；TC-OSR-005）。
 *
 * <p>统计口径为 {@code COUNT(DISTINCT research_output.id)}（成果去重数），
 * 不输出项目关联数口径。</p>
 */
public class OutputStatRow {

    private String groupKey;

    private Long outputCount;

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public Long getOutputCount() {
        return outputCount;
    }

    public void setOutputCount(Long outputCount) {
        this.outputCount = outputCount;
    }
}
