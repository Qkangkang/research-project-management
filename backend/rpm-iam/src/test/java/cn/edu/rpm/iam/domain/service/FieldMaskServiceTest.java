package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.domain.model.FieldPolicyCodes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FieldMaskService} 纯单元测试（T-IAM-010 先行测试，技术文档 03 §2.4）。
 *
 * <p>覆盖验证用例：</p>
 * <ul>
 *   <li>TC-IAM-005：项目成员无财务字段权限时，{@code bankAccount} 只返回后四位
 *       {@code **** **** **** 3456}，明文不出现在 DTO；</li>
 *   <li>TC-IAM-006：专家身份对申请人隐藏时，{@code reviewerName / reviewerDept}
 *       返回 {@code null}，非敏感字段不变。</li>
 * </ul>
 */
class FieldMaskServiceTest {

    private static final UUID USER_ID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final UUID ROLE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant REQUEST_TIME = Instant.parse("2026-11-17T10:00:00Z");

    private final FieldMaskService service = new FieldMaskService();

    private static AuthContext contextWith(Set<String> heldPermissionCodes) {
        return new AuthContext(USER_ID, ROLE_ID, "E1001", heldPermissionCodes, Set.of(), REQUEST_TIME);
    }

    @Test
    void masksBankAccountToLastFourDigitsWhenFinancePolicyNotGranted() {
        Payee payee = new Payee("张三", "1234567890123456", "招商银行");
        ExpenseVo vo = new ExpenseVo("EXP-2026-0001", payee);

        ExpenseVo masked = service.mask(vo, contextWith(Set.of()), Set.of(FieldPolicyCodes.FINANCE));

        assertThat(masked.getPayee().getBankAccount()).isEqualTo("**** **** **** 3456");
        assertThat(masked.getPayee().getBankAccount()).doesNotContain("1234567890");
        assertThat(masked.getPayee().getAccountName()).isEqualTo("张三");
        assertThat(masked.getPayee().getBankName()).isEqualTo("招商银行");
        assertThat(masked.getExpenseNo()).isEqualTo("EXP-2026-0001");
    }

    @Test
    void leavesBankAccountUntouchedWhenFinancePolicyGranted() {
        Payee payee = new Payee("张三", "1234567890123456", "招商银行");
        ExpenseVo vo = new ExpenseVo("EXP-2026-0001", payee);

        service.mask(vo, contextWith(Set.of(FieldPolicyCodes.FINANCE)), Set.of(FieldPolicyCodes.FINANCE));

        assertThat(payee.getBankAccount()).isEqualTo("1234567890123456");
    }

    @Test
    void leavesTargetUnchangedWhenNoPolicyRequested() {
        Payee payee = new Payee("张三", "1234567890123456", "招商银行");
        ExpenseVo vo = new ExpenseVo("EXP-2026-0001", payee);

        service.mask(vo, contextWith(Set.of()), Set.of());

        assertThat(payee.getBankAccount()).isEqualTo("1234567890123456");
    }

    @Test
    void hidesExpertIdentityButKeepsNonSensitiveFields() {
        ReviewVo vo = new ReviewVo("李四", "智能感知研究所", "研究方案可行，建议立项");
        ReviewVo masked = service.mask(vo, contextWith(Set.of()), Set.of(FieldPolicyCodes.EXPERT_IDENTITY));

        assertThat(masked.getReviewerName()).isNull();
        assertThat(masked.getReviewerDept()).isNull();
        assertThat(masked.getComment()).isEqualTo("研究方案可行，建议立项");
    }

    @Test
    void keepsExpertIdentityWhenGranted() {
        ReviewVo vo = new ReviewVo("李四", "智能感知研究所", "研究方案可行");
        service.mask(vo, contextWith(Set.of(FieldPolicyCodes.EXPERT_IDENTITY)), Set.of(FieldPolicyCodes.EXPERT_IDENTITY));

        assertThat(vo.getReviewerName()).isEqualTo("李四");
        assertThat(vo.getReviewerDept()).isEqualTo("智能感知研究所");
    }

    @Test
    void masksIdCardKeepingFirstAndLastCharacter() {
        PersonVo vo = new PersonVo("32010219900101123X", null);
        service.mask(vo, contextWith(Set.of()), Set.of(FieldPolicyCodes.ID_CARD));

        assertThat(vo.getIdCard()).startsWith("3").endsWith("X").hasSize(18);
        assertThat(vo.getIdCard()).isEqualTo("3****************X");
    }

    @Test
    void masksContactKeepingPrefixAndSuffix() {
        PersonVo vo = new PersonVo(null, "13812345678");
        service.mask(vo, contextWith(Set.of()), Set.of(FieldPolicyCodes.CONTACT));

        assertThat(vo.getMobile()).isEqualTo("138****5678");
    }

    @Test
    void nullTargetReturnsNull() {
        String masked = service.mask(null, contextWith(Set.of()), Set.of(FieldPolicyCodes.FINANCE));

        assertThat(masked).isNull();
    }

    /**
     * 测试用可变 VO：收款信息。
     */
    static class Payee {
        private String accountName;
        private String bankAccount;
        private String bankName;

        Payee(String accountName, String bankAccount, String bankName) {
            this.accountName = accountName;
            this.bankAccount = bankAccount;
            this.bankName = bankName;
        }

        String getAccountName() {
            return accountName;
        }

        String getBankAccount() {
            return bankAccount;
        }

        String getBankName() {
            return bankName;
        }
    }

    /**
     * 测试用可变 VO：支出详情（嵌套收款信息）。
     */
    static class ExpenseVo {
        private String expenseNo;
        private Payee payee;

        ExpenseVo(String expenseNo, Payee payee) {
            this.expenseNo = expenseNo;
            this.payee = payee;
        }

        String getExpenseNo() {
            return expenseNo;
        }

        Payee getPayee() {
            return payee;
        }
    }

    /**
     * 测试用可变 VO：评审意见。
     */
    static class ReviewVo {
        private String reviewerName;
        private String reviewerDept;
        private String comment;

        ReviewVo(String reviewerName, String reviewerDept, String comment) {
            this.reviewerName = reviewerName;
            this.reviewerDept = reviewerDept;
            this.comment = comment;
        }

        String getReviewerName() {
            return reviewerName;
        }

        String getReviewerDept() {
            return reviewerDept;
        }

        String getComment() {
            return comment;
        }
    }

    /**
     * 测试用可变 VO：证件与联系方式。
     */
    static class PersonVo {
        private String idCard;
        private String mobile;

        PersonVo(String idCard, String mobile) {
            this.idCard = idCard;
            this.mobile = mobile;
        }

        String getIdCard() {
            return idCard;
        }

        String getMobile() {
            return mobile;
        }
    }
}
