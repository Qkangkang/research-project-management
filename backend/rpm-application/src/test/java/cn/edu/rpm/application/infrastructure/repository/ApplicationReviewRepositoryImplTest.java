package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationReviewEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplicationReviewMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationReviewRepositoryImpl} 纯单元测试（T-APP-006 先行测试）。
 *
 * <p>验收锚点：插入审查意见成功（阶段、结论、问题项 jsonb、评语原样落库）；
 * 按 {@code application_version_id + review_stage} 查询审查意见
 * （与 idx_application_review_version_stage 对齐）；同阶段多位评审人返回多条，
 * 按 reviewed_at 升序形成审查轨迹；无记录返回空列表。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationReviewRepositoryImplTest {

    private static final String VERSION_ID = "aaaabbbbccccddddeeeeffff11112222";
    private static final String REVIEWER_ID = "99998888777766665555444433332222";

    @Mock
    private ApplicationReviewMapper applicationReviewMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ApplicationReviewEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<ApplicationReviewEntity> entityCaptor;

    private ApplicationReviewRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ApplicationReviewEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ApplicationReviewRepositoryImpl(applicationReviewMapper);
    }

    private static ApplicationReviewEntity review(String stage, String decision) {
        ApplicationReviewEntity entity = new ApplicationReviewEntity();
        entity.setApplicationVersionId(VERSION_ID);
        entity.setReviewStage(stage);
        entity.setReviewerId(REVIEWER_ID);
        entity.setDecision(decision);
        entity.setIssues("[{\"field\":\"budget\",\"code\":\"BUDGET_EXCEED\"}]");
        entity.setComment("预算超出指南上限，需调整");
        entity.setReviewedAt(OffsetDateTime.parse("2026-02-03T14:30:00+08:00"));
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsReviewRecord() {
        ApplicationReviewEntity formal =
                review(ApplicationReviewEntity.STAGE_FORMAL, ApplicationReviewEntity.DECISION_RETURN);
        when(applicationReviewMapper.insert(ArgumentMatchers.any(ApplicationReviewEntity.class)))
                .thenReturn(1);

        repository.insert(formal);

        verify(applicationReviewMapper).insert(entityCaptor.capture());
        ApplicationReviewEntity saved = entityCaptor.getValue();
        assertThat(saved.getApplicationVersionId()).isEqualTo(VERSION_ID);
        assertThat(saved.getReviewStage()).isEqualTo(ApplicationReviewEntity.STAGE_FORMAL);
        assertThat(saved.getReviewerId()).isEqualTo(REVIEWER_ID);
        assertThat(saved.getDecision()).isEqualTo(ApplicationReviewEntity.DECISION_RETURN);
        assertThat(saved.getIssues()).contains("BUDGET_EXCEED");
        assertThat(saved.getComment()).isEqualTo("预算超出指南上限，需调整");
    }

    @Test
    void findByVersionIdAndStageFiltersVersionAndStage() {
        ApplicationReviewEntity formal =
                review(ApplicationReviewEntity.STAGE_FORMAL, ApplicationReviewEntity.DECISION_PASS);
        formal.setId("11111111111111111111111111111111");
        when(applicationReviewMapper.selectList(
                ArgumentMatchers.<Wrapper<ApplicationReviewEntity>>any()))
                .thenReturn(List.of(formal));

        List<ApplicationReviewEntity> result =
                repository.findByVersionIdAndStage(VERSION_ID, ApplicationReviewEntity.STAGE_FORMAL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDecision()).isEqualTo(ApplicationReviewEntity.DECISION_PASS);
        verify(applicationReviewMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplicationReviewEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("application_version_id =", "review_stage =");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("order by reviewed_at");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(VERSION_ID, ApplicationReviewEntity.STAGE_FORMAL);
    }

    @Test
    void findByVersionIdAndStageReturnsEmptyWhenNoReviewYet() {
        when(applicationReviewMapper.selectList(
                ArgumentMatchers.<Wrapper<ApplicationReviewEntity>>any())).thenReturn(List.of());

        assertThat(repository.findByVersionIdAndStage(VERSION_ID,
                ApplicationReviewEntity.STAGE_EXPERT)).isEmpty();
    }

    @Test
    void entityMapsEveryApplicationReviewColumnIncludingV04AuditColumns() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ApplicationReviewEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("application_review");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "application_version_id", "review_stage", "reviewer_id", "decision",
                        "issues", "comment", "reviewed_at",
                        "created_at", "updated_at", "created_by", "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
