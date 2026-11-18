package cn.edu.rpm.common.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * uuid 文本互转工具：数据库列（{@code uuid}）与领域契约（{@link UUID}）之间的唯一转换入口。
 *
 * <p>背景：MyBatis-Plus {@code IdType.ASSIGN_UUID} 生成 32 位无连字符文本，PostgreSQL 读取
 * {@code uuid} 列时也可能返回 32 位十六进制；而 JDK 15+ 的 {@link UUID#fromString(String)}
 * 只接受标准 8-4-4-4-12 形态。为避免各模块重复实现且口径不一，统一使用本工具：</p>
 * <ul>
 *   <li>{@link #parse(String)} 同时兼容 32 位无连字符与标准形态；非法输入抛
 *       {@link IllegalArgumentException}（快速失败，禁止静默返回 {@code null}）；</li>
 *   <li>{@link #format(UUID)} 输出标准形态；PostgreSQL 与 JDBC（{@code stringtype=unspecified}）
 *       均可直接解析。</li>
 * </ul>
 */
public final class UuidUtils {

    private static final Pattern COMPACT = Pattern.compile("[0-9a-fA-F]{32}");

    private UuidUtils() {
    }

    /**
     * 解析 uuid 文本（兼容 32 位无连字符与标准 8-4-4-4-12 形态）。
     *
     * @param text uuid 文本
     * @return 解析后的 {@link UUID}
     * @throws IllegalArgumentException 文本为 {@code null}/空白或形态非法
     */
    public static UUID parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("uuid 文本不能为空");
        }
        String trimmed = text.trim();
        if (COMPACT.matcher(trimmed).matches()) {
            String canonical = trimmed.substring(0, 8) + "-" + trimmed.substring(8, 12) + "-"
                    + trimmed.substring(12, 16) + "-" + trimmed.substring(16, 20) + "-"
                    + trimmed.substring(20);
            return UUID.fromString(canonical);
        }
        return UUID.fromString(trimmed);
    }

    /**
     * 输出标准 8-4-4-4-12 形态的 uuid 文本。
     *
     * @param uuid uuid 值
     * @return 标准形态文本
     */
    public static String format(UUID uuid) {
        return uuid.toString();
    }

    /**
     * 输出 32 位无连字符形态的 uuid 文本（与 MyBatis-Plus {@code ASSIGN_UUID} 及
     * 实体主键文本一致，作为数据库查询/缓存 key 的统一入参形态）。
     *
     * @param uuid uuid 值
     * @return 32 位十六进制文本
     */
    public static String formatCompact(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    /**
     * 判断两个 uuid 文本是否表示同一个值（兼容 32 位无连字符与标准 8-4-4-4-12 形态）。
     *
     * <p>数据库 {@code uuid} 列经 JDBC 读取后固定返回标准形态，而应用内部大量使用
     * {@link #formatCompact(UUID)} 作为查询/写入参数，直接字符串比较会产生误判。</p>
     *
     * @param left  左侧 uuid 文本
     * @param right 右侧 uuid 文本
     * @return 两者均非空且表示同一 uuid 时返回 {@code true}
     */
    public static boolean sameId(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        try {
            return parse(left).equals(parse(right));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 判断 uuid 文本是否表示指定 uuid（兼容 32 位无连字符与标准 8-4-4-4-12 形态）。
     *
     * @param stored 存储/接口中的 uuid 文本
     * @param value  uuid 值
     * @return 文本非空且表示该 uuid 时返回 {@code true}
     */
    public static boolean sameId(String stored, UUID value) {
        if (stored == null || value == null) {
            return false;
        }
        try {
            return parse(stored).equals(value);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
