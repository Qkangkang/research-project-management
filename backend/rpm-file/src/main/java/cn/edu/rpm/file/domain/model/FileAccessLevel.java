package cn.edu.rpm.file.domain.model;

/**
 * 文件业务引用访问级别（V01 {@code file_ref.ck_file_ref_access}）。
 *
 * <p>{@code RESTRICTED} 附件仍必须经过 {@code FileAccessService} 业务授权；
 * 级别只用于展示与后续更细粒度策略，不替代统一授权判定。</p>
 */
public enum FileAccessLevel {

    /** 院内可见。 */
    INTERNAL,

    /** 项目内可见。 */
    PROJECT,

    /** 受限（需额外字段级/角色授权）。 */
    RESTRICTED,

    /** 公开。 */
    PUBLIC
}
