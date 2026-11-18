package cn.edu.rpm.iam.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.iam.application.dto.LoginCmd;
import cn.edu.rpm.iam.application.dto.LoginVO;
import cn.edu.rpm.iam.domain.model.RoleAssignmentView;
import cn.edu.rpm.iam.domain.port.UserRoleQueryPort;
import cn.edu.rpm.iam.domain.service.FunctionPermissionResolver;
import cn.edu.rpm.iam.domain.service.PasswordEncoderService;
import cn.edu.rpm.iam.infrastructure.entity.IamLoginLogEntity;
import cn.edu.rpm.iam.infrastructure.entity.IamRoleEntity;
import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import cn.edu.rpm.iam.infrastructure.mapper.IamLoginLogMapper;
import cn.edu.rpm.iam.infrastructure.mapper.IamRoleMapper;
import cn.edu.rpm.iam.infrastructure.mapper.IamUserMapper;
import cn.edu.rpm.iam.infrastructure.security.JwtTokenProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthenticationService 单元测试（V10 认证模块）。
 *
 * <p>纯单元测试：所有外部依赖通过 Mockito 模拟，不依赖数据库/Redis。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String SECRET =
            "test-secret-key-must-be-at-least-32-bytes-long-for-hs256";

    @Mock private IamUserMapper userMapper;
    @Mock private IamLoginLogMapper loginLogMapper;
    @Mock private IamRoleMapper roleMapper;
    @Mock private UserRoleQueryPort userRoleQueryPort;
    @Mock private FunctionPermissionResolver permissionResolver;

    private PasswordEncoderService passwordEncoder;
    private JwtTokenProvider jwtProvider;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new PasswordEncoderService();
        jwtProvider = new JwtTokenProvider(SECRET, 3600_000L, 86400_000L, "test");
        service = new AuthenticationService(
                userMapper, loginLogMapper, roleMapper,
                passwordEncoder, jwtProvider, userRoleQueryPort, permissionResolver, 3600L);
    }

    // ─────── login 成功路径 ───────

    @Test
    void login_validCredentials_returnsTokenAndUserInfo() {
        IamUserEntity user = buildActiveUser("EMP001", "Admin@123");
        stubUserQuery("EMP001", user);
        stubRoles(user.getId(), List.of());

        LoginCmd cmd = loginCmd("EMP001", "Admin@123");
        LoginVO vo = service.login(cmd, "127.0.0.1", "TestBrowser");

        assertThat(vo.getAccessToken()).isNotBlank();
        assertThat(vo.getRefreshToken()).isNotBlank();
        assertThat(vo.getExpiresIn()).isEqualTo(3600L);
        assertThat(vo.getUserInfo().getUsername()).isEqualTo("EMP001");
        assertThat(vo.getUserInfo().getRealName()).isEqualTo("测试用户");
        verify(loginLogMapper).insert(any(IamLoginLogEntity.class)); // 应写入登录日志
    }

    @Test
    void login_withRoles_roleCodesPopulatedInToken() {
        IamUserEntity user = buildActiveUser("EMP001", "Admin@123");
        IamRoleEntity adminRole = buildRole("role-001", "ADMIN");
        stubUserQuery("EMP001", user);
        stubRoles(user.getId(), List.of(new RoleAssignmentView("role-001", null)));
        when(roleMapper.selectBatchIds(List.of("role-001"))).thenReturn(List.of(adminRole));

        LoginVO vo = service.login(loginCmd("EMP001", "Admin@123"), "127.0.0.1", null);

        assertThat(vo.getUserInfo().getRoles()).containsExactly("ADMIN");
    }

    @Test
    void login_withRoles_permissionCodesPopulatedInUserInfo() {
        IamUserEntity user = buildActiveUser("EMP001", "Admin@123");
        IamRoleEntity adminRole = buildRole("role-001", "ADMIN");
        stubUserQuery("EMP001", user);
        stubRoles(user.getId(), List.of(new RoleAssignmentView("role-001", null)));
        when(roleMapper.selectBatchIds(List.of("role-001"))).thenReturn(List.of(adminRole));
        when(permissionResolver.resolvePermissions(eq(user.getId()), eq("role-001"), any()))
                .thenReturn(Set.of("project:view", "fund:view"));

        LoginVO vo = service.login(loginCmd("EMP001", "Admin@123"), "127.0.0.1", null);

        assertThat(vo.getUserInfo().getPermissions())
                .containsExactly("fund:view", "project:view");
    }

    // ─────── login 失败路径 ───────

    @Test
    void login_wrongPassword_throws401() {
        IamUserEntity user = buildActiveUser("EMP001", "Admin@123");
        stubUserQuery("EMP001", user);

        assertThatThrownBy(() ->
                service.login(loginCmd("EMP001", "WrongPass"), "127.0.0.1", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工号或密码错误");
        verify(loginLogMapper).insert(any(IamLoginLogEntity.class)); // 失败也要写日志
    }

    @Test
    void login_userNotFound_throws401WithGenericMessage() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // 账号不存在返回同一错误信息（防用户枚举）
        assertThatThrownBy(() ->
                service.login(loginCmd("UNKNOWN", "anypass"), "127.0.0.1", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工号或密码错误");
    }

    @Test
    void login_lockedUser_throws401WithLockMessage() {
        IamUserEntity user = buildUserWithStatus("EMP001", "Admin@123", "LOCKED");
        stubUserQuery("EMP001", user);

        assertThatThrownBy(() ->
                service.login(loginCmd("EMP001", "Admin@123"), "127.0.0.1", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("锁定");
    }

    @Test
    void login_disabledUser_throws401() {
        IamUserEntity user = buildUserWithStatus("EMP001", "Admin@123", "DISABLED");
        stubUserQuery("EMP001", user);

        assertThatThrownBy(() ->
                service.login(loginCmd("EMP001", "Admin@123"), "127.0.0.1", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("禁用");
    }

    @Test
    void login_leavedUser_throws401() {
        IamUserEntity user = buildUserWithStatus("EMP001", "Admin@123", "LEAVED");
        stubUserQuery("EMP001", user);

        assertThatThrownBy(() ->
                service.login(loginCmd("EMP001", "Admin@123"), "127.0.0.1", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("停用");
    }

    @Test
    void login_noPasswordHash_throws401() {
        IamUserEntity user = buildActiveUser("EMP001", "Admin@123");
        user.setPasswordHash(null); // SSO 账号无密码
        stubUserQuery("EMP001", user);

        assertThatThrownBy(() ->
                service.login(loginCmd("EMP001", "Admin@123"), "127.0.0.1", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工号或密码错误");
    }

    // ─────── refresh ───────

    @Test
    void refresh_validRefreshToken_returnsNewTokens() {
        IamUserEntity user = buildActiveUser("user-001", "Admin@123");
        user.setId("user-001");
        String refreshToken = jwtProvider.createRefreshToken("user-001", "EMP001");
        when(userMapper.selectById("user-001")).thenReturn(user);
        stubRoles("user-001", List.of());

        LoginVO vo = service.refresh(refreshToken);
        assertThat(vo.getAccessToken()).isNotBlank();
    }

    @Test
    void refresh_invalidToken_throws401() {
        assertThatThrownBy(() -> service.refresh("invalid.token"))
                .isInstanceOf(BizException.class);
    }

    // ─────── 辅助方法 ───────

    private IamUserEntity buildActiveUser(String employeeNo, String rawPassword) {
        IamUserEntity user = new IamUserEntity();
        user.setId(java.util.UUID.randomUUID().toString());
        user.setEmployeeNo(employeeNo);
        user.setDisplayName("测试用户");
        user.setDeptId("dept-001");
        user.setStatus("ACTIVE");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return user;
    }

    private IamUserEntity buildUserWithStatus(String employeeNo, String rawPassword, String status) {
        IamUserEntity user = buildActiveUser(employeeNo, rawPassword);
        user.setStatus(status);
        return user;
    }

    private IamRoleEntity buildRole(String id, String code) {
        IamRoleEntity role = new IamRoleEntity();
        role.setId(id);
        role.setCode(code);
        return role;
    }

    @SuppressWarnings("unchecked")
    private void stubUserQuery(String employeeNo, IamUserEntity user) {
        when(userMapper.selectOne(any())).thenReturn(user);
    }

    private void stubRoles(String userId, List<RoleAssignmentView> assignments) {
        when(userRoleQueryPort.findValidAssignments(eq(userId), any())).thenReturn(assignments);
        if (!assignments.isEmpty()) {
            List<String> roleIds = assignments.stream()
                    .map(RoleAssignmentView::roleId).toList();
            when(roleMapper.selectBatchIds(roleIds)).thenReturn(List.of());
        }
    }

    private LoginCmd loginCmd(String employeeNo, String password) {
        LoginCmd cmd = new LoginCmd();
        cmd.setEmployeeNo(employeeNo);
        cmd.setPassword(password);
        return cmd;
    }
}
