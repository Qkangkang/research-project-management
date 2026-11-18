package cn.edu.rpm.application.infrastructure.adapter;

import cn.edu.rpm.application.domain.port.ApplicationWorkflowStart;
import cn.edu.rpm.application.domain.port.WorkflowLaunch;
import cn.edu.rpm.workflow.domain.dto.StartWorkflowCmd;
import cn.edu.rpm.workflow.domain.dto.TemplateRoutingContext;
import cn.edu.rpm.workflow.domain.dto.WorkflowInstance;
import cn.edu.rpm.workflow.domain.port.WorkflowPort;
import cn.edu.rpm.workflow.domain.service.WorkflowTemplateResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowLaunchAdapter} 纯单元测试（T-APP-014 步骤 6）。
 *
 * <p>验证适配器把申报上下文映射为模板路由与 {@link StartWorkflowCmd}，
 * 并在幂等回放时按业务版本读取最近实例；Flowable 自身行为由 rpm-workflow 测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowLaunchAdapterTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaabbbb-cccc-dddd-eeee-ffff00001111");
    private static final UUID VERSION_ID = UUID.fromString("12341234-1234-1234-1234-123412341234");
    private static final UUID DEPT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID APPLICANT_ID = UUID.fromString("11112222-3333-4444-5555-666677778888");
    private static final UUID TEMPLATE_VERSION_ID =
            UUID.fromString("77778888-9999-aaaa-bbbb-ccccddddeeee");
    private static final UUID INSTANCE_ID = UUID.fromString("fedcfedc-fedc-fedc-fedc-fedcfedcfedc");
    private static final OffsetDateTime AT =
            OffsetDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneOffset.ofHours(8));

    @Mock
    private WorkflowTemplateResolver templateResolver;

    @Mock
    private WorkflowPort workflowPort;

    private WorkflowLaunchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WorkflowLaunchAdapter(templateResolver, workflowPort, "applicationApproval");
    }

    @Test
    void startsWorkflowWithResolvedTemplateVersion() {
        when(templateResolver.resolve(any(TemplateRoutingContext.class), eq(AT)))
                .thenReturn(TEMPLATE_VERSION_ID);
        when(workflowPort.start(any())).thenReturn(new WorkflowInstance(
                INSTANCE_ID, "flowable-1", "APPLICATION", APPLICATION_ID, VERSION_ID,
                TEMPLATE_VERSION_ID, APPLICANT_ID, "RUNNING", AT, null));

        WorkflowLaunch launch = adapter.start(new ApplicationWorkflowStart(
                APPLICATION_ID, VERSION_ID, "YEAR_PLAN", DEPT_ID, APPLICANT_ID, AT));

        ArgumentCaptor<TemplateRoutingContext> routingCaptor =
                ArgumentCaptor.forClass(TemplateRoutingContext.class);
        verify(templateResolver).resolve(routingCaptor.capture(), eq(AT));
        assertThat(routingCaptor.getValue().projectType()).isEqualTo("YEAR_PLAN");
        assertThat(routingCaptor.getValue().deptId()).isEqualTo(DEPT_ID);

        ArgumentCaptor<StartWorkflowCmd> startCaptor = ArgumentCaptor.forClass(StartWorkflowCmd.class);
        verify(workflowPort).start(startCaptor.capture());
        StartWorkflowCmd cmd = startCaptor.getValue();
        assertThat(cmd.bizType()).isEqualTo("APPLICATION");
        assertThat(cmd.bizId()).isEqualTo(APPLICATION_ID);
        assertThat(cmd.bizVersionId()).isEqualTo(VERSION_ID);
        assertThat(cmd.templateVersionId()).isEqualTo(TEMPLATE_VERSION_ID);
        assertThat(cmd.processDefinitionKey()).isEqualTo("applicationApproval");
        assertThat(cmd.initiatorId()).isEqualTo(APPLICANT_ID);

        assertThat(launch.instanceId()).isEqualTo(INSTANCE_ID);
        assertThat(launch.status()).isEqualTo("RUNNING");
    }

    @Test
    void findLatestReturnsEmptyWhenNoInstance() {
        when(workflowPort.findInstanceByBiz("APPLICATION", APPLICATION_ID, VERSION_ID))
                .thenReturn(Optional.empty());

        assertThat(adapter.findLatest(APPLICATION_ID, VERSION_ID)).isEmpty();
    }

    @Test
    void findLatestMapsWorkflowInstance() {
        when(workflowPort.findInstanceByBiz("APPLICATION", APPLICATION_ID, VERSION_ID))
                .thenReturn(Optional.of(new WorkflowInstance(
                        INSTANCE_ID, "flowable-1", "APPLICATION", APPLICATION_ID, VERSION_ID,
                        TEMPLATE_VERSION_ID, APPLICANT_ID, "RUNNING", AT, null)));

        Optional<WorkflowLaunch> launch = adapter.findLatest(APPLICATION_ID, VERSION_ID);

        assertThat(launch).isPresent();
        assertThat(launch.get().instanceId()).isEqualTo(INSTANCE_ID);
        assertThat(launch.get().status()).isEqualTo("RUNNING");
    }
}
