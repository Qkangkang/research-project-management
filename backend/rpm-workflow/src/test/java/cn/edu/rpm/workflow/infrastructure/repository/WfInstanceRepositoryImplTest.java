package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfInstanceEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfInstanceMapper;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WfInstanceRepositoryImpl} 纯单元测试（T-WF-004 先行测试）。
 *
 * <p>验收锚点：插入实例成功；运行中实例查询与部分唯一索引
 * {@code uk_wf_instance_running (biz_type, biz_id, biz_version_id) WHERE status='RUNNING'}
 * 使用同一谓词（TC-APP-009）；终态实例不参与运行中查询，故同一业务版本可再次启动；
 * 更新携带旧版本返回 0。</p>
 *
 * <p>数据库唯一约束本身依赖真实 PostgreSQL，归下游业务测试阶段验证。</p>
 */
@ExtendWith(MockitoExtension.class)
class WfInstanceRepositoryImplTest {

    private static final String INSTANCE_ID = "0123456789abcdef0123456789abcdef";
    private static final String BIZ_ID = "11111111111111111111111111111111";
    private static final String VERSION_ID = "22222222222222222222222222222222";

    @Mock
    private WfInstanceMapper wfInstanceMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<WfInstanceEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<WfInstanceEntity> entityCaptor;

    private WfInstanceRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WfInstanceEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new WfInstanceRepositoryImpl(wfInstanceMapper);
    }

    private static WfInstanceEntity instance(String status) {
        WfInstanceEntity entity = new WfInstanceEntity();
        entity.setBizType("APPLICATION");
        entity.setBizId(BIZ_ID);
        entity.setBizVersionId(VERSION_ID);
        entity.setTemplateVersionId("33333333333333333333333333333333");
        entity.setFlowableInstanceId("flowable-inst-1");
        entity.setStatus(status);
        entity.setInitiatorId("44444444444444444444444444444444");
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsBusinessIdentifiers() {
        WfInstanceEntity entity = instance(WfInstanceEntity.STATUS_RUNNING);
        when(wfInstanceMapper.insert(ArgumentMatchers.any(WfInstanceEntity.class))).thenReturn(1);

        repository.insert(entity);

        verify(wfInstanceMapper).insert(entityCaptor.capture());
        WfInstanceEntity saved = entityCaptor.getValue();
        assertThat(saved.getBizType()).isEqualTo("APPLICATION");
        assertThat(saved.getBizId()).isEqualTo(BIZ_ID);
        assertThat(saved.getBizVersionId()).isEqualTo(VERSION_ID);
        assertThat(saved.getStatus()).isEqualTo(WfInstanceEntity.STATUS_RUNNING);
        assertThat(saved.getFlowableInstanceId()).isEqualTo("flowable-inst-1");
    }

    @Test
    void findRunningByBizFiltersBizTripleAndRunningStatus() {
        WfInstanceEntity running = instance(WfInstanceEntity.STATUS_RUNNING);
        running.setId(INSTANCE_ID);
        when(wfInstanceMapper.selectList(ArgumentMatchers.<Wrapper<WfInstanceEntity>>any()))
                .thenReturn(List.of(running));

        Optional<WfInstanceEntity> result =
                repository.findRunningByBiz("APPLICATION", BIZ_ID, VERSION_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(INSTANCE_ID);
        assertThat(result.get().getStatus()).isEqualTo(WfInstanceEntity.STATUS_RUNNING);
        verify(wfInstanceMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<WfInstanceEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment())
                .contains("biz_type =", "biz_id =", "biz_version_id =", "status =");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("APPLICATION", BIZ_ID, VERSION_ID, WfInstanceEntity.STATUS_RUNNING);
    }

    @Test
    void findRunningByBizReturnsEmptyWhenOnlyTerminalInstancesExist() {
        when(wfInstanceMapper.selectList(ArgumentMatchers.<Wrapper<WfInstanceEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findRunningByBiz("APPLICATION", BIZ_ID, VERSION_ID)).isEmpty();
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        when(wfInstanceMapper.selectById("ffffffffffffffffffffffffffffffff")).thenReturn(null);

        assertThat(repository.findById("ffffffffffffffffffffffffffffffff")).isEmpty();
    }

    @Test
    void updateReturnsZeroWhenOptimisticLockVersionIsStale() {
        WfInstanceEntity stale = instance(WfInstanceEntity.STATUS_RUNNING);
        stale.setId(INSTANCE_ID);
        stale.setVersion(1);
        when(wfInstanceMapper.updateById(stale)).thenReturn(0);

        assertThat(repository.update(stale)).isZero();
    }

    @Test
    void entityMapsEveryWfInstanceColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(WfInstanceEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("wf_instance");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "biz_type", "biz_id", "biz_version_id", "template_version_id",
                        "flowable_instance_id", "status", "initiator_id", "started_at",
                        "ended_at", "created_at", "updated_at", "created_by",
                        "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
