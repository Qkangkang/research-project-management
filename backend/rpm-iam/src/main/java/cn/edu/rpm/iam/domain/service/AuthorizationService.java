package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ProjectQuery;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.iam.application.dto.ResourceRef;

/**
 * 统一授权服务（技术文档 03 §2.1）。
 *
 * <p>最终许可 = 功能权限 ∩ 数据范围 ∩ 业务状态 ∩ 字段权限 ∩ 职责分离 ∩ 授权有效期；
 * 业务状态由 {@code StateGuard} 在后续批次接入，职责分离由工作流在事务内显式调用
 * {@code SeparationOfDutyGuard}。约定：</p>
 * <ul>
 *   <li>Controller 的 {@code @RequirePermission} 只做功能权限粗筛；应用服务入口必须调用
 *       {@link #check(AuthContext, Action, ResourceRef)} 并携带资源 ID；</li>
 *   <li>仓储查询必须注入 {@link #resolveProjectScope(AuthContext, ProjectQuery)} 的结果，
 *       禁止只靠 Controller 判断；</li>
 *   <li>列表、详情、附件下载、导出、异步任务使用同一授权逻辑（TC-IAM-015 五处同权）。</li>
 * </ul>
 */
public interface AuthorizationService {

    /**
     * 对指定动作与资源做最终授权判定。
     *
     * @param ctx      请求级授权上下文
     * @param action   受控动作（权限码）
     * @param resource 被访问资源的最小引用（数据范围维度）
     * @return 判定结果；拒绝时不泄露资源是否存在
     */
    AuthDecision check(AuthContext ctx, Action action, ResourceRef resource);

    /**
     * 解析项目数据范围，供仓储参数化 SQL 注入（列表/导出/异步任务共用）。
     *
     * @param ctx   请求级授权上下文
     * @param query 数据范围解析参数
     * @return 项目数据范围
     */
    ProjectScope resolveProjectScope(AuthContext ctx, ProjectQuery query);

    /**
     * 按资源类型对返回 DTO 施加字段脱敏。
     *
     * @param vo      待脱敏 DTO（可变 JavaBean）
     * @param ctx     请求级授权上下文
     * @param resource 被访问资源（决定字段策略）
     * @param <T>     DTO 类型
     * @return 脱敏后的同一实例
     */
    <T> T mask(T vo, AuthContext ctx, ResourceRef resource);
}
