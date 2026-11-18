package cn.edu.rpm.file.domain.model;

/**
 * 文件访问使用的业务权限码常量（与《03-权限工作流与审计设计.md》§1.3 权限矩阵口径一致）。
 *
 * <p>附件下载按所属项目的"查看项目基本概况"能力授权，不再自造独立的文件权限码；
 * 业务模块如已有更细的资源权限，可在调用侧替换等价动作码。</p>
 */
public final class FilePermissionCodes {

    /** 项目查看权限（列表/详情/附件下载共用同一数据范围）。 */
    public static final String PROJECT_VIEW = "project:view";

    private FilePermissionCodes() {
    }
}
