package cn.edu.rpm.execution.application.dto;

/**
 * 创建周期报告草稿命令（T-EXEC-010；技术文档 04 §2.3）。
 *
 * @param periodType 周期类型（MONTH / QUARTER）
 * @param periodKey  周期键（月报 {@code yyyy-MM}，季报 {@code yyyy-Qn}）
 */
public record CreateProgressReportCmd(String periodType, String periodKey) {
}
