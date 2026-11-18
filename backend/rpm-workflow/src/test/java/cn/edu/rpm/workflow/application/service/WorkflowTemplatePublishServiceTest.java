package cn.edu.rpm.workflow.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.workflow.application.dto.PublishTemplateCmd;
import cn.edu.rpm.workflow.infrastructure.adapter.JacksonTemplateConditionMatcher;
import cn.edu.rpm.workflow.infrastructure.adapter.JacksonTemplateDefinitionCodec;
import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateEntity;
import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateVersionEntity;
import cn.edu.rpm.workflow.infrastructure.repository.WfTemplateRepository;
import cn.edu.rpm.workflow.infrastructure.repository.WfTemplateVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowTemplatePublishService} 纯单元测试（T-WF-013 先行测试）。
 *
 * <p>TC-APP-017：首节点无可用处理人 → {@code RPM-WF-002}，模板保持 DRAFT（不落新版本）；
 * TC-APP-018：发布新版本后旧版本 {@code effective_to} 收口，但旧实例绑定不受影响。</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTemplatePublishServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-17T10:00:00+08:00");

    private static final String VALID_DEFINITION = """
            {"nodes":[
              {"key":"formalReview","name":"形式审查","assignee":"role:research_admin","returnTo":"start"},
              {"key":"decision","name":"决策","assignee":"role:leader","returnTo":"formalReview"}
            ]}
            """;

    @Mock
    private WfTemplateRepository templateRepository;

    @Mock
    private WfTemplateVersionRepository templateVersionRepository;

    private WorkflowTemplatePublishService service;

    private String templateId;

    private UUID templateUuid;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new WorkflowTemplatePublishService(templateRepository, templateVersionRepository,
                new JacksonTemplateDefinitionCodec(objectMapper),
                new JacksonTemplateConditionMatcher(objectMapper));
        templateUuid = UUID.randomUUID();
        templateId = UuidUtils.formatCompact(templateUuid);
    }

    private WfTemplateEntity template(String status) {
        WfTemplateEntity template = new WfTemplateEntity();
        template.setId(templateId);
        template.setCode("TPL-001");
        template.setName("面上项目审批模板");
        template.setProjectType("YEAR_PLAN");
        template.setStatus(status);
        template.setVersion(0);
        return template;
    }

    private WfTemplateVersionEntity version(int versionNo, OffsetDateTime from, OffsetDateTime to) {
        WfTemplateVersionEntity version = new WfTemplateVersionEntity();
        version.setId(UUID.randomUUID().toString().replace("-", ""));
        version.setTemplateId(templateId);
        version.setVersionNo(versionNo);
        version.setDefinition(VALID_DEFINITION);
        version.setConditionExpr("{}");
        version.setEffectiveFrom(from);
        version.setEffectiveTo(to);
        version.setStatus(WfTemplateVersionEntity.STATUS_PUBLISHED);
        version.setVersion(0);
        return version;
    }

    private PublishTemplateCmd cmd(String definition, String conditionExpr, OffsetDateTime effectiveFrom) {
        return new PublishTemplateCmd(templateUuid, definition, conditionExpr, effectiveFrom,
                UUID.randomUUID());
    }

    @Test
    void tcApp017BlocksPublishWhenFirstNodeHasNoAssignee() {
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(
                template(WfTemplateEntity.STATUS_DRAFT)));
        String definition = """
                {"nodes":[
                  {"key":"formalReview","name":"形式审查","assignee":"","returnTo":"start"},
                  {"key":"decision","name":"决策","assignee":"role:leader","returnTo":"formalReview"}
                ]}
                """;

        assertThatThrownBy(() -> service.publish(cmd(definition, "{}", NOW)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_002));

        verify(templateVersionRepository, never()).insert(any());
        verify(templateRepository, never()).update(any());
    }

    @Test
    void tcApp018PublishesNextVersionAndClosesPreviousWindow() {
        WfTemplateEntity template = template(WfTemplateEntity.STATUS_PUBLISHED);
        WfTemplateVersionEntity existing = version(1, NOW.minusDays(10), null);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findLatestByTemplateId(templateId))
                .thenReturn(Optional.of(existing));
        when(templateVersionRepository.findPublishedByTemplateId(templateId))
                .thenReturn(List.of(existing));
        when(templateVersionRepository.update(existing)).thenReturn(1);
        when(templateRepository.update(template)).thenReturn(1);
        doAnswer(invocation -> {
            WfTemplateVersionEntity inserted = invocation.getArgument(0);
            inserted.setId(UUID.randomUUID().toString().replace("-", ""));
            return null;
        }).when(templateVersionRepository).insert(any());

        UUID newVersionId = service.publish(cmd(VALID_DEFINITION, "{}", NOW));

        ArgumentCaptor<WfTemplateVersionEntity> captor =
                ArgumentCaptor.forClass(WfTemplateVersionEntity.class);
        verify(templateVersionRepository).insert(captor.capture());
        WfTemplateVersionEntity inserted = captor.getValue();
        assertThat(inserted.getVersionNo()).isEqualTo(2);
        assertThat(inserted.getStatus()).isEqualTo(WfTemplateVersionEntity.STATUS_PUBLISHED);
        assertThat(inserted.getEffectiveFrom()).isEqualTo(NOW);
        assertThat(newVersionId.toString().replace("-", "")).isEqualTo(inserted.getId());

        // 旧版本收口到新版本生效时刻；旧实例绑定 template_version_id 不受影响
        assertThat(existing.getEffectiveTo()).isEqualTo(NOW);
        assertThat(template.getCurrentVersionId()).isEqualTo(inserted.getId());
        assertThat(template.getStatus()).isEqualTo(WfTemplateEntity.STATUS_PUBLISHED);
    }

    @Test
    void overlappingBudgetConditionIsRejected() {
        WfTemplateEntity template = template(WfTemplateEntity.STATUS_PUBLISHED);
        WfTemplateVersionEntity existing = version(1, NOW.minusDays(10), null);
        existing.setConditionExpr(
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"BETWEEN\","
                        + "\"value\":[1000000,2000000]}]}");
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findLatestByTemplateId(templateId))
                .thenReturn(Optional.of(existing));
        when(templateVersionRepository.findPublishedByTemplateId(templateId))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.publish(cmd(VALID_DEFINITION,
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"BETWEEN\","
                        + "\"value\":[1500000,3000000]}]}", NOW)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_001));

        verify(templateVersionRepository, never()).insert(any());
        assertThat(existing.getEffectiveTo()).isNull();
    }

    @Test
    void closedVersionDoesNotBlockOverlappingBudgetCondition() {
        WfTemplateEntity template = template(WfTemplateEntity.STATUS_PUBLISHED);
        WfTemplateVersionEntity closed = version(1, NOW.minusDays(20), NOW.minusDays(1));
        closed.setConditionExpr(
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"BETWEEN\","
                        + "\"value\":[1000000,2000000]}]}");
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findLatestByTemplateId(templateId))
                .thenReturn(Optional.of(closed));
        when(templateVersionRepository.findPublishedByTemplateId(templateId))
                .thenReturn(List.of(closed));
        when(templateRepository.update(template)).thenReturn(1);
        doAnswer(invocation -> {
            WfTemplateVersionEntity inserted = invocation.getArgument(0);
            inserted.setId(UUID.randomUUID().toString().replace("-", ""));
            return null;
        }).when(templateVersionRepository).insert(any());

        assertThat(service.publish(cmd(VALID_DEFINITION,
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"BETWEEN\","
                        + "\"value\":[1500000,3000000]}]}", NOW))).isNotNull();
    }

    @Test
    void incompleteReturnPolicyIsRejected() {
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(
                template(WfTemplateEntity.STATUS_DRAFT)));
        String definition = """
                {"nodes":[
                  {"key":"formalReview","name":"形式审查","assignee":"role:research_admin","returnTo":"decision"}
                ]}
                """;

        assertThatThrownBy(() -> service.publish(cmd(definition, "{}", NOW)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_001));
    }

    @Test
    void effectiveFromEarlierThanLatestVersionIsRejected() {
        WfTemplateEntity template = template(WfTemplateEntity.STATUS_PUBLISHED);
        WfTemplateVersionEntity latest = version(1, NOW, null);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findLatestByTemplateId(templateId))
                .thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.publish(cmd(VALID_DEFINITION, "{}", NOW.minusDays(1))))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_001));
    }
}
