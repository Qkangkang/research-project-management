package cn.edu.rpm.iam.api;

import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.iam.application.dto.LoginCmd;
import cn.edu.rpm.iam.application.dto.LoginVO;
import cn.edu.rpm.iam.application.dto.UserInfoVO;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（V10 认证模块）。
 *
 * <p>接口设计与前端 {@code src/api/iam.ts} 对齐：</p>
 * <ul>
 *   <li>POST /api/v1/auth/login       本地账号登录</li>
 *   <li>POST /api/v1/auth/logout      登出（无状态，前端清理 Token 即可）</li>
 *   <li>GET  /api/v1/auth/user-info   获取当前登录用户信息</li>
 *   <li>POST /api/v1/auth/refresh-token 刷新 Token</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authService;

    public AuthController(AuthenticationService authService) {
        this.authService = authService;
    }

    /**
     * 登录。
     *
     * @param cmd 登录命令
     * @return 登录响应
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginCmd cmd, HttpServletRequest request) {
        String ip = getClientIp(request);
        String ua = request.getHeader("User-Agent");
        LoginVO vo = authService.login(cmd, ip, ua);
        return Result.ok(vo);
    }

    /**
     * 登出（无状态，前端清理 Token 即可）。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        AuthContextHolder.clear();
        return Result.ok(null);
    }

    /**
     * 获取当前用户信息（需要登录）。
     */
    @GetMapping("/user-info")
    public Result<UserInfoVO> userInfo() {
        var ctx = AuthContextHolder.require();
        UserInfoVO vo = authService.getUserInfo(ctx.userId().toString());
        return Result.ok(vo);
    }

    /**
     * 刷新 Token。
     */
    @PostMapping("/refresh-token")
    public Result<LoginVO> refreshToken(HttpServletRequest request) {
        String refreshToken = request.getHeader("Authorization");
        if (refreshToken != null && refreshToken.startsWith("Bearer ")) {
            refreshToken = refreshToken.substring(7).trim();
        }
        LoginVO vo = authService.refresh(refreshToken);
        return Result.ok(vo);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
