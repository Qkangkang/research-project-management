package cn.edu.rpm.iam.domain.model;

/**
 * 利益冲突声明状态常量（{@code coi_declaration.status}，ck_coi_status）。
 *
 * <p>只有已处置（{@link #CONFIRMED} 回避已确认 / {@link #EXEMPTED} 已获豁免）
 * 的声明才算冲突已处理；{@link #DECLARED} 仍未处置，评审提交必须阻断
 * （TC-IAM-012 / TC-IAM-013）。</p>
 */
public final class CoiDeclarationStatus {

    /** 已声明，待管理员处置。 */
    public static final String DECLARED = "DECLARED";

    /** 已确认回避。 */
    public static final String CONFIRMED = "CONFIRMED";

    /** 已豁免（无需回避）。 */
    public static final String EXEMPTED = "EXEMPTED";

    /** 已关闭。 */
    public static final String CLOSED = "CLOSED";

    private CoiDeclarationStatus() {
    }

    /**
     * 判断声明是否已完成处置（冲突已有结论）。
     *
     * @param status 声明状态
     * @return {@code CONFIRMED} 或 {@code EXEMPTED} 返回 {@code true}
     */
    public static boolean isResolved(String status) {
        return CONFIRMED.equals(status) || EXEMPTED.equals(status);
    }
}
