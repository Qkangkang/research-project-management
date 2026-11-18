package cn.edu.rpm.project.api;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.project.api.dto.ProjectRequests;
import cn.edu.rpm.project.application.dto.ChangeRequestCreated;
import cn.edu.rpm.project.application.dto.ProjectListItem;
import cn.edu.rpm.project.application.dto.ProjectListQuery;
import cn.edu.rpm.project.application.dto.ProjectStateChanged;
import cn.edu.rpm.project.application.service.ChangeRequestAppService;
import cn.edu.rpm.project.application.service.ProjectHandoverAppService;
import cn.edu.rpm.project.application.service.ProjectMemberAppService;
import cn.edu.rpm.project.application.service.ProjectQueryAppService;
import cn.edu.rpm.project.domain.service.MilestoneService;
import cn.edu.rpm.project.domain.service.ProjectLifecycleService;
import cn.edu.rpm.project.domain.service.ProjectRiskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectController} 纯单元测试（T-PRJ-023）。
 *
 * <p>验证请求→命令映射、统一响应载体与未登录 401；业务规则由应用服务测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private ProjectQueryAppService projectQueryAppService;

    @Mock
    private ProjectMemberAppService projectMemberAppService;

    @Mock
    private MilestoneService milestoneService;

    @Mock
    private ProjectRiskService projectRiskService;

    @Mock
    private ProjectLifecycleService projectLifecycleService;

    @Mock
    private ChangeRequestAppService changeRequestAppService;

    @Mock
    private ProjectHandoverAppService projectHandoverAppService;

    private ProjectController controller;

    private AuthContext auth;

    @BeforeEach
    void setUp() {
        controller = new ProjectController(projectQueryAppService, projectMemberAppService,
                milestoneService, projectRiskService, projectLifecycleService,
                changeRequestAppService, projectHandoverAppService);
        auth = new AuthContext(ACTOR_ID, null, "E1001",
                Set.of("project:view", "project:change:create", "project:state:change"),
                Set.of(), Instant.parse("2026-11-18T02:00:00Z"));
        AuthContextHolder.set(auth);
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void listMapsQueryParamsAndReturnsPage() {
        when(projectQueryAppService.list(any(), eq(auth)))
                .thenReturn(PageVO.of(List.<ProjectListItem>of(), 1, 20, 0));

        Result<PageVO<ProjectListItem>> result = controller.list("智能", "YEAR_PLAN", null, null,
                "EXECUTING", 2026, 1, 20);

        ArgumentCaptor<ProjectListQuery> captor = ArgumentCaptor.forClass(ProjectListQuery.class);
        verify(projectQueryAppService).list(captor.capture(), eq(auth));
        assertThat(captor.getValue().getKeyword()).isEqualTo("智能");
        assertThat(captor.getValue().getProjectType()).isEqualTo("YEAR_PLAN");
        assertThat(captor.getValue().getStatus()).isEqualTo("EXECUTING");
        assertThat(captor.getValue().getYear()).isEqualTo(2026);
        assertThat(captor.getValue().getPage()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(20);
        assertThat(result.getData().getItems()).isEmpty();
        assertThat(result.getData().getTotal()).isZero();
    }

    @Test
    void pauseMapsReasonAndReturnsNewStatus() {
        when(projectLifecycleService.pause(eq(PROJECT_ID), eq("经费审计"),
                eq(auth))).thenReturn(new ProjectStateChanged(PROJECT_ID, "SUSPENDED", 4));

        Result<ProjectStateChanged> result = controller.pause(PROJECT_ID,
                new ProjectRequests.StateChangeRequest("经费审计"));

        assertThat(result.getData().status()).isEqualTo("SUSPENDED");
        assertThat(result.getData().version()).isEqualTo(4);
    }

    @Test
    void createChangeMapsRequestAndIdempotencyKey() {
        when(changeRequestAppService.create(eq(PROJECT_ID), any(), eq(auth)))
                .thenReturn(new ChangeRequestCreated("c-1", "v-1", "IN_REVIEW", "CHANGE_PERIOD"));

        Result<ChangeRequestCreated> result = controller.createChange(PROJECT_ID,
                "abcdabcd-abcd-abcd-abcd-abcdabcdabcd",
                new ProjectRequests.CreateChangeRequest("PERIOD", LocalDate.of(2027, 1, 1),
                        Map.of("new_end_date", "2030-05-31"), "设备采购周期延长",
                        List.of()));

        ArgumentCaptor<cn.edu.rpm.project.application.dto.CreateChangeRequestCmd> captor =
                ArgumentCaptor.forClass(
                        cn.edu.rpm.project.application.dto.CreateChangeRequestCmd.class);
        verify(changeRequestAppService).create(eq(PROJECT_ID), captor.capture(), eq(auth));
        assertThat(captor.getValue().changeType()).isEqualTo("PERIOD");
        assertThat(captor.getValue().effectiveDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(captor.getValue().requestId()).isEqualTo(UUID.fromString(
                "abcdabcd-abcd-abcd-abcd-abcdabcdabcd"));
        assertThat(result.getData().status()).isEqualTo("IN_REVIEW");
    }

    @Test
    void unauthenticatedRequestReturns401() {
        AuthContextHolder.clear();

        assertThatThrownBy(() -> controller.detail(PROJECT_ID))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMON_401));
    }
}
