package cn.edu.rpm.iam.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.LoginCmd;
import cn.edu.rpm.iam.application.dto.LoginVO;
import cn.edu.rpm.iam.application.dto.UserInfoVO;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 登录认证应用服务（V10 认证模块）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>本地账号验证（工号 + 密码）；</li>
 *   <li>账号状态校验（ACTIVE/LEAVED/DISABLED/LOCKED）；</li>
 *   <li>JWT 生成（access_token + refresh_token）；</li>
 *   <li>登录日志记录（追加，不可修改）；</li>
 *   <li>last_login_at 更新。</li>
 * </ul>
 *
 * <p>安全规则：</p>
 * <ul>
 *   <li>账号不存在与密码错误返回同一错误信息（防用户枚举）；</li>
 *   <li>登录日志记录失败原因，但不向调用方暴露内部细节。</li>
 * </ul>
 */
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private static final String GENERIC_AUTH_ERROR = "工号或密码错误";

    /**
     * 账号不存在时的占位 BCrypt 哈希（与 V10 初始管理员密码同强度 strength=10）。
     * 作用：让“账号不存在”路径也执行一次完整的 BCrypt 匹配，消除计时差异（防用户名枚举）。
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$g/HsTpDOeYxNGD.QhlEMBOxLl4rugNe3LPxlOWjVrV0Myk0Y9sv7q";

    private final IamUserMapper userMapper;
    private final IamLoginLogMapper loginLogMapper;
    private final IamRoleMapper roleMapper;
    private final PasswordEncoderService passwordEncoder;
    private final JwtTokenProvider jwtProvider;
    private final UserRoleQueryPort userRoleQueryPort;
    private final FunctionPermissionResolver permissionResolver;
    private final long accessTokenValiditySec;

    public AuthenticationService(IamUserMapper userMapper,
                                 IamLoginLogMapper loginLogMapper,
                                 IamRoleMapper roleMapper,
                                 PasswordEncoderService passwordEncoder,
                                 JwtTokenProvider jwtProvider,
                                 UserRoleQueryPort userRoleQueryPort,
                                 FunctionPermissionResolver permissionResolver,
                                 long accessTokenValiditySec) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.userRoleQueryPort = userRoleQueryPort;
        this.permissionResolver = permissionResolver;
        this.accessTokenValiditySec = accessTokenValiditySec;
    }

    /**
     * 本地账号登录。
     *
     * @param cmd      登录命令
     * @param loginIp  客户端 IP（审计）
     * @param userAgent 客户端 UA（审计）
     * @return 登录响应（access_token + refresh_token + 用户信息）
     */
    public LoginVO login(LoginCmd cmd, String loginIp, String userAgent) {
        String employeeNo = cmd.getEmployeeNo().trim();

        // 1. 查询用户（账号不存在也走密码匹配，防止枚举计时差异）
        IamUserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<IamUserEntity>()
                        .eq(IamUserEntity::getEmployeeNo, employeeNo));

        // 2. 密码验证（账号不存在/无密码时用占位哈希执行等耗时的 BCrypt 匹配，消除计时侧信道）
        String effectiveHash = (user != null && user.getPasswordHash() != null)
                ? user.getPasswordHash() : DUMMY_PASSWORD_HASH;
        boolean hashMatched = passwordEncoder.matches(cmd.getPassword(), effectiveHash);
        boolean passwordOk = user != null && user.getPasswordHash() != null && hashMatched;

        if (user == null || !passwordOk) {
            recordFailLog(user, employeeNo, "账号不存在或密码错误", loginIp, userAgent);
            throw new BizException(ErrorCode.COMMON_401, GENERIC_AUTH_ERROR);
        }

        // 3. 账号状态校验
        checkUserStatus(user, loginIp, userAgent, employeeNo);

        // 4. 加载角色
        OffsetDateTime now = OffsetDateTime.now();
        List<RoleAssignmentView> roles = userRoleQueryPort.findValidAssignments(user.getId(), now);
        List<String> roleCodes = resolveRoleCodes(roles);
        List<String> permissionCodes = resolvePermissionCodes(user.getId(), roles, now);

        // 5. 生成 Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleCodes);
        String accessToken = jwtProvider.createAccessToken(user.getId(), employeeNo, claims);
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), employeeNo);

        // 6. 更新 last_login_at（仅更新该单列，不触发 AuditMetaObjectHandler 的 uuid 类型校验）
        userMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<IamUserEntity>()
                        .setSql("last_login_at = now()")
                        .eq("id", user.getId()));

        // 7. 记录成功日志
        recordSuccessLog(user, loginIp, userAgent);

        // 8. 组装用户信息
        UserInfoVO userInfo = buildUserInfo(user, roleCodes, permissionCodes);
        return new LoginVO(accessToken, refreshToken, accessTokenValiditySec, userInfo);
    }

    /**
     * 通过 refresh_token 刷新 access_token。
     *
     * @param refreshToken 刷新 Token
     * @return 新的登录响应
     */
    public LoginVO refresh(String refreshToken) {
        // 只接受 type=refresh 的 Token（access token 不能用于滚动续期）
        if (!jwtProvider.validateRefreshToken(refreshToken)) {
            throw new BizException(ErrorCode.COMMON_401, "刷新 Token 无效或已过期");
        }
        String userId = jwtProvider.getUserId(refreshToken);
        String employeeNo = jwtProvider.getEmployeeNo(refreshToken);

        IamUserEntity user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.COMMON_401, "账号状态异常");
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<RoleAssignmentView> roles = userRoleQueryPort.findValidAssignments(userId, now);
        List<String> roleCodes = resolveRoleCodes(roles);
        List<String> permissionCodes = resolvePermissionCodes(userId, roles, now);

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleCodes);
        String newAccessToken = jwtProvider.createAccessToken(userId, employeeNo, claims);
        String newRefreshToken = jwtProvider.createRefreshToken(userId, employeeNo);

        // 记录刷新日志
        IamLoginLogEntity logEntry = new IamLoginLogEntity();
        logEntry.setId(UUID.randomUUID().toString());
        logEntry.setUserId(userId);
        logEntry.setEmployeeNo(employeeNo);
        logEntry.setLoginType("REFRESH");
        logEntry.setLoginResult("SUCCESS");
        logEntry.setLoginAt(OffsetDateTime.now());
        loginLogMapper.insert(logEntry);

        UserInfoVO userInfo = buildUserInfo(user, roleCodes, permissionCodes);
        return new LoginVO(newAccessToken, newRefreshToken, accessTokenValiditySec, userInfo);
    }

    /**
     * 获取当前用户信息（Token 解析后查询最新角色）。
     *
     * @param userId 用户 ID（由 JwtAuthFilter 解析）
     * @return 用户信息
     */
    public UserInfoVO getUserInfo(String userId) {
        IamUserEntity user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.COMMON_401, "账号状态异常");
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<RoleAssignmentView> roles = userRoleQueryPort.findValidAssignments(userId, now);
        List<String> roleCodes = resolveRoleCodes(roles);
        List<String> permissionCodes = resolvePermissionCodes(userId, roles, now);
        return buildUserInfo(user, roleCodes, permissionCodes);
    }

    // ─────────── 私有方法 ───────────

    private void checkUserStatus(IamUserEntity user, String loginIp, String userAgent, String employeeNo) {
        String status = user.getStatus();
        switch (status) {
            case "LOCKED" -> {
                recordFailLog(user, employeeNo, "账号已锁定", loginIp, userAgent);
                throw new BizException(ErrorCode.COMMON_401, "账号已被锁定，请联系管理员");
            }
            case "DISABLED" -> {
                recordFailLog(user, employeeNo, "账号已禁用", loginIp, userAgent);
                throw new BizException(ErrorCode.COMMON_401, "账号已被禁用");
            }
            case "LEAVED" -> {
                recordFailLog(user, employeeNo, "账号已离职", loginIp, userAgent);
                throw new BizException(ErrorCode.COMMON_401, "账号已被停用");
            }
            default -> {
                if (!"ACTIVE".equals(status)) {
                    recordFailLog(user, employeeNo, "账号状态异常:" + status, loginIp, userAgent);
                    throw new BizException(ErrorCode.COMMON_401, GENERIC_AUTH_ERROR);
                }
            }
        }
    }

    private UserInfoVO buildUserInfo(IamUserEntity user, List<String> roleCodes,
                                     List<String> permissionCodes) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getEmployeeNo());
        vo.setRealName(user.getDisplayName());
        vo.setRoles(roleCodes);
        vo.setPermissions(permissionCodes);
        vo.setDepartmentId(user.getDeptId());
        vo.setEmail(user.getEmail());
        return vo;
    }

    private void recordSuccessLog(IamUserEntity user, String loginIp, String userAgent) {
        IamLoginLogEntity logEntry = new IamLoginLogEntity();
        logEntry.setId(UUID.randomUUID().toString());
        logEntry.setUserId(user.getId());
        logEntry.setEmployeeNo(user.getEmployeeNo());
        logEntry.setLoginType("LOCAL");
        logEntry.setLoginIp(loginIp);
        logEntry.setUserAgent(truncate(userAgent, 512));
        logEntry.setLoginResult("SUCCESS");
        logEntry.setLoginAt(OffsetDateTime.now());
        try {
            loginLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("登录日志写入失败，不影响登录", e);
        }
    }

    private void recordFailLog(IamUserEntity user, String employeeNo, String reason,
                               String loginIp, String userAgent) {
        IamLoginLogEntity logEntry = new IamLoginLogEntity();
        logEntry.setId(UUID.randomUUID().toString());
        if (user != null) logEntry.setUserId(user.getId());
        logEntry.setEmployeeNo(employeeNo);
        logEntry.setLoginType("LOCAL");
        logEntry.setLoginIp(loginIp);
        logEntry.setUserAgent(truncate(userAgent, 512));
        logEntry.setLoginResult("FAILURE");
        logEntry.setFailureReason(truncate(reason, 256));
        logEntry.setLoginAt(OffsetDateTime.now());
        try {
            loginLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("登录失败日志写入失败", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 通过角色 ID 列表查询角色 code 列表。
     *
     * <p>RoleAssignmentView 只含 roleId，需通过 Mapper 查出 code（T-IAM-009）。</p>
     */
    private List<String> resolveRoleCodes(List<RoleAssignmentView> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        List<String> roleIds = assignments.stream()
                .map(RoleAssignmentView::roleId)
                .distinct()
                .collect(Collectors.toList());
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(IamRoleEntity::getCode)
                .collect(Collectors.toList());
    }

    /**
     * 合并用户全部有效角色的功能权限码，供前端菜单/按钮与路由守卫使用。
     *
     * <p>权限快照与请求鉴权共用 {@link FunctionPermissionResolver}，避免登录态与后端
     * {@code @RequirePermission} 判定口径不一致；最终数据访问仍由后端授权服务裁决。</p>
     */
    private List<String> resolvePermissionCodes(String userId, List<RoleAssignmentView> assignments,
                                                OffsetDateTime at) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        Set<String> merged = new LinkedHashSet<>();
        for (RoleAssignmentView assignment : assignments) {
            Set<String> codes = permissionResolver.resolvePermissions(userId, assignment.roleId(), at);
            if (codes != null) {
                merged.addAll(codes);
            }
        }
        return merged.stream().sorted().toList();
    }
}
