package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.ApprovalContext;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ProjectQuery;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.FieldPolicyCodes;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultAuthorizationService} 纯单元测试（T-IAM-013 先行测试，技术文档 03 §2.1）。
 *
 * <p>覆盖验证用例：</p>
 * <ul>
 *   <li>TC-IAM-001：PI 对本人项目 {@code project:view} → {@code allowed=true, reasonCode=ALLOW}；</li>
 *   <li>TC-IAM-002：缺功能权限 → {@code RPM-COMMON-403}；</li>
 *   <li>TC-IAM-003：数据范围不命中 → {@code RPM-COMMON-403}；</li>
 *   <li>TC-IAM-008：临时授权项目可见、未授权项目拒绝；</li>
 *   <li>TC-IAM-015：列表/详情/附件/导出/异步五处同一范围、判定一致；</li>
 *   <li>自审委托职责分离守护抛 {@code RPM-WF-003}。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DefaultAuthorizationServiceTest {

    private static final UUID USER_ID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final UUID ROLE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER_USER = UUID.fromString("ffffffff-0000-0000-0000-000000000000");
    private static final UUID PROJECT_ID = UUID.fromString("aa111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PROJECT = UUID.fromString("aa222222-2222-2222-2222-222222222222");
    private static final UUID DEPT_ID = UUID.fromString("d1111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_DEPT = UUID.fromString("d2222222-2222-2222-2222-222222222222");
    private static final Instant REQUEST_TIME = Instant.parse("2026-11-17T10:00:00Z");

    @Mock
    private DataScopeResolver dataScopeResolver;

    @Mock
    private FieldMaskService fieldMaskService;

    @Mock
    private SeparationOfDutyGuard separationOfDutyGuard;

    private DefaultAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAuthorizationService(dataScopeResolver, fieldMaskService, separationOfDutyGuard);
    }

    private static AuthContext context(String... permissionCodes) {
        return new AuthContext(USER_ID, ROLE_ID, "E1001", Set.of(permissionCodes), Set.of(), REQUEST_TIME);
    }

    private static ResourceRef ownProject() {
        return ResourceRef.project(PROJECT_ID, USER_ID, DEPT_ID, List.of(USER_ID));
    }

    @Test
    void piCanViewOwnProjectWithAllowReason() {
        when(dataScopeResolver.resolve(eq(USER_ID), eq(ROLE_ID), eq(REQUEST_TIME), any()))
                .thenReturn(new ProjectScope(false, USER_ID, Set.of(), Set.of()));
        AuthContext ctx = context("project:view");

        AuthDecision decision = service.check(ctx, Action.of("project:view"), ownProject());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo("ALLOW");
        assertThat(decision.maskedFields()).isEmpty();
    }

    @Test
    void missingFunctionPermissionIsDeniedWith403EvenWhenDataScopeMatches() {
        when(dataScopeResolver.resolve(eq(USER_ID), eq(ROLE_ID), eq(REQUEST_TIME), any()))
                .thenReturn(new ProjectScope(false, USER_ID, Set.of(), Set.of()));
        AuthContext ctx = context("project:view");

        AuthDecision decision = service.check(ctx, Action.of("fund:expense:submit"), ownProject());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo(ErrorCode.COMMON_403.getCode());
        assertThat(decision.maskedFields()).isEmpty();
    }

    @Test
    void outOfScopeProjectIsDeniedWith403WithoutResourceMetadata() {
        when(dataScopeResolver.resolve(eq(USER_ID), eq(ROLE_ID), eq(REQUEST_TIME), any()))
                .thenReturn(new ProjectScope(false, USER_ID, Set.of(DEPT_ID), Set.of()));
        AuthContext ctx = context("project:view");
        ResourceRef resource = ResourceRef.project(OTHER_PROJECT, OTHER_USER, OTHER_DEPT, List.of(OTHER_USER));

        AuthDecision decision = service.check(ctx, Action.of("project:view"), resource);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("RPM-COMMON-403");
        assertThat(decision.maskedFields()).isEmpty();
    }

    @Test
    void tempGrantedProjectIsAllowedWithoutGlobalPermission() {
        when(dataScopeResolver.resolve(eq(USER_ID), eq(ROLE_ID), eq(REQUEST_TIME), any()))
                .thenReturn(new ProjectScope(false, null, Set.of(), Set.of(PROJECT_ID)));
        AuthContext ctx = context();
        ResourceRef granted = ResourceRef.project(PROJECT_ID, OTHER_USER, OTHER_DEPT, Set.of());

        AuthDecision decision = service.check(ctx, Action.of("project:view"), granted);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo("ALLOW");
    }

    @Test
    void projectOutsideTempGrantIsDenied() {
        when(dataScopeResolver.resolve(eq(USER_ID), eq(ROLE_ID), eq(REQUEST_TIME), any()))
                .thenReturn(new ProjectScope(false, null, Set.of(), Set.of(PROJECT_ID)));
        AuthContext ctx = context();
        ResourceRef other = ResourceRef.project(OTHER_PROJECT, OTHER_USER, OTHER_DEPT, Set.of());

        AuthDecision decision = service.check(ctx, Action.of("project:view"), other);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("RPM-COMMON-403");
    }

    @Test
    void projectMemberIsAllowedViaMembershipDimension() {
        when(dataScopeResolver.resolve(eq(USER_ID), eq(ROLE_ID), eq(REQUEST_TIME), any()))
                .thenReturn(ProjectScope.empty());
        AuthContext ctx = context("project:view");
        ResourceRef memberProject = ResourceRef.project(PROJECT_ID, OTHER_USER, OTHER_DEPT, List.of(USER_ID));

        AuthDecision decision = service.check(ctx, Action.of("project:view"), memberProject);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void fiveEntryPointsShareSameScopeAndRejectOutOfScopeConsistently() {
        ProjectScope scope = new ProjectScope(false, USER_ID, Set.of(DEPT_ID), Set.of());
        when(dataScopeResolver.resolve(eq(USER_ID), eq(ROLE_ID), eq(REQUEST_TIME), any())).thenReturn(scope);
        AuthContext ctx = context("project:list", "project:view", "file:download", "report:export", "sys:job:view");
        ResourceRef outOfScope = ResourceRef.project(OTHER_PROJECT, OTHER_USER, OTHER_DEPT, List.of(OTHER_USER));
        List<String> entryActions = List.of("project:list", "project:view", "file:download", "report:export", "sys:job:view");

        for (String actionCode : entryActions) {
            AuthDecision decision = service.check(ctx, Action.of(actionCode), outOfScope);
            assertThat(decision.allowed()).as("action=%s 必须与其余入口一致拒绝", actionCode).isFalse();
            assertThat(decision.reasonCode()).isEqualTo("RPM-COMMON-403");
            assertThat(service.resolveProjectScope(ctx, ProjectQuery.ofAction(actionCode))).isEqualTo(scope);
        }
        verify(dataScopeResolver, times(10)).resolve(eq(USER_ID), eq(ROLE_ID), eq(REQUEST_TIME), any());
    }

    @Test
    void checkApprovalDelegatesToSeparationOfDutyGuard() {
        ApprovalContext approvalContext = new ApprovalContext(USER_ID, USER_ID, null, null);
        doThrow(new BizException(ErrorCode.WF_003)).when(separationOfDutyGuard).checkApproval(approvalContext);

        assertThatThrownBy(() -> service.checkApproval(approvalContext))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.WF_003));
        verify(separationOfDutyGuard).checkApproval(same(approvalContext));
    }

    @Test
    void maskUsesFinancePolicyForExpenseResource() {
        AuthContext ctx = context();
        Object vo = new Object();
        ResourceRef expense = new ResourceRef(ResourceTypes.EXPENSE, PROJECT_ID, USER_ID, DEPT_ID, Set.of());
        when(fieldMaskService.mask(same(vo), same(ctx), eq(Set.of(FieldPolicyCodes.FINANCE)))).thenReturn(vo);

        Object masked = service.mask(vo, ctx, expense);

        assertThat(masked).isSameAs(vo);
        verify(fieldMaskService).mask(same(vo), same(ctx), eq(Set.of(FieldPolicyCodes.FINANCE)));
    }
}
