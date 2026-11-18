package cn.edu.rpm.organization.infrastructure.repository;

import cn.edu.rpm.organization.infrastructure.entity.OrgMembershipEntity;
import cn.edu.rpm.organization.infrastructure.mapper.OrgMembershipMapper;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrgMembershipRepositoryImpl} 纯单元测试（T-ORG-002 先行测试）。
 *
 * <p>验收锚点：插入人员归属成功；按 {@code user_id} 查询归属部门列表（idx_org_membership_user）；
 * 按 {@code is_primary = true} 查询主要部门。口径说明：拆解验收所述"is_primary 唯一"
 * 在 V01～V07 无对应唯一约束，主部门唯一性由查询语义（取单条）与数据治理保证。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrgMembershipRepositoryImplTest {

    private static final String USER_ID = "0123456789abcdef0123456789abcdef";
    private static final String PRIMARY_ORG_ID = "d1111111111111111111111111111111";
    private static final String SECONDARY_ORG_ID = "d2222222222222222222222222222222";

    @Mock
    private OrgMembershipMapper orgMembershipMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<OrgMembershipEntity>> wrapperCaptor;

    private OrgMembershipRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OrgMembershipEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new OrgMembershipRepositoryImpl(orgMembershipMapper);
    }

    private static OrgMembershipEntity newMembership(String orgId, boolean primary) {
        OrgMembershipEntity entity = new OrgMembershipEntity();
        entity.setUserId(USER_ID);
        entity.setOrgId(orgId);
        entity.setPositionName(primary ? "系主任" : "客座教授");
        entity.setIsPrimary(primary);
        entity.setStatus("ACTIVE");
        return entity;
    }

    @Test
    void insertDelegatesToMapper() {
        OrgMembershipEntity entity = newMembership(PRIMARY_ORG_ID, true);
        when(orgMembershipMapper.insert(same(entity))).thenReturn(1);

        repository.insert(entity);

        verify(orgMembershipMapper).insert(same(entity));
    }

    @Test
    void findByUserIdReturnsMembershipList() {
        when(orgMembershipMapper.selectList(ArgumentMatchers.<Wrapper<OrgMembershipEntity>>any()))
                .thenReturn(List.of(newMembership(PRIMARY_ORG_ID, true),
                        newMembership(SECONDARY_ORG_ID, false)));

        List<OrgMembershipEntity> result = repository.findByUserId(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrgId()).isEqualTo(PRIMARY_ORG_ID);
        assertThat(result.get(0).getPositionName()).isEqualTo("系主任");
        assertThat(result.get(1).getOrgId()).isEqualTo(SECONDARY_ORG_ID);
        verify(orgMembershipMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<OrgMembershipEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("user_id =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(USER_ID);
    }

    @Test
    void findByUserIdReturnsEmptyListWhenNoMembership() {
        when(orgMembershipMapper.selectList(ArgumentMatchers.<Wrapper<OrgMembershipEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findByUserId("ghost-user")).isEmpty();
    }

    @Test
    void findPrimaryByUserIdFiltersUserIdAndPrimaryFlag() {
        when(orgMembershipMapper.selectOne(ArgumentMatchers.<Wrapper<OrgMembershipEntity>>any()))
                .thenReturn(newMembership(PRIMARY_ORG_ID, true));

        Optional<OrgMembershipEntity> result = repository.findPrimaryByUserId(USER_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getOrgId()).isEqualTo(PRIMARY_ORG_ID);
        assertThat(result.get().getIsPrimary()).isTrue();
        verify(orgMembershipMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<OrgMembershipEntity> wrapper = wrapperCaptor.getValue();
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("user_id =");
        assertThat(sql).contains("is_primary =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(USER_ID, Boolean.TRUE);
    }

    @Test
    void findPrimaryByUserIdReturnsEmptyWhenNoPrimaryMembership() {
        when(orgMembershipMapper.selectOne(ArgumentMatchers.<Wrapper<OrgMembershipEntity>>any()))
                .thenReturn(null);

        assertThat(repository.findPrimaryByUserId("ghost-user")).isEmpty();
    }
}
