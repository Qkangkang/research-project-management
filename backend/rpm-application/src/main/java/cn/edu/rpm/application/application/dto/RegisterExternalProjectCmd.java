package cn.edu.rpm.application.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 外部项目登记命令（T-APP-020；技术文档 04 §3.5）。
 *
 * <p>批复号与合同号至少一个非空（{@code ck_external_registration_no}）；核验人与
 * 证明材料必填。三个外部编号（{@code approval_no / contract_no / financial_project_no}）
 * 独立保存、互不覆盖（TC-APP-042）。</p>
 *
 * @param batchId          所属申报批次 ID（{@code application.batch_id} 非空约束）
 * @param projectType      项目类型
 * @param title            项目名称
 * @param deptId           承担部门 ID
 * @param piId             项目负责人用户 ID（外部项目登记时确定，立项必填）
 * @param funderName       资助方名称
 * @param approvalNo       外部批复文号；与合同号至少一个
 * @param contractNo       外部合同编号；与批复号至少一个
 * @param financialProjectNo 财务项目号；可空
 * @param approvedAmount   批复/合同金额；可空
 * @param startDate        项目开始日期
 * @param endDate          项目结束日期
 * @param verifiedBy       核验人用户 ID（须有科研管理权限）
 * @param evidenceFileIds  证明材料文件 ID（至少一份且可用）
 * @param requestId        请求幂等键
 */
public record RegisterExternalProjectCmd(
        String batchId,
        String projectType,
        String title,
        String deptId,
        String piId,
        String funderName,
        String approvalNo,
        String contractNo,
        String financialProjectNo,
        BigDecimal approvedAmount,
        LocalDate startDate,
        LocalDate endDate,
        String verifiedBy,
        List<String> evidenceFileIds,
        UUID requestId
) {

    /** 规范化证据文件集合。 */
    public RegisterExternalProjectCmd {
        evidenceFileIds = evidenceFileIds == null ? List.of() : List.copyOf(evidenceFileIds);
    }
}
