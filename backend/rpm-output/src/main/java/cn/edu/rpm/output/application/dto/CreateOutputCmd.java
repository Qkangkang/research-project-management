package cn.edu.rpm.output.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 成果登记命令（T-OUT-008；技术文档 05 §4.2）。
 *
 * @param outputType     成果类型
 * @param title          标题
 * @param doi            DOI，可空
 * @param patentNo       专利号，可空
 * @param completedAt    完成日期
 * @param ownerId        完成人用户 ID，可空（缺省为当前请求人）
 * @param deptId         所属部门 ID
 * @param metadata       扩展元数据
 * @param evidenceFileIds 证据文件 ID 列表
 */
public record CreateOutputCmd(
        String outputType,
        String title,
        String doi,
        String patentNo,
        LocalDate completedAt,
        String ownerId,
        String deptId,
        Map<String, Object> metadata,
        List<String> evidenceFileIds
) {
}
