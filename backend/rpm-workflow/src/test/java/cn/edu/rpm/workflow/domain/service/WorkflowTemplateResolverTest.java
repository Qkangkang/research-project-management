package cn.edu.rpm.workflow.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.workflow.domain.dto.TemplateRoutingContext;
import cn.edu.rpm.workflow.domain.model.WorkflowTemplateCandidate;
import cn.edu.rpm.workflow.domain.model.WorkflowTemplateVersionCandidate;
import cn.edu.rpm.workflow.domain.port.WorkflowTemplateQueryPort;
import cn.edu.rpm.workflow.infrastructure.adapter.JacksonTemplateConditionMatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link WorkflowTemplateResolver} 纯单元测试（T-WF-012 先行测试）。
 *
 * <p>TC-APP-015：{@code YEAR_PLAN} + 预算 {@code 1200000.00} + 部门 {@code D1}
 * 命中唯一模板版本；TC-APP-016：同时命中两个金额区间重叠模板返回 {@code RPM-SYS-001}。</p>
 */
class WorkflowTemplateResolverTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-17T10:00:00+08:00");

    private static final UUID DEPT_ID = UUID.fromString("0b7f5b7e-2a1c-4c2e-8d4f-9a0b1c2d3e4f");

    private FakeQueryPort queryPort;

    private WorkflowTemplateResolver resolver;

    @BeforeEach
    void setUp() {
        queryPort = new FakeQueryPort();
        resolver = new WorkflowTemplateResolver(queryPort,
                new JacksonTemplateConditionMatcher(new ObjectMapper()));
    }

    private TemplateRoutingContext context() {
        return new TemplateRoutingContext("YEAR_PLAN", DEPT_ID, new BigDecimal("1200000.00"),
                null, null, null, null);
    }

    @Test
    void tcApp015ResolvesUniqueTemplateVersion() {
        UUID matching = UUID.randomUUID();
        UUID notMatching = UUID.randomUUID();
        queryPort.add(UUID.randomUUID(), "YEAR_PLAN", matching,
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"BETWEEN\","
                        + "\"value\":[1000000,2000000]}]}");
        queryPort.add(UUID.randomUUID(), "YEAR_PLAN", notMatching,
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"GT\",\"value\":5000000}]}");

        assertThat(resolver.resolve(context(), AT)).isEqualTo(matching);
    }

    @Test
    void tcApp016OverlappingTemplatesReturnSys001() {
        queryPort.add(UUID.randomUUID(), "YEAR_PLAN", UUID.randomUUID(),
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"BETWEEN\","
                        + "\"value\":[1000000,2000000]}]}");
        queryPort.add(UUID.randomUUID(), "YEAR_PLAN", UUID.randomUUID(),
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"GTE\",\"value\":1000000}]}");

        assertThatThrownBy(() -> resolver.resolve(context(), AT))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_001));
    }

    @Test
    void noMatchingTemplateReturnsWf001() {
        queryPort.add(UUID.randomUUID(), "YEAR_PLAN", UUID.randomUUID(),
                "{\"conditions\":[{\"field\":\"budget_total\",\"operator\":\"LT\",\"value\":100}]}");

        assertThatThrownBy(() -> resolver.resolve(context(), AT))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_001));
    }

    @Test
    void templatesWithOtherProjectTypeAreSkipped() {
        queryPort.add(UUID.randomUUID(), "OTHER_TYPE", UUID.randomUUID(), "{}");

        assertThatThrownBy(() -> resolver.resolve(context(), AT))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_001));
    }

    @Test
    void templateWithoutEffectiveVersionIsSkipped() {
        UUID templateId = UUID.randomUUID();
        queryPort.templates.add(new WorkflowTemplateCandidate(templateId, "YEAR_PLAN"));

        assertThatThrownBy(() -> resolver.resolve(context(), AT))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WF_001));
    }

    @Test
    void departmentConditionUsesWhitelistedUuidComparison() {
        UUID versionId = UUID.randomUUID();
        queryPort.add(UUID.randomUUID(), "YEAR_PLAN", versionId,
                "{\"conditions\":[{\"field\":\"dept_id\",\"operator\":\"EQ\","
                        + "\"value\":\"0b7f5b7e-2a1c-4c2e-8d4f-9a0b1c2d3e4f\"}]}");

        assertThat(resolver.resolve(context(), AT)).isEqualTo(versionId);
    }

    /**
     * 内存版模板查询端口。
     */
    private static final class FakeQueryPort implements WorkflowTemplateQueryPort {

        private final List<WorkflowTemplateCandidate> templates = new ArrayList<>();

        private final Map<UUID, WorkflowTemplateVersionCandidate> versions = new HashMap<>();

        private void add(UUID templateId, String projectType, UUID versionId, String conditionExpr) {
            templates.add(new WorkflowTemplateCandidate(templateId, projectType));
            versions.put(templateId, new WorkflowTemplateVersionCandidate(versionId, conditionExpr));
        }

        @Override
        public List<WorkflowTemplateCandidate> findPublishedTemplates() {
            return List.copyOf(templates);
        }

        @Override
        public Optional<WorkflowTemplateVersionCandidate> findEffectiveVersion(String templateId,
                                                                              OffsetDateTime at) {
            return Optional.ofNullable(versions.get(UUID.fromString(templateId)));
        }

        @Override
        public Optional<WorkflowTemplateVersionCandidate> findEffectiveVersionByCode(String templateCode,
                                                                                    OffsetDateTime at) {
            return Optional.empty();
        }
    }
}
