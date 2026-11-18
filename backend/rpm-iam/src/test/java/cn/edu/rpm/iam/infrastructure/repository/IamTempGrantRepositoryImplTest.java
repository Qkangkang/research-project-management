package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamTempGrantEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamTempGrantMapper;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IamTempGrantRepositoryImpl} 纯单元测试（T-IAM-005 先行测试）。
 *
 * <p>验收锚点：插入临时授权成功；按 grantee_id + 有效期窗口查询（对应
 * idx_temp_grant_grantee_valid 三列）；到期时点查询返回空。
 * 有效期语义与 Batch-4 GrantValidityGuard 一致：valid_from &lt;= at &lt; valid_to
 * （iam_temp_grant.valid_to 非空，无"长期有效"分支）。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamTempGrantRepositoryImplTest {

    private static final String GRANTEE_ID = "0123456789abcdef0123456789abcdef";
    private static final String PROJECT_ID = "33333333333333333333333333333333";
    private static final OffsetDateTime AT =
            OffsetDateTime.parse("2026-11-17T10:00:00+08:00");

    @Mock
    private IamTempGrantMapper iamTempGrantMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<IamTempGrantEntity>> wrapperCaptor;

    private IamTempGrantRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, IamTempGrantEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new IamTempGrantRepositoryImpl(iamTempGrantMapper);
    }

    private static IamTempGrantEntity newGrant() {
        IamTempGrantEntity grant = new IamTempGrantEntity();
        grant.setGranteeId(GRANTEE_ID);
        grant.setResourceType("PROJECT");
        grant.setResourceId(PROJECT_ID);
        grant.setPermissionCode("project:view");
        grant.setReason("出差代理审批");
        grant.setValidFrom(OffsetDateTime.parse("2026-11-10T00:00:00+08:00"));
        grant.setValidTo(OffsetDateTime.parse("2026-11-20T00:00:00+08:00"));
        grant.setGrantedBy("44444444444444444444444444444444");
        grant.setStatus(IamTempGrantEntity.STATUS_ACTIVE);
        return grant;
    }

    @Test
    void insertDelegatesToMapper() {
        IamTempGrantEntity grant = newGrant();
        when(iamTempGrantMapper.insert(same(grant))).thenReturn(1);

        repository.insert(grant);

        verify(iamTempGrantMapper).insert(same(grant));
    }

    @Test
    void findValidByGranteeFiltersGranteeAndValidityWindow() {
        when(iamTempGrantMapper.selectList(ArgumentMatchers.<Wrapper<IamTempGrantEntity>>any()))
                .thenReturn(List.of(newGrant()));

        List<IamTempGrantEntity> result = repository.findValidByGrantee(GRANTEE_ID, AT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getResourceId()).isEqualTo(PROJECT_ID);
        assertThat(result.get(0).getPermissionCode()).isEqualTo("project:view");
        verify(iamTempGrantMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<IamTempGrantEntity> wrapper = wrapperCaptor.getValue();
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("grantee_id =");
        assertThat(sql).contains("status =");
        assertThat(sql).contains("valid_from <=");
        assertThat(sql).contains("valid_to >");
        assertThat(sql).doesNotContain("IS NULL");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(GRANTEE_ID, IamTempGrantEntity.STATUS_ACTIVE, AT);
    }

    @Test
    void findValidByGranteeReturnsEmptyListWhenGrantExpired() {
        when(iamTempGrantMapper.selectList(ArgumentMatchers.<Wrapper<IamTempGrantEntity>>any()))
                .thenReturn(List.of());

        OffsetDateTime afterValidTo = OffsetDateTime.parse("2026-11-21T00:00:00+08:00");
        assertThat(repository.findValidByGrantee(GRANTEE_ID, afterValidTo)).isEmpty();
    }
}
