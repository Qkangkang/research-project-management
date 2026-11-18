package cn.edu.rpm.iam.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PasswordEncoderService 单元测试（V10 认证模块）。
 */
class PasswordEncoderServiceTest {

    private PasswordEncoderService encoder;

    @BeforeEach
    void setUp() {
        encoder = new PasswordEncoderService();
    }

    @Test
    void encode_producesBcryptHash() {
        String hash = encoder.encode("Admin@123");
        assertThat(hash).startsWith("$2a$10$");
    }

    @Test
    void matches_correctPassword_returnsTrue() {
        String hash = encoder.encode("Admin@123");
        assertThat(encoder.matches("Admin@123", hash)).isTrue();
    }

    @Test
    void matches_wrongPassword_returnsFalse() {
        String hash = encoder.encode("Admin@123");
        assertThat(encoder.matches("WrongPass", hash)).isFalse();
    }

    @Test
    void matches_nullInputs_returnsFalse() {
        assertThat(encoder.matches(null, "$2a$10$someHash")).isFalse();
        assertThat(encoder.matches("Admin@123", null)).isFalse();
    }

    @Test
    void encode_emptyPassword_throwsException() {
        assertThatThrownBy(() -> encoder.encode(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encode_samePasswordProducesDifferentHashes() {
        // BCrypt 自带随机盐，同一密码生成的哈希值不同
        String hash1 = encoder.encode("Admin@123");
        String hash2 = encoder.encode("Admin@123");
        assertThat(hash1).isNotEqualTo(hash2);
        // 但两个哈希都应该与原始密码匹配
        assertThat(encoder.matches("Admin@123", hash1)).isTrue();
        assertThat(encoder.matches("Admin@123", hash2)).isTrue();
    }
}
