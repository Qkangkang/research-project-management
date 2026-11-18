package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationMemberEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplicationMemberMapper;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApplicationMemberRepositoryImpl} 纯单元测试（T-APP-005 先行测试）。
 *
 * <p>验收锚点：成员快照批量插入成功（≤500 条单批，超出分批，RULE-DB3）；
 * 批量插入前为缺失主键的成员分配 32 位 uuid 文本（与 ASSIGN_UUID 同格式）；
 * 按 {@code application_version_id} 查询返回成员列表并按 {@code sort_order} 升序；
 * 空快照不触发任何 SQL。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApplicationMemberRepositoryImplTest {

    private static final String VERSION_ID = "aaaabbbbccccddddeeeeffff11112222";
    private static final String USER_ID = "99998888777766665555444433332222";

    @Mock
    private ApplicationMemberMapper applicationMemberMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ApplicationMemberEntity>> wrapperCaptor;

    private ApplicationMemberRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ApplicationMemberEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ApplicationMemberRepositoryImpl(applicationMemberMapper);
    }

    private static ApplicationMemberEntity member(int sortOrder) {
        ApplicationMemberEntity entity = new ApplicationMemberEntity();
        entity.setApplicationVersionId(VERSION_ID);
        entity.setUserId(USER_ID);
        entity.setMemberRole("CO_AUTHOR");
        entity.setContributionDesc("负责感知算法设计");
        entity.setSortOrder(sortOrder);
        return entity;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<ApplicationMemberEntity>> listCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    @Test
    void insertBatchAssignsIdAndDelegatesSingleChunk() {
        ApplicationMemberEntity first = member(0);
        ApplicationMemberEntity second = member(1);
        second.setUserId("88887777666655554444333322221111");
        second.setMemberRole("ADVISOR");
        ArgumentCaptor<List<ApplicationMemberEntity>> captor = listCaptor();

        repository.insertBatch(List.of(first, second));

        verify(applicationMemberMapper).insertBatch(captor.capture());
        List<ApplicationMemberEntity> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getUserId()).isEqualTo(USER_ID);
        assertThat(saved.get(0).getMemberRole()).isEqualTo("CO_AUTHOR");
        assertThat(saved.get(1).getMemberRole()).isEqualTo("ADVISOR");
        assertThat(first.getId()).matches("[0-9a-f]{32}");
        assertThat(second.getId()).matches("[0-9a-f]{32}");
    }

    @Test
    void insertBatchKeepsCallerProvidedId() {
        ApplicationMemberEntity member = member(0);
        member.setId("feedfacefeedfacefeedfacefeedface");
        ArgumentCaptor<List<ApplicationMemberEntity>> captor = listCaptor();

        repository.insertBatch(List.of(member));

        verify(applicationMemberMapper).insertBatch(captor.capture());
        assertThat(captor.getValue().get(0).getId())
                .isEqualTo("feedfacefeedfacefeedfacefeedface");
    }

    @Test
    void insertBatchSplitsIntoChunksOfAtMostFiveHundred() {
        List<ApplicationMemberEntity> members = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            ApplicationMemberEntity entity = member(i);
            entity.setUserId(String.format("%032d", i));
            entity.setMemberRole("MEMBER");
            members.add(entity);
        }
        ArgumentCaptor<List<ApplicationMemberEntity>> captor = listCaptor();

        repository.insertBatch(members);

        verify(applicationMemberMapper, times(2)).insertBatch(captor.capture());
        List<List<ApplicationMemberEntity>> chunks = captor.getAllValues();
        assertThat(chunks.get(0)).hasSize(500);
        assertThat(chunks.get(1)).hasSize(1);
        assertThat(chunks.get(1).get(0).getSortOrder()).isEqualTo(500);
        assertThat(members).allSatisfy(m -> assertThat(m.getId()).matches("[0-9a-f]{32}"));
    }

    @Test
    void insertBatchSkipsEmptySnapshot() {
        repository.insertBatch(List.of());
        repository.insertBatch(null);

        verify(applicationMemberMapper, never()).insertBatch(ArgumentMatchers.anyList());
    }

    @Test
    void findByVersionIdFiltersVersionAndOrdersBySortOrder() {
        ApplicationMemberEntity first = member(0);
        first.setId("11111111111111111111111111111111");
        ApplicationMemberEntity second = member(1);
        second.setId("22222222222222222222222222222222");
        when(applicationMemberMapper.selectList(
                ArgumentMatchers.<Wrapper<ApplicationMemberEntity>>any()))
                .thenReturn(List.of(first, second));

        List<ApplicationMemberEntity> result = repository.findByVersionId(VERSION_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSortOrder()).isZero();
        assertThat(result.get(1).getSortOrder()).isEqualTo(1);
        verify(applicationMemberMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<ApplicationMemberEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("application_version_id =");
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("order by sort_order");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(VERSION_ID);
    }

    @Test
    void findByVersionIdReturnsEmptyWhenSnapshotMissing() {
        when(applicationMemberMapper.selectList(
                ArgumentMatchers.<Wrapper<ApplicationMemberEntity>>any())).thenReturn(List.of());

        assertThat(repository.findByVersionId("00000000000000000000000000000000")).isEmpty();
    }

    @Test
    void entityMapsEveryApplicationMemberColumnIncludingV04AuditColumns() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(ApplicationMemberEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("application_member");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "application_version_id", "user_id", "member_role",
                        "contribution_desc", "sort_order",
                        "created_at", "updated_at", "created_by", "updated_by", "version");
        assertThat(tableInfo.getKeyColumn()).isEqualTo("id");
    }
}
