package cn.edu.rpm.common.util;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 规范化 JSON 与 SHA-256 摘要公共工具（Batch-12，任务拆解 T-PRJ-018 风险项）。
 *
 * <p>申报版本与项目基线快照必须使用同一规范化规则，保证同一语义内容可复算同一摘要
 * （键递归排序、数组保序、无多余空白）。本工具从 Batch-9 的
 * {@code ApplicationContentHasher} 抽取到 {@code rpm-common}，避免跨模块调用；
 * 申报侧保留原类并委托本工具，行为不变。</p>
 *
 * <p>纯 Java，不依赖 Spring；序列化失败属于不可提交的输入，按 {@code RPM-SYS-002}
 * 快速失败，禁止以空摘要落库（数据库 CHECK 约束兜底）。</p>
 */
public class CanonicalJsonHasher {

    private final ObjectMapper canonicalMapper;

    /** 构造规范化序列化器（对象键按字典序输出）。 */
    public CanonicalJsonHasher() {
        this.canonicalMapper = JsonMapper.builder()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
    }

    /**
     * 生成规范 JSON（对象键递归排序，数组保序）。
     *
     * @param value 待规范化内容
     * @return 规范 JSON 文本
     * @throws BizException 内容无法序列化 {@code RPM-SYS-002}
     */
    public String canonicalJson(Object value) {
        try {
            return canonicalMapper.writeValueAsString(normalize(value));
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYS_002, "内容无法序列化为规范 JSON");
        }
    }

    /**
     * 计算 UTF-8 文本的 SHA-256。
     *
     * @param canonicalJson 规范 JSON 文本
     * @return 64 位小写十六进制摘要
     * @throws BizException 运行环境缺少 SHA-256 {@code RPM-SYS-002}
     */
    public String sha256(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.SYS_002, "运行环境不支持 SHA-256");
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), normalize(item)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalize(item));
            }
            return normalized;
        }
        return value;
    }
}
