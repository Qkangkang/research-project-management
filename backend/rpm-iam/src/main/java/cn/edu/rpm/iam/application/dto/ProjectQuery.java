package cn.edu.rpm.iam.application.dto;

import java.util.Set;
import java.util.UUID;

/**
 * 项目数据范围解析参数（技术文档 03 §2.2 {@code ProjectQuery}）。
 *
 * <p>{@code action} 用于筛选临时授权：仅把 {@code permission_code} 与本次动作一致的
 * 授权项目计入 {@link ProjectScope#grantedProjectIds()}；为空表示不按动作过滤（全量授权项目）。
 * {@code requestedProjectIds} 为调用方显式指定的项目集，供仓储在参数化 SQL 中追加
 * {@code p.id = ANY(:requestedProjectIds)} 过滤（授权服务不据此放大范围）。</p>
 *
 * @param action              本次动作权限码；可为空
 * @param requestedProjectIds 调用方请求的项目 ID 集合；空集合表示不限
 */
public record ProjectQuery(String action, Set<UUID> requestedProjectIds) {

    /** 规范化请求项目集合。 */
    public ProjectQuery {
        requestedProjectIds = requestedProjectIds == null ? Set.of() : Set.copyOf(requestedProjectIds);
    }

    /**
     * 不限制动作与项目集的解析参数（用于列表等全量场景）。
     *
     * @return 查询参数
     */
    public static ProjectQuery all() {
        return new ProjectQuery(null, Set.of());
    }

    /**
     * 仅按动作筛选临时授权的解析参数。
     *
     * @param action 动作权限码
     * @return 查询参数
     */
    public static ProjectQuery ofAction(String action) {
        return new ProjectQuery(action, Set.of());
    }

    /**
     * 同时指定动作与请求项目集。
     *
     * @param action              动作权限码
     * @param requestedProjectIds 请求项目 ID 集合
     * @return 查询参数
     */
    public static ProjectQuery of(String action, Set<UUID> requestedProjectIds) {
        return new ProjectQuery(action, requestedProjectIds);
    }
}
