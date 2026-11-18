package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfCallbackLogEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfCallbackLogMapper;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WfCallbackLogRepositoryImpl} 纯单元测试（T-WF-007 先行测试）。
 *
 * <p>验收锚点：插入回调日志成功；按唯一 request_id 查回已处理记录
 * （TC-APP-023 重复请求返回首次结果）；未处理请求返回空。
 * 唯一约束本身依赖真实 PostgreSQL，归下游业务测试阶段验证。</p>
 */
@ExtendWith(MockitoExtension.class)
class WfCallbackLogRepositoryImplTest {

    private static final String REQUEST_ID = "77777777-7777-7777-7777-777777777777";
    private static final String INSTANCE_ID = "0123456789abcdef0123456789abcdef";

    @Mock
    private WfCallbackLogMapper wfCallbackLogMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<WfCallbackLogEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<WfCallbackLogEntity> entityCaptor;

    private WfCallbackLogRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WfCallbackLogEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new WfCallbackLogRepositoryImpl(wfCallbackLogMapper);
    }

    private static WfCallbackLogEntity callbackLog() {
        WfCallbackLogEntity entity = new WfCallbackLogEntity();
        entity.setInstanceId(INSTANCE_ID);
        entity.setTaskId("11111111111111111111111111111111");
        entity.setRequestId(REQUEST_ID);
        entity.setAction("APPROVED");
        entity.setPayload("{\"action\":\"APPROVED\",\"request_id\":\"" + REQUEST_ID + "\"}");
        entity.setStatus(WfCallbackLogEntity.STATUS_PROCESSED);
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsIdempotencyKey() {
        WfCallbackLogEntity entity = callbackLog();
        when(wfCallbackLogMapper.insert(ArgumentMatchers.any(WfCallbackLogEntity.class)))
                .thenReturn(1);

        repository.insert(entity);

        verify(wfCallbackLogMapper).insert(entityCaptor.capture());
        WfCallbackLogEntity saved = entityCaptor.getValue();
        assertThat(saved.getInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(saved.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(saved.getAction()).isEqualTo("APPROVED");
        assertThat(saved.getPayload()).contains("\"action\":\"APPROVED\"");
        assertThat(saved.getStatus()).isEqualTo(WfCallbackLogEntity.STATUS_PROCESSED);
    }

    @Test
    void findByRequestIdFiltersUniqueRequestKey() {
        when(wfCallbackLogMapper.selectOne(ArgumentMatchers.<Wrapper<WfCallbackLogEntity>>any()))
                .thenReturn(callbackLog());

        Optional<WfCallbackLogEntity> result = repository.findByRequestId(REQUEST_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getRequestId()).isEqualTo(REQUEST_ID);
        verify(wfCallbackLogMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("request_id =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains(REQUEST_ID);
    }

    @Test
    void findByRequestIdReturnsEmptyForFirstRequest() {
        when(wfCallbackLogMapper.selectOne(ArgumentMatchers.<Wrapper<WfCallbackLogEntity>>any()))
                .thenReturn(null);

        assertThat(repository.findByRequestId(REQUEST_ID)).isEmpty();
    }

    @Test
    void entityMapsEveryWfCallbackLogColumnWithoutAuditBaseColumns() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(WfCallbackLogEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("wf_callback_log");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "instance_id", "task_id", "request_id", "action", "payload",
                        "status", "processed_at", "created_at");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
