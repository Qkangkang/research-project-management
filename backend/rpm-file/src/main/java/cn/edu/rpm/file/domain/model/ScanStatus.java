package cn.edu.rpm.file.domain.model;

/**
 * 病毒扫描状态（V01 {@code file_object.ck_file_object_scan}）。
 *
 * <p>一期病毒扫描钩子为占位实现（决策 D-04），返回 {@link #SKIPPED} 后文件可直接
 * 转为 {@code AVAILABLE}；二期对接院方扫描服务后返回 {@link #PASSED}/{@link #FAILED}。</p>
 */
public enum ScanStatus {

    /** 待扫描。 */
    PENDING,

    /** 扫描通过。 */
    PASSED,

    /** 扫描失败（病毒/恶意内容）。 */
    FAILED,

    /** 一期占位：未接入扫描服务，按通过处理。 */
    SKIPPED
}
