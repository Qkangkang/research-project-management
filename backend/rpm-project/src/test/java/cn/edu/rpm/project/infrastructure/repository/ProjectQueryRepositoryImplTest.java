package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.project.application.dto.ProjectDetailSummary;
import cn.edu.rpm.project.application.dto.ProjectListItem;
import cn.edu.rpm.project.application.dto.ProjectListQuery;
import cn.edu.rpm.project.infrastructure.mapper.ProjectQueryMapper;
import cn.edu.rpm.project.infrastructure.mapper.dto.ProjectDetailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectQueryRepositoryImpl} 纯单元测试（T-PRJ-011）。
 *
 * <p>验收锚点：列表分页 {@code total} 与授权计数一致；空结果返回空列表；
 * 详情摘要把扁平行组装为项目头 + 生效基线 + 页签摘要；SQL 全部参数化且
 * 数据范围使用 {@code EXISTS project_member}（TC-PRJ-001～004 支撑）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectQueryRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String VIEWER_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private ProjectQueryMapper projectQueryMapper;

    private ProjectQueryRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new ProjectQueryRepositoryImpl(projectQueryMapper);
    }

    @Test
    void searchReturnsEmptyPageWithoutQueryingRowsWhenCountIsZero() {
        when(projectQueryMapper.count(any(), anyBoolean(), any(), anyString(),
                anyCollection(), anyCollection())).thenReturn(0L);

        PageVO<ProjectListItem> page = repository.search(new ProjectListQuery(),
                ProjectScope.allScope(), VIEWER_ID);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotal()).isZero();
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
        verify(projectQueryMapper, never()).search(any(), anyBoolean(), any(), anyString(),
                anyCollection(), anyCollection(), anyLong(), anyInt());
    }

    @Test
    void searchReturnsItemsAndTotalFromSameScope() {
        ProjectListItem item = new ProjectListItem();
        item.setProjectId(PROJECT_ID);
        item.setProjectNo("KY-2026-000456");
        when(projectQueryMapper.count(any(), anyBoolean(), any(), anyString(),
                anyCollection(), anyCollection())).thenReturn(1L);
        when(projectQueryMapper.search(any(), anyBoolean(), any(), anyString(),
                anyCollection(), anyCollection(), anyLong(), anyInt())).thenReturn(List.of(item));

        ProjectListQuery query = new ProjectListQuery();
        query.setPage(2);
        query.setSize(10);
        PageVO<ProjectListItem> page = repository.search(query, ProjectScope.allScope(), VIEWER_ID);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems()).containsExactly(item);
        verify(projectQueryMapper).search(any(), eq(true), any(), eq(VIEWER_ID),
                anyCollection(), anyCollection(), eq(10L), eq(10));
    }

    @Test
    void detailConvertsRowAndFillsZeroDefaults() {
        ProjectDetailRow row = new ProjectDetailRow();
        row.setProjectId(PROJECT_ID);
        row.setProjectNo("KY-2026-000456");
        row.setBaselineId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        row.setBaselineVersion(1);
        row.setStatus("EXECUTING");
        row.setMilestoneTotal(6L);
        row.setMilestoneCompleted(null);
        row.setReportDue(null);
        row.setBudgetTotal(new BigDecimal("1200000.00"));
        row.setAvailableAmount(null);
        row.setOutputCount(3L);
        row.setOpenRiskCount(null);
        when(projectQueryMapper.findDetail(eq(PROJECT_ID), anyBoolean(), any(), eq(VIEWER_ID),
                anyCollection(), anyCollection())).thenReturn(row);

        Optional<ProjectDetailSummary> summary = repository.findDetailSummary(PROJECT_ID,
                ProjectScope.allScope(), VIEWER_ID);

        assertThat(summary).isPresent();
        assertThat(summary.get().baselineVersion()).isEqualTo(1);
        assertThat(summary.get().summary().milestoneTotal()).isEqualTo(6);
        assertThat(summary.get().summary().milestoneCompleted()).isZero();
        assertThat(summary.get().summary().availableAmount()).isEqualByComparingTo("0");
        assertThat(summary.get().summary().openRiskCount()).isZero();
    }

    @Test
    void detailReturnsEmptyWhenProjectNotVisible() {
        when(projectQueryMapper.findDetail(any(), anyBoolean(), any(), anyString(),
                anyCollection(), anyCollection())).thenReturn(null);

        assertThat(repository.findDetailSummary(PROJECT_ID, ProjectScope.empty(), VIEWER_ID))
                .isEmpty();
    }

    @Test
    void scopeIdsArePassedAsCanonicalUuidText() {
        UUID deptId = UUID.randomUUID();
        UUID grantedId = UUID.randomUUID();
        ProjectScope scope = ProjectScope.of(false, UUID.randomUUID(), Set.of(deptId), Set.of(grantedId));
        when(projectQueryMapper.findDetail(any(), anyBoolean(), any(), anyString(),
                anyCollection(), anyCollection())).thenReturn(null);

        repository.findDetailSummary(PROJECT_ID, scope, VIEWER_ID);

        verify(projectQueryMapper).findDetail(eq(PROJECT_ID), eq(false),
                eq(scope.piId().toString()), eq(VIEWER_ID),
                eq(List.of(deptId.toString())), eq(List.of(grantedId.toString())));
    }

    @Test
    void mapperXmlIsParameterizedAndUsesMemberExistsScope() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("mapper/ProjectQueryMapper.xml")) {
            assertThat(in).as("ProjectQueryMapper.xml 必须随模块打包").isNotNull();
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).contains("EXISTS", "project_member", "scopeAll", "ILIKE");
            assertThat(xml).contains("LIMIT #{limit} OFFSET #{offset}");
            assertThat(xml).doesNotContain("${");
        }
    }

    @Test
    void listQueryNormalizesPagingAndEscapesWildcards() {
        ProjectListQuery query = new ProjectListQuery();
        query.setPage(0);
        query.setSize(500);
        query.setKeyword(" 50%_off ");

        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(ProjectListQuery.MAX_SIZE);
        assertThat(query.getKeyword()).isEqualTo("50\\%\\_off");
        assertThat(query.getOffset()).isZero();
    }
}
