package cn.edu.rpm.iam.domain.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密服务（V10 认证模块）。
 *
 * <p>使用 BCrypt 算法（Spring Security 推荐，自带盐值，strength=10）。
 * 技术文档 §10.3：全站 HTTPS，密码使用成熟慢哈希，Token 可撤销。</p>
 */
public class PasswordEncoderService {

    private final PasswordEncoder passwordEncoder;

    public PasswordEncoderService() {
        // BCrypt strength=10（Spring Security 默认）
        this.passwordEncoder = new BCryptPasswordEncoder(10);
    }

    /**
     * 加密明文密码。
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希值
     */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 验证密码。
     *
     * @param rawPassword     明文密码
     * @param encodedPassword BCrypt 哈希值
     * @return 是否匹配
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
