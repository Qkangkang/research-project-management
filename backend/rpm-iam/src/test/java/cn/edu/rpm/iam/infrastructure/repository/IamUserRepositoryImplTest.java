package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamUserMapper;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IamUserRepositoryImpl} 纯单元测试（T-IAM-001 先行测试）。
 *
 * <p>不依赖 Spring / 数据库：用 MyBatis-Plus {@link TableInfoHelper} 完成与真实启动
 * 一致的注解解析，使 Lambda Wrapper 能解析出真实列名；Mapper 以 Mockito 模拟，
 * 断言仓储委托行为与查询条件确实落在 DDL 约束列上
 * （uk_iam_user_employee_no / uk_iam_user_sso_subject）。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamUserRepositoryImplTest {

    private static final String USER_ID = "0123456789abcdef0123456789abcdef";
    private static final String EMP_NO = "EMP-2026-0001";
    private static final String DEPT_ID = "d1111111111111111111111111111111";
    private static final String SSO_SUBJECT = "oidc-sub-0001";
    private static final String DISPLAY_NAME = "张三";

    @Mock
    private IamUserMapper iamUserMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<IamUserEntity>> wrapperCaptor;

    private IamUserRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, IamUserEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new IamUserRepositoryImpl(iamUserMapper);
    }

    private static IamUserEntity newUser() {
        IamUserEntity user = new IamUserEntity();
        user.setEmployeeNo(EMP_NO);
        user.setDisplayName(DISPLAY_NAME);
        user.setDeptId(DEPT_ID);
        user.setStatus("ACTIVE");
        return user;
    }

    @Test
    void insertDelegatesToMapper() {
        IamUserEntity user = newUser();
        when(iamUserMapper.insert(same(user))).thenReturn(1);

        repository.insert(user);

        verify(iamUserMapper).insert(same(user));
    }

    @Test
    void findByIdReturnsUser() {
        IamUserEntity user = newUser();
        user.setId(USER_ID);
        when(iamUserMapper.selectById(USER_ID)).thenReturn(user);

        Optional<IamUserEntity> result = repository.findById(USER_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getEmployeeNo()).isEqualTo(EMP_NO);
        assertThat(result.get().getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(result.get().getDeptId()).isEqualTo(DEPT_ID);
    }

    @Test
    void findByIdReturnsEmptyWhenUserMissing() {
        when(iamUserMapper.selectById("no-such-id")).thenReturn(null);

        assertThat(repository.findById("no-such-id")).isEmpty();
    }

    @Test
    void findByEmployeeNoFiltersEmployeeNoColumn() {
        when(iamUserMapper.selectOne(ArgumentMatchers.<Wrapper<IamUserEntity>>any()))
                .thenReturn(newUser());

        Optional<IamUserEntity> result = repository.findByEmployeeNo(EMP_NO);

        assertThat(result).isPresent();
        assertThat(result.get().getEmployeeNo()).isEqualTo(EMP_NO);
        verify(iamUserMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<IamUserEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("employee_no =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(EMP_NO);
    }

    @Test
    void findByEmployeeNoReturnsEmptyWhenNotFound() {
        when(iamUserMapper.selectOne(ArgumentMatchers.<Wrapper<IamUserEntity>>any())).thenReturn(null);

        assertThat(repository.findByEmployeeNo("ghost-emp")).isEmpty();
    }

    @Test
    void findBySsoSubjectFiltersSsoSubjectColumn() {
        when(iamUserMapper.selectOne(ArgumentMatchers.<Wrapper<IamUserEntity>>any()))
                .thenReturn(newUser());

        Optional<IamUserEntity> result = repository.findBySsoSubject(SSO_SUBJECT);

        assertThat(result).isPresent();
        verify(iamUserMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<IamUserEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("sso_subject =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(SSO_SUBJECT);
    }

    @Test
    void updateReturnsAffectedRows() {
        IamUserEntity user = newUser();
        user.setId(USER_ID);
        user.setVersion(3);
        when(iamUserMapper.updateById(same(user))).thenReturn(1);

        assertThat(repository.update(user)).isEqualTo(1);
    }

    @Test
    void updateReturnsZeroOnOptimisticLockConflict() {
        IamUserEntity user = newUser();
        user.setId(USER_ID);
        user.setVersion(3);
        when(iamUserMapper.updateById(same(user))).thenReturn(0);

        assertThat(repository.update(user)).isZero();
    }
}
