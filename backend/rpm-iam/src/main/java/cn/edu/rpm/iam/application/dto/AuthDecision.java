package cn.edu.rpm.iam.application.dto;

import cn.edu.rpm.common.exception.ErrorCode;

import java.util.Objects;
import java.util.Set;

/**
 * 授权判定结果（T-IAM-007，技术文档 03 §2.1）。
 *
 * <p>拒绝时不携带任何资源元数据（标题、编号、是否存在），防止越权探测
 * （TC-IAM-002 / TC-IAM-003）；{@code reasonCode} 使用统一错误码字符串，放行时为
 * {@link #REASON_ALLOW}。</p>
 *
 * @param allowed      是否放行
 * @param reasonCode   放行 {@code ALLOW} 或错误码（如 {@code RPM-COMMON-403}）
 * @param maskedFields 本次判定关联的脱敏字段名集合（无脱敏时为空集合）
 */
public record AuthDecision(boolean allowed, String reasonCode, Set<String> maskedFields) {

    /** 放行原因码。 */
    public static final String REASON_ALLOW = "ALLOW";

    /** 规范化脱敏字段集合并校验原因码。 */
    public AuthDecision {
        Objects.requireNonNull(reasonCode, "reasonCode 不能为空");
        maskedFields = maskedFields == null ? Set.of() : Set.copyOf(maskedFields);
    }

    /**
     * 构造放行判定。
     *
     * @return {@code allowed=true}、原因 {@code ALLOW}、无脱敏字段
     */
    public static AuthDecision allow() {
        return new AuthDecision(true, REASON_ALLOW, Set.of());
    }

    /**
     * 构造拒绝判定（不泄露资源是否存在）。
     *
     * @param errorCode 拒绝错误码，如 {@link ErrorCode#COMMON_403}
     * @return {@code allowed=false} 的判定
     */
    public static AuthDecision deny(ErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "errorCode 不能为空");
        return new AuthDecision(false, errorCode.getCode(), Set.of());
    }
}
