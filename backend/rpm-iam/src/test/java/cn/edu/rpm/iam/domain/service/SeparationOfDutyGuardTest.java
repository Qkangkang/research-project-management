package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.iam.application.dto.ApprovalContext;
import cn.edu.rpm.iam.application.dto.ExpertContext;
import cn.edu.rpm.iam.domain.model.CoiDeclarationStatus;
import cn.edu.rpm.iam.domain.model.CoiDeclarationView;
import cn.edu.rpm.iam.domain.port.CoiDeclarationQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SeparationOfDutyGuard} 纯单元测试（T-IAM-011 先行测试，技术文档 03 §2.6）。
 *
 * <p>覆盖验证用例：</p>
 * <ul>
 *   <li>TC-IAM-011：同一 {@code user_id} 审批本人申报 → {@code RPM-WF-003}；</li>
 *   <li>TC-IAM-012：专家与申请人同部门且未声明/未确认回避 → {@code RPM-WF-006}；</li>
 *   <li>TC-IAM-014：同一人员同时为 PI 与财务终审人 → {@code RPM-WF-006}。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SeparationOfDutyGuardTest {

    private static final UUID APPLICANT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID APPROVER = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID PI = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID FINANCE_FINAL = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID EXPERT = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID DEPT_CSE = UUID.fromString("d1111111-1111-1111-1111-111111111111");
    private static final UUID DEPT_MATH = UUID.fromString("d2222222-2222-2222-2222-222222222222");
    private static final UUID BIZ_ID = UUID.fromString("99999999-0000-0000-0000-000000000009");
    private static final String BIZ_ID_TEXT = "99999999000000000000000000000009";
    private static final String EXPERT_ID_TEXT = "eeeeeeee000000000000000000000005";

    @Mock
    private CoiDeclarationQueryPort coiDeclarationQueryPort;

    private SeparationOfDutyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SeparationOfDutyGuard(coiDeclarationQueryPort);
    }

    @Test
    void applicantApprovingOwnSubmissionIsRejectedWithWf003() {
        ApprovalContext ctx = new ApprovalContext(APPLICANT, APPLICANT, PI, null);

        assertThatThrownBy(() -> guard.checkApproval(ctx))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.WF_003));
    }

    @Test
    void differentApproverPasses() {
        ApprovalContext ctx = new ApprovalContext(APPLICANT, APPROVER, PI, FINANCE_FINAL);

        assertThatCode(() -> guard.checkApproval(ctx)).doesNotThrowAnyException();
    }

    @Test
    void piWhoIsAlsoFinanceFinalApproverIsRejectedWithWf006() {
        ApprovalContext ctx = new ApprovalContext(APPLICANT, APPROVER, PI, PI);

        assertThatThrownBy(() -> guard.checkApproval(ctx))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.WF_006));
    }

    @Test
    void expertInSameDeptWithoutResolvedDeclarationIsRejectedWithWf006() {
        when(coiDeclarationQueryPort.findByBizAndExpert("REVIEW", BIZ_ID_TEXT, EXPERT_ID_TEXT))
                .thenReturn(List.of(new CoiDeclarationView(APPLICANT, CoiDeclarationStatus.DECLARED)));
        ExpertContext ctx = new ExpertContext("REVIEW", BIZ_ID, EXPERT, APPLICANT, DEPT_CSE, DEPT_CSE);

        assertThatThrownBy(() -> guard.checkExpert(ctx))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.WF_006));
    }

    @Test
    void expertInSameDeptWithNoDeclarationIsRejectedWithWf006() {
        when(coiDeclarationQueryPort.findByBizAndExpert("REVIEW", BIZ_ID_TEXT, EXPERT_ID_TEXT))
                .thenReturn(List.of());
        ExpertContext ctx = new ExpertContext("REVIEW", BIZ_ID, EXPERT, APPLICANT, DEPT_CSE, DEPT_CSE);

        assertThatThrownBy(() -> guard.checkExpert(ctx))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.WF_006));
    }

    @Test
    void expertInSameDeptWithConfirmedDeclarationPasses() {
        when(coiDeclarationQueryPort.findByBizAndExpert("REVIEW", BIZ_ID_TEXT, EXPERT_ID_TEXT))
                .thenReturn(List.of(new CoiDeclarationView(APPLICANT, CoiDeclarationStatus.CONFIRMED)));
        ExpertContext ctx = new ExpertContext("REVIEW", BIZ_ID, EXPERT, APPLICANT, DEPT_CSE, DEPT_CSE);

        assertThatCode(() -> guard.checkExpert(ctx)).doesNotThrowAnyException();
    }

    @Test
    void expertInDifferentDeptPassesWithoutDeclarationLookup() {
        ExpertContext ctx = new ExpertContext("REVIEW", BIZ_ID, EXPERT, APPLICANT, DEPT_CSE, DEPT_MATH);

        assertThatCode(() -> guard.checkExpert(ctx)).doesNotThrowAnyException();
        verifyNoInteractions(coiDeclarationQueryPort);
    }
}
