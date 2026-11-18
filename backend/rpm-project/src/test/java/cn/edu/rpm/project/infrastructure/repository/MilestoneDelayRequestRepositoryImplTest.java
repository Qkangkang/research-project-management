package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.MilestoneDelayRequestEntity;
import cn.edu.rpm.project.infrastructure.mapper.MilestoneDelayRequestMapper;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MilestoneDelayRequestRepositoryImpl} 纯单元测试（T-PRJ-005 先行测试）。
 */
@ExtendWith(MockitoExtension.class)
class MilestoneDelayRequestRepositoryImplTest {

    private static final String MILESTONE_ID = "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm";

    @Mock
    private MilestoneDelayRequestMapper delayRequestMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<MilestoneDelayRequestEntity>> wrapperCaptor;

    private MilestoneDelayRequestRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MilestoneDelayRequestEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new MilestoneDelayRequestRepositoryImpl(delayRequestMapper);
    }

    @Test
    void insertKeepsPendingStatusAndOriginalPlanSnapshot() {
        MilestoneDelayRequestEntity request = new MilestoneDelayRequestEntity();
        request.setMilestoneId(MILESTONE_ID);
        request.setOriginalPlanDate(LocalDate.of(2027, 3, 31));
        request.setRequestedPlanDate(LocalDate.of(2027, 4, 30));
        request.setDelayDays(30);
        request.setReason("关键设备采购延迟导致联调顺延");
        request.setStatus(MilestoneDelayRequestEntity.STATUS_PENDING);

        repository.insert(request);

        verify(delayRequestMapper).insert(request);
        assertThat(request.getOriginalPlanDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(request.getStatus()).isEqualTo(MilestoneDelayRequestEntity.STATUS_PENDING);
    }

    @Test
    void findPendingByMilestoneIdFiltersPendingStatus() {
        when(delayRequestMapper.selectList(
                ArgumentMatchers.<Wrapper<MilestoneDelayRequestEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findPendingByMilestoneId(MILESTONE_ID)).isEmpty();

        verify(delayRequestMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("milestone_id =", "status =")
                .containsIgnoringCase("order by created_at desc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(MilestoneDelayRequestEntity.STATUS_PENDING);
    }

    @Test
    void approveTransitionsPendingToApproved() {
        MilestoneDelayRequestEntity request = new MilestoneDelayRequestEntity();
        request.setStatus(MilestoneDelayRequestEntity.STATUS_PENDING);

        request.approve("approver", "同意", OffsetDateTime.parse("2027-03-01T10:00:00+08:00"));

        assertThat(request.getStatus()).isEqualTo(MilestoneDelayRequestEntity.STATUS_APPROVED);
        assertThat(request.getDecision()).isEqualTo(MilestoneDelayRequestEntity.DECISION_APPROVE);
    }

    @Test
    void updateDelegatesOptimisticLocking() {
        MilestoneDelayRequestEntity request = new MilestoneDelayRequestEntity();
        request.setVersion(3);
        when(delayRequestMapper.updateById(request)).thenReturn(1);

        assertThat(repository.update(request)).isEqualTo(1);
    }
}
