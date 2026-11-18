package cn.edu.rpm.project.application.dto;

/**
 * 关闭项目风险命令（T-PRJ-015；TC-EXEC-017 / TC-EXEC-018）。
 *
 * @param resolution     处置结果（必填）
 * @param evidenceFileId 处置证据文件 ID（必填，且必须为可用文件）
 */
public record CloseProjectRiskCmd(
        String resolution,
        String evidenceFileId
) {
}
