package cn.edu.rpm.output.application.dto;

import java.time.LocalDate;

/**
 * 全院成果库检索行（T-OUT-014；TC-OSR-001～006）。
 *
 * <p>仅元数据，不含证据附件与直链（09 §5.2）。</p>
 *
 * @param outputId    成果 ID
 * @param outputType  成果类型
 * @param title       标题
 * @param doi         DOI，可空
 * @param patentNo    专利号，可空
 * @param completedAt 完成日期
 * @param deptId      所属部门 ID
 */
public record OutputLibraryItemVO(
        String outputId,
        String outputType,
        String title,
        String doi,
        String patentNo,
        LocalDate completedAt,
        String deptId
) {
}
