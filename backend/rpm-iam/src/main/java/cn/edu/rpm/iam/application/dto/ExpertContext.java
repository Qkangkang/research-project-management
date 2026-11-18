package cn.edu.rpm.iam.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * 专家利益冲突校验上下文（技术文档 03 §2.6 {@code checkExpert}）。
 *
 * <p>规则：专家与申请人同部门时，必须在 {@code coi_declaration} 中存在
 * 针对该业务对象、该申请人且已处置（{@code CONFIRMED / EXEMPTED}）的声明，
 * 否则视为未处置的利益冲突，拒绝提交评审意见（TC-IAM-012，{@code RPM-WF-006}）。</p>
 *
 * @param bizType        业务对象类型，如 {@code REVIEW}
 * @param bizId          业务对象 ID
 * @param expertId       专家用户 ID
 * @param applicantId    申请人用户 ID
 * @param expertDeptId   专家所属部门组织 ID；可为空
 * @param applicantDeptId 申请人所属部门组织 ID；可为空
 */
public record ExpertContext(
        String bizType,
        UUID bizId,
        UUID expertId,
        UUID applicantId,
        UUID expertDeptId,
        UUID applicantDeptId
) {

    /** 校验业务对象、专家与申请人。 */
    public ExpertContext {
        Objects.requireNonNull(bizType, "bizType 不能为空");
        Objects.requireNonNull(bizId, "bizId 不能为空");
        Objects.requireNonNull(expertId, "expertId 不能为空");
        Objects.requireNonNull(applicantId, "applicantId 不能为空");
    }
}
