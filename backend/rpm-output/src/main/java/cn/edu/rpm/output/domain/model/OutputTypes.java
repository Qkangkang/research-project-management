package cn.edu.rpm.output.domain.model;

/**
 * 成果类型常量（与 V01 {@code ck_research_output_type} 取值一致）。
 *
 * <p>领域层为常量的唯一声明处；PO 与模板解析器均引用本类，禁止散落字面量。</p>
 */
public final class OutputTypes {

    /** 论文。 */
    public static final String PAPER = "PAPER";

    /** 专利。 */
    public static final String PATENT = "PATENT";

    /** 软件著作权。 */
    public static final String SOFTWARE = "SOFTWARE";

    /** 标准。 */
    public static final String STANDARD = "STANDARD";

    /** 获奖。 */
    public static final String AWARD = "AWARD";

    /** 报告。 */
    public static final String REPORT = "REPORT";

    /** 其他。 */
    public static final String OTHER = "OTHER";

    private OutputTypes() {
    }
}
