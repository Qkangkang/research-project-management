package cn.edu.rpm.audit.application.service;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 审计前后摘要服务（T-AUD-003；技术文档 03 §4.2）。
 *
 * <p>把方法参数/返回值规范化为 JSON 后计算 SHA-256，只落摘要、不落业务明文
 * （身份证、银行账号、评审意见等敏感值不会出现在 {@code audit_log}）。
 * JSON 属性按字典序输出，Map 键排序，保证同一语义对象摘要稳定。</p>
 *
 * <p>不可序列化的参数（如请求对象、流）不阻断审计：记录告警后回退为
 * {@code String.valueOf} 摘要，保证关键动作始终留痕。</p>
 */
public class AuditDigestService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditDigestService.class);

    private static final String SHA_256 = "SHA-256";

    private final ObjectMapper objectMapper;

    /**
     * 使用默认规范化配置构造摘要服务。
     */
    public AuditDigestService() {
        this(defaultMapper());
    }

    /**
     * 使用外部 {@link ObjectMapper} 构造摘要服务（测试可注入固定配置）。
     *
     * @param objectMapper Jackson 实例
     */
    public AuditDigestService(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    /**
     * 计算对象摘要。
     *
     * @param value 待摘要对象（方法参数数组、返回值等）；为 {@code null} 时返回 {@code null}
     * @return 64 位小写十六进制 SHA-256；{@code null} 输入返回 {@code null}
     */
    public String digest(Object value) {
        if (value == null) {
            return null;
        }
        byte[] canonical;
        try {
            canonical = objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            LOG.warn("审计摘要序列化失败，回退为字符串摘要 type={}", value.getClass().getName(), e);
            canonical = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        }
        return sha256Hex(canonical);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private static ObjectMapper defaultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return mapper;
    }
}
