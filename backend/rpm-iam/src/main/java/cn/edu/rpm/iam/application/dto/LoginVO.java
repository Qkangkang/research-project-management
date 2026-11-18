package cn.edu.rpm.iam.application.dto;

/**
 * 登录响应 VO（V10 认证模块）。
 *
 * <p>与前端 src/api/types.ts LoginResponse 对齐。</p>
 */
public class LoginVO {

    /** 访问 Token */
    private String accessToken;

    /** 刷新 Token */
    private String refreshToken;

    /** Access Token 有效期（秒）*/
    private long expiresIn;

    /** 用户信息 */
    private UserInfoVO userInfo;

    public LoginVO(String accessToken, String refreshToken, long expiresIn, UserInfoVO userInfo) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.userInfo = userInfo;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public UserInfoVO getUserInfo() {
        return userInfo;
    }
}
