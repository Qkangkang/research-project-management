package cn.edu.rpm.common.domain.port;

/**
 * 数据库序列取值端口（T-APP-009）。
 *
 * <p>由基础设施适配器以 {@code SELECT nextval(?::regclass)} 实现；数据库序列
 * 保证并发生成不重号。调用方必须先校验序列名白名单格式，禁止把任意文本拼入 SQL。</p>
 */
public interface SequenceValuePort {

    /**
     * 取下一个序列值。
     *
     * @param sequenceName 已在 {@code number_rule.sequence_name} 配置的序列名
     * @return 单调递增的序列值（大于 0）
     */
    long nextValue(String sequenceName);
}
