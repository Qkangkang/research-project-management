package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 财务回调 HMAC-SHA256 验签（T-FUND-026；技术文档 05 §3.3、07 §4.3）。
 *
 * <p>按配置密钥对原始报文计算 HMAC-SHA256，使用 {@link MessageDigest#isEqual}
 * 常量时间比较；签名缺失、密钥未配置或不匹配统一返回 {@code RPM-INT-002}
 * （失败关闭，禁止放行未验签回调）。</p>
 */
@Component
public class FinanceSignatureVerifier {

    /** HMAC 算法。 */
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    /**
     * 构造验签器。
     *
     * @param secret 回调签名密钥（{@code rpm.finance.callback.secret}）
     */
    public FinanceSignatureVerifier(@Value("${rpm.finance.callback.secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 校验 UTF-8 报文签名。
     *
     * @param rawBody   原始报文
     * @param signature 调用方签名（十六进制，大小写不敏感）
     * @throws BizException 签名缺失、密钥未配置或校验失败 {@code RPM-INT-002}
     */
    public void verify(String rawBody, String signature) {
        if (rawBody == null) {
            throw new BizException(ErrorCode.INT_002, "回调报文不能为空");
        }
        verify(rawBody.getBytes(StandardCharsets.UTF_8), signature);
    }

    /**
     * 校验原始字节报文签名。
     *
     * @param rawBody   原始报文字节
     * @param signature 调用方签名（十六进制，大小写不敏感）
     * @throws BizException 签名缺失、密钥未配置或校验失败 {@code RPM-INT-002}
     */
    public void verify(byte[] rawBody, String signature) {
        if (signature == null || signature.isBlank() || secret.length == 0) {
            throw new BizException(ErrorCode.INT_002, "回调签名缺失或未配置密钥");
        }
        byte[] expected = hmac(rawBody).getBytes(StandardCharsets.UTF_8);
        byte[] provided = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new BizException(ErrorCode.INT_002, "回调签名校验失败");
        }
    }

    private String hmac(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (GeneralSecurityException ex) {
            throw new BizException(ErrorCode.INT_002, "回调签名计算失败");
        }
    }
}
