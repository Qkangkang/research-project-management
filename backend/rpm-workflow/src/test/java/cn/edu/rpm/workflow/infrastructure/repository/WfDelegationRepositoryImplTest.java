package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfDelegationEntity;
import cn.edu.rpm.workflow.infrastructure.mapper.WfDelegationMapper;
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
 * {@link WfDelegationRepositoryImpl} 纯单元测试（T-WF-006 先行测试）。
 *
 * <p>验收锚点：插入委托成功；按 delegate_id + 有效期窗口查询有效委托
 * （idx_wf_delegation_delegate_valid）；过期/撤销委托不返回
 * （TC-IAM-009 有效委托、TC-IAM-010 无委托）。</p>
 */
@ExtendWith(MockitoExtension.class)
class WfDelegationRepositoryImplTest {

    private static final String DELEGATOR_ID = "0123456789abcdef0123456789abcdef";
    private static final String DELEGATE_ID = "11111111111111111111111111111111";
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-17T10:00:00+08:00");

    @Mock
    private WfDelegationMapper wfDelegationMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<WfDelegationEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<WfDelegationEntity> entityCaptor;

    private WfDelegationRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WfDelegationEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new WfDelegationRepositoryImpl(wfDelegationMapper);
    }

    private static WfDelegationEntity delegation(String status,
                                                 OffsetDateTime from, OffsetDateTime to) {
        WfDelegationEntity entity = new WfDelegationEntity();
        entity.setDelegatorId(DELEGATOR_ID);
        entity.setDelegateId(DELEGATE_ID);
        entity.setScope("{\"biz_types\":[\"APPLICATION\"]}");
        entity.setValidFrom(from);
        entity.setValidTo(to);
        entity.setStatus(status);
        return entity;
    }

    @Test
    void insertDelegatesToMapperAndKeepsDelegationWindow() {
        WfDelegationEntity entity = delegation(WfDelegationEntity.STATUS_ACTIVE,
                AT.minusDays(1), AT.plusDays(1));
        when(wfDelegationMapper.insert(ArgumentMatchers.any(WfDelegationEntity.class)))
                .thenReturn(1);

        repository.insert(entity);

        verify(wfDelegationMapper).insert(entityCaptor.capture());
        WfDelegationEntity saved = entityCaptor.getValue();
        assertThat(saved.getDelegatorId()).isEqualTo(DELEGATOR_ID);
        assertThat(saved.getDelegateId()).isEqualTo(DELEGATE_ID);
        assertThat(saved.getScope()).contains("APPLICATION");
        assertThat(saved.getValidFrom()).isEqualTo(AT.minusDays(1));
        assertThat(saved.getValidTo()).isEqualTo(AT.plusDays(1));
        assertThat(saved.getStatus()).isEqualTo(WfDelegationEntity.STATUS_ACTIVE);
    }

    @Test
    void findValidByDelegateFiltersDelegateActiveStatusAndHalfOpenWindow() {
        WfDelegationEntity active = delegation(WfDelegationEntity.STATUS_ACTIVE,
                AT.minusDays(1), AT.plusDays(1));
        when(wfDelegationMapper.selectList(ArgumentMatchers.<Wrapper<WfDelegationEntity>>any()))
                .thenReturn(List.of(active));

        List<WfDelegationEntity> result = repository.findValidByDelegate(DELEGATE_ID, AT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(WfDelegationEntity.STATUS_ACTIVE);
        verify(wfDelegationMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<WfDelegationEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment())
                .contains("delegate_id =", "status =", "valid_from <=", "valid_to >");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(DELEGATE_ID, WfDelegationEntity.STATUS_ACTIVE, AT);
    }

    @Test
    void findValidByDelegateReturnsEmptyForExpiredOrRevokedDelegation() {
        when(wfDelegationMapper.selectList(ArgumentMatchers.<Wrapper<WfDelegationEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findValidByDelegate(DELEGATE_ID, AT)).isEmpty();
    }

    @Test
    void entityMapsEveryWfDelegationColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(WfDelegationEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("wf_delegation");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "delegator_id", "delegate_id", "scope", "valid_from", "valid_to",
                        "status", "created_at", "updated_at", "created_by",
                        "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
