package cn.edu.rpm.execution.infrastructure.repository;

import cn.edu.rpm.execution.infrastructure.entity.DeliverableEntity;
import cn.edu.rpm.execution.infrastructure.mapper.DeliverableMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DeliverableRepositoryImpl} 纯单元测试（T-EXEC-003 先行测试）。
 */
@ExtendWith(MockitoExtension.class)
class DeliverableRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";
    private static final String MILESTONE_ID = "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm";

    @Mock
    private DeliverableMapper deliverableMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<DeliverableEntity>> wrapperCaptor;

    private DeliverableRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DeliverableEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new DeliverableRepositoryImpl(deliverableMapper);
    }

    @Test
    void insertKeepsFileHashAndUploader() {
        DeliverableEntity deliverable = new DeliverableEntity();
        deliverable.setProjectId(PROJECT_ID);
        deliverable.setMilestoneId(MILESTONE_ID);
        deliverable.setFileId("ffffffffffffffffffffffffffffffff");
        deliverable.setTitle("中期检查报告.pdf");
        deliverable.setSha256("c".repeat(64));
        deliverable.setStatus(DeliverableEntity.STATUS_ACTIVE);
        deliverable.setUploadedBy("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        repository.insert(deliverable);

        verify(deliverableMapper).insert(deliverable);
        assertThat(deliverable.getStatus()).isEqualTo(DeliverableEntity.STATUS_ACTIVE);
    }

    @Test
    void findByMilestoneIdOrdersByCreatedAtDesc() {
        when(deliverableMapper.selectList(ArgumentMatchers.<Wrapper<DeliverableEntity>>any()))
                .thenReturn(List.of());

        repository.findByMilestoneId(MILESTONE_ID);

        verify(deliverableMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("milestone_id =")
                .containsIgnoringCase("order by created_at desc");
    }

    @Test
    void existsActiveByMilestoneIdRequiresActiveStatus() {
        when(deliverableMapper.selectCount(ArgumentMatchers.<Wrapper<DeliverableEntity>>any()))
                .thenReturn(1L);

        assertThat(repository.existsActiveByMilestoneId(PROJECT_ID, MILESTONE_ID)).isTrue();

        verify(deliverableMapper).selectCount(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =", "milestone_id =", "status =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(DeliverableEntity.STATUS_ACTIVE);
    }

    @Test
    void revokeMarksDeliverableRevoked() {
        DeliverableEntity deliverable = new DeliverableEntity();
        deliverable.setStatus(DeliverableEntity.STATUS_ACTIVE);

        deliverable.revoke();

        assertThat(deliverable.getStatus()).isEqualTo(DeliverableEntity.STATUS_REVOKED);
    }
}
