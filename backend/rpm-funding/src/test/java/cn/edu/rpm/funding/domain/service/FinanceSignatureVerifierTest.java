package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FinanceSignatureVerifier} 纯单元测试（T-FUND-026；TC-FUND-049）。
 */
class FinanceSignatureVerifierTest {

    private static final String SECRET = "finance-callback-secret";
    private static final String BODY = "{\"external_event_id\":\"FIN-EVT-1\",\"amount\":30000.00}";

    @Test
    void validSignaturePasses() throws Exception {
        FinanceSignatureVerifier verifier = new FinanceSignatureVerifier(SECRET);

        assertThatCode(() -> verifier.verify(BODY, hmac(BODY)))
                .doesNotThrowAnyException();
    }

    @Test
    void tamperedBodyThrowsInt002() throws Exception {
        FinanceSignatureVerifier verifier = new FinanceSignatureVerifier(SECRET);
        String signature = hmac(BODY);

        assertThatThrownBy(() -> verifier.verify(BODY.replace("30000", "90000"), signature))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INT_002);
    }

    @Test
    void missingSignatureThrowsInt002() {
        FinanceSignatureVerifier verifier = new FinanceSignatureVerifier(SECRET);

        assertThatThrownBy(() -> verifier.verify(BODY, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INT_002);
        assertThatThrownBy(() -> verifier.verify(BODY, "   "))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INT_002);
    }

    @Test
    void blankSecretFailsClosed() throws Exception {
        FinanceSignatureVerifier verifier = new FinanceSignatureVerifier("");

        assertThatThrownBy(() -> verifier.verify(BODY, hmac(BODY)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INT_002);
    }

    private static String hmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
