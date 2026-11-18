package cn.edu.rpm.file.domain.model;

/**
 * 文件对象状态（V01 {@code file_object.ck_file_object_status}）。
 *
 * <p>状态机：{@code UPLOADING → QUARANTINED → AVAILABLE}；静态校验失败转
 * {@code REJECTED}；业务允许逻辑删除后转 {@code DELETED}。业务模块只允许引用
 * {@code AVAILABLE} 文件（技术文档 08 §4.2）。</p>
 */
public enum FileStatus {

    /** 上传中。 */
    UPLOADING,

    /** 已上传待扫描/待校验，禁止业务引用。 */
    QUARANTINED,

    /** 可用，允许业务绑定与下载。 */
    AVAILABLE,

    /** 校验或扫描拒绝，禁止业务引用。 */
    REJECTED,

    /** 逻辑删除。 */
    DELETED
}
