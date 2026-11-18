package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfTaskEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfTaskMapper;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WfTaskRepositoryImpl} 纯单元测试（T-WF-005 先行测试）。
 *
 * <p>验收锚点：插入任务成功；按 assignee_id + status 查询待办
 * （idx_wf_task_assignee_status）；携带旧 version 更新返回 0（TC-APP-024）；
 * 同一 request_id 可查回首次处理结果（TC-APP-023）。</p>
 */
@ExtendWith(MockitoExtension.class)
class WfTaskRepositoryImplTest {

    private static final String ASSIGNEE_ID = "0123456789abcdef0123456789abcdef";
    private static final String INSTANCE_ID = "11111111111111111111111111111111";
    private static final String REQUEST_ID = "66666666-6666-6666-6666-666666666666";

    @Mock
    private WfTaskMapper wfTaskMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<WfTaskEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<WfTaskEntity> entityCaptor;

    private WfTaskRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WfTaskEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new WfTaskRepositoryImpl(wfTaskMapper);
    }

    private static WfTaskEntity task(String status) {
        WfTaskEntity entity = new WfTaskEntity();
        entity.setInstanceId(INSTANCE_ID);
        entity.setNodeKey("formalReview");
        entity.setNodeName("形式审查");
        entity.setAssigneeId(ASSIGNEE_ID);
        entity.setDelegatedFrom("22222222222222222222222222222222");
        entity.setStatus(status);
        entity.setRoundNo(1);
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsNodeAndAssignee() {
        WfTaskEntity entity = task(WfTaskEntity.STATUS_PENDING);
        when(wfTaskMapper.insert(ArgumentMatchers.any(WfTaskEntity.class))).thenReturn(1);

        repository.insert(entity);

        verify(wfTaskMapper).insert(entityCaptor.capture());
        WfTaskEntity saved = entityCaptor.getValue();
        assertThat(saved.getInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(saved.getNodeKey()).isEqualTo("formalReview");
        assertThat(saved.getNodeName()).isEqualTo("形式审查");
        assertThat(saved.getAssigneeId()).isEqualTo(ASSIGNEE_ID);
        assertThat(saved.getDelegatedFrom()).isEqualTo("22222222222222222222222222222222");
        assertThat(saved.getStatus()).isEqualTo(WfTaskEntity.STATUS_PENDING);
        assertThat(saved.getRoundNo()).isEqualTo(1);
    }

    @Test
    void findPendingByAssigneeFiltersAssigneeAndPendingStatus() {
        WfTaskEntity pending = task(WfTaskEntity.STATUS_PENDING);
        when(wfTaskMapper.selectList(ArgumentMatchers.<Wrapper<WfTaskEntity>>any()))
                .thenReturn(List.of(pending));

        List<WfTaskEntity> result = repository.findPendingByAssignee(ASSIGNEE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(WfTaskEntity.STATUS_PENDING);
        verify(wfTaskMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<WfTaskEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment())
                .contains("assignee_id =", "status =", "ORDER BY due_at ASC");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(ASSIGNEE_ID, WfTaskEntity.STATUS_PENDING);
    }

    @Test
    void findPendingByAssigneeReturnsEmptyWhenNoTodo() {
        when(wfTaskMapper.selectList(ArgumentMatchers.<Wrapper<WfTaskEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findPendingByAssignee(ASSIGNEE_ID)).isEmpty();
    }

    @Test
    void findByInstanceFiltersInstanceAndOrdersByRound() {
        when(wfTaskMapper.selectList(ArgumentMatchers.<Wrapper<WfTaskEntity>>any()))
                .thenReturn(List.of(task(WfTaskEntity.STATUS_APPROVED), task(WfTaskEntity.STATUS_PENDING)));

        List<WfTaskEntity> result = repository.findByInstance(INSTANCE_ID);

        assertThat(result).hasSize(2);
        verify(wfTaskMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<WfTaskEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment())
                .contains("instance_id =", "ORDER BY round_no ASC", "created_at ASC");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(INSTANCE_ID);
    }

    @Test
    void findByActionRequestIdReturnsFirstProcessedTask() {
        WfTaskEntity processed = task(WfTaskEntity.STATUS_APPROVED);
        processed.setAction(WfTaskEntity.ACTION_APPROVED);
        processed.setActionRequestId(REQUEST_ID);
        when(wfTaskMapper.selectOne(ArgumentMatchers.<Wrapper<WfTaskEntity>>any()))
                .thenReturn(processed);

        Optional<WfTaskEntity> result = repository.findByActionRequestId(REQUEST_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getAction()).isEqualTo(WfTaskEntity.ACTION_APPROVED);
        verify(wfTaskMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("action_request_id =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains(REQUEST_ID);
    }

    @Test
    void findByActionRequestIdReturnsEmptyForFirstSubmission() {
        when(wfTaskMapper.selectOne(ArgumentMatchers.<Wrapper<WfTaskEntity>>any()))
                .thenReturn(null);

        assertThat(repository.findByActionRequestId(REQUEST_ID)).isEmpty();
    }

    @Test
    void updateReturnsZeroWhenTaskAlreadyProcessedWithStaleVersion() {
        WfTaskEntity stale = task(WfTaskEntity.STATUS_PENDING);
        stale.setId("33333333333333333333333333333333");
        stale.setVersion(3);
        when(wfTaskMapper.updateById(stale)).thenReturn(0);

        assertThat(repository.update(stale)).isZero();
    }

    @Test
    void findTaskPageDelegatesToJoinQueryWithOffset() {
        WorkflowTaskRow row = new WorkflowTaskRow();
        row.setTaskId("44444444444444444444444444444444");
        row.setInstanceId(INSTANCE_ID);
        when(wfTaskMapper.selectTaskPage(ASSIGNEE_ID, WfTaskEntity.STATUS_PENDING,
                "APPLICATION", 20L, 20)).thenReturn(List.of(row));

        List<WorkflowTaskRow> result = repository.findTaskPage(ASSIGNEE_ID,
                WfTaskEntity.STATUS_PENDING, "APPLICATION", 20L, 20);

        assertThat(result).containsExactly(row);
        verify(wfTaskMapper).selectTaskPage(ASSIGNEE_ID, WfTaskEntity.STATUS_PENDING,
                "APPLICATION", 20L, 20);
    }

    @Test
    void countTasksDelegatesToJoinCount() {
        when(wfTaskMapper.countTaskPage(ASSIGNEE_ID, WfTaskEntity.STATUS_PENDING, null))
                .thenReturn(3L);

        assertThat(repository.countTasks(ASSIGNEE_ID, WfTaskEntity.STATUS_PENDING, null))
                .isEqualTo(3L);
    }

    @Test
    void cancelPendingTasksCancelsBothPendingAndProcessing() {
        OffsetDateTime at = OffsetDateTime.parse("2026-11-17T10:00:00+08:00");
        WfTaskEntity pending = task(WfTaskEntity.STATUS_PENDING);
        pending.setId("55555555555555555555555555555555");
        WfTaskEntity processing = task(WfTaskEntity.STATUS_PROCESSING);
        processing.setId("66666666666666666666666666666666");
        when(wfTaskMapper.selectList(ArgumentMatchers.<Wrapper<WfTaskEntity>>any()))
                .thenReturn(List.of(pending, processing));
        when(wfTaskMapper.updateById(any(WfTaskEntity.class))).thenReturn(1);

        int cancelled = repository.cancelPendingTasks(INSTANCE_ID, at);

        assertThat(cancelled).isEqualTo(2);
        assertThat(pending.getStatus()).isEqualTo(WfTaskEntity.STATUS_CANCELLED);
        assertThat(pending.getAction()).isEqualTo(WfTaskEntity.ACTION_CANCELLED);
        assertThat(pending.getCompletedAt()).isEqualTo(at);
        assertThat(processing.getStatus()).isEqualTo(WfTaskEntity.STATUS_CANCELLED);
        verify(wfTaskMapper, org.mockito.Mockito.times(2)).updateById(any(WfTaskEntity.class));
        verify(wfTaskMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("instance_id =", "status IN");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(INSTANCE_ID, WfTaskEntity.STATUS_PENDING, WfTaskEntity.STATUS_PROCESSING);
    }

    @Test
    void entityMapsEveryWfTaskColumnIncludingV04ExceptionColumns() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(WfTaskEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("wf_task");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "instance_id", "node_key", "node_name", "assignee_id",
                        "delegated_from", "status", "due_at", "claimed_at",
                        "completed_at", "action", "comment", "action_request_id",
                        "round_no", "exception_reason", "exception_at",
                        "created_at", "updated_at", "created_by", "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
