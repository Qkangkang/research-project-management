package cn.edu.rpm.output.application.dto;

import java.time.LocalDate;

/**
 * 全院成果库详情（T-OUT-014；技术文档 09 §5.2）。
 *
 * <p>仅元数据（标题、类型、DOI/专利号、完成时间、关联项目数）；
 * 证据附件不在详情返回，下载走成果原有字段权限 + 文件鉴权。</p>
 *
 * @param outputId      成果 ID
 * @param outputType    成果类型
 * @param title         标题
 * @param doi           DOI，可空
 * @param patentNo      专利号，可空
 * @param completedAt   完成日期
 * @param deptId        所属部门 ID
 * @param relationCount 有效关联项目数
 */
public record OutputLibraryDetailVO(
        String outputId,
        String outputType,
        String title,
        String doi,
        String patentNo,
        LocalDate completedAt,
        String deptId,
        long relationCount
) {
}
