package cn.edu.rpm.iam.application.dto;

import java.util.Objects;

/**
 * 受控动作（技术文档 03 §2.1 {@code AuthorizationService.check} 的入参）。
 *
 * <p>{@code code} 必须为权限点字典中的权限码（如 {@code project:view}、
 * {@code fund:expense:submit}），不得在调用侧使用自造字符串；临时授权按同一
 * {@code permission_code} 口径匹配资源级追加权限。</p>
 *
 * @param code 权限码
 */
public record Action(String code) {

    /** 校验权限码非空白。 */
    public Action {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("动作权限码不能为空");
        }
        code = code.trim();
    }

    /**
     * 以权限码构造动作。
     *
     * @param code 权限码，如 {@code project:view}
     * @return 动作实例
     */
    public static Action of(String code) {
        return new Action(code);
    }

    /**
     * 判断两个动作是否同一权限码。
     *
     * @param other 另一动作
     * @return 权限码相同返回 {@code true}
     */
    public boolean sameAs(Action other) {
        return other != null && Objects.equals(code, other.code);
    }
}
