package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.ApprovalContext;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ExpertContext;
import cn.edu.rpm.iam.application.dto.ProjectQuery;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.FieldPolicyCodes;
import cn.edu.rpm.iam.domain.model.ResourceTypes;

import java.util.Objects;
import java.util.Set;

/**
 * 统一授权服务默认实现（T-IAM-013，技术文档 03 §2.1）。
 *
 * <p>组合关系：</p>
 * <ul>
 *   <li>功能权限：使用 {@link AuthContext#permissionCodes()} 权限快照（登录/角色变更时由
 *       {@link FunctionPermissionResolver} 构建，避免每次鉴权回源）；</li>
 *   <li>数据范围：委托 {@link DataScopeResolver}，临时授权窗口守卫在其内部由
 *       {@link GrantValidityGuard} 复核；</li>
 *   <li>字段脱敏：委托 {@link FieldMaskService}，策略按资源类型映射；</li>
 *   <li>职责分离：对外暴露 {@link #checkApproval(ApprovalContext)} /
 *       {@link #checkExpert(ExpertContext)} 门面，委托 {@link SeparationOfDutyGuard}
 *       （工作流任务处理在事务内调用；也可直接注入守护）。</li>
 * </ul>
 *
 * <p>拒绝一律返回 {@code RPM-COMMON-403} 且不携带任何资源元数据，
 * 防止越权探测（TC-IAM-002 / TC-IAM-003）。</p>
 */
public class DefaultAuthorizationService implements AuthorizationService {

    private final DataScopeResolver dataScopeResolver;

    private final FieldMaskService fieldMaskService;

    private final SeparationOfDutyGuard separationOfDutyGuard;

    public DefaultAuthorizationService(DataScopeResolver dataScopeResolver,
                                       FieldMaskService fieldMaskService,
                                       SeparationOfDutyGuard separationOfDutyGuard) {
        this.dataScopeResolver = Objects.requireNonNull(dataScopeResolver, "dataScopeResolver 不能为空");
        this.fieldMaskService = Objects.requireNonNull(fieldMaskService, "fieldMaskService 不能为空");
        this.separationOfDutyGuard = Objects.requireNonNull(separationOfDutyGuard,
                "separationOfDutyGuard 不能为空");
    }

    @Override
    public AuthDecision check(AuthContext ctx, Action action, ResourceRef resource) {
        Objects.requireNonNull(ctx, "AuthContext 不能为空");
        Objects.requireNonNull(action, "Action 不能为空");
        Objects.requireNonNull(resource, "ResourceRef 不能为空");

        ProjectScope scope = resolveProjectScope(ctx, ProjectQuery.of(action.code(), Set.of(resource.resourceId())));
        boolean grantedForResource = ResourceTypes.PROJECT.equals(resource.resourceType())
                && scope.grantedProjectIds().contains(resource.resourceId());
        if (!ctx.hasPermission(action.code()) && !grantedForResource) {
            return AuthDecision.deny(ErrorCode.COMMON_403);
        }

        boolean withinDataScope = scope.scopeAll()
                || (scope.piId() != null && scope.piId().equals(resource.piId()))
                || (resource.deptId() != null && scope.deptIds().contains(resource.deptId()))
                || scope.grantedProjectIds().contains(resource.resourceId())
                || resource.memberUserIds().contains(ctx.userId());
        if (!withinDataScope) {
            return AuthDecision.deny(ErrorCode.COMMON_403);
        }
        return AuthDecision.allow();
    }

    @Override
    public ProjectScope resolveProjectScope(AuthContext ctx, ProjectQuery query) {
        Objects.requireNonNull(ctx, "AuthContext 不能为空");
        ProjectQuery effectiveQuery = query == null ? ProjectQuery.all() : query;
        return dataScopeResolver.resolve(ctx.userId(), ctx.activeRoleId(), ctx.requestTime(), effectiveQuery);
    }

    @Override
    public <T> T mask(T vo, AuthContext ctx, ResourceRef resource) {
        Set<String> policyCodes = resource == null
                ? Set.of() : FieldPolicyCodes.forResourceType(resource.resourceType());
        return fieldMaskService.mask(vo, ctx, policyCodes);
    }

    /**
     * 审批职责分离门面：委托 {@link SeparationOfDutyGuard#checkApproval(ApprovalContext)}。
     *
     * @param ctx 审批上下文
     * @throws cn.edu.rpm.common.exception.BizException 自审 {@code RPM-WF-003}；PI 兼任财务终审
     *         {@code RPM-WF-006}
     */
    public void checkApproval(ApprovalContext ctx) {
        separationOfDutyGuard.checkApproval(ctx);
    }

    /**
     * 专家利益冲突门面：委托 {@link SeparationOfDutyGuard#checkExpert(ExpertContext)}。
     *
     * @param ctx 专家评审上下文
     * @throws cn.edu.rpm.common.exception.BizException 存在未处置的利益冲突 {@code RPM-WF-006}
     */
    public void checkExpert(ExpertContext ctx) {
        separationOfDutyGuard.checkExpert(ctx);
    }
}
