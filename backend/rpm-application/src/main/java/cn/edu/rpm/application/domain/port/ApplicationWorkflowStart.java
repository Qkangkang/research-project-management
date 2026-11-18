package cn.edu.rpm.application.domain.port;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 申报审批启动命令（T-APP-014）。
 *
 * @param applicationId 申报 ID
 * @param versionId     已固化的申报版本 ID
 * @param projectType   项目类型（审批模板路由依据）
 * @param deptId        申报部门 ID（模板路由白名单字段）；可为空
 * @param applicantId   申请人用户 ID（发起人，同时用于自审校验）
 * @param at            业务时点（选择该时点生效的模板版本）
 */
public record ApplicationWorkflowStart(
        UUID applicationId,
        UUID versionId,
        String projectType,
        UUID deptId,
        UUID applicantId,
        OffsetDateTime at
) {

    /** 校验必填字段。 */
    public ApplicationWorkflowStart {
        Objects.requireNonNull(applicationId, "applicationId 不能为空");
        Objects.requireNonNull(versionId, "versionId 不能为空");
        Objects.requireNonNull(projectType, "projectType 不能为空");
        Objects.requireNonNull(applicantId, "applicantId 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
    }
}
