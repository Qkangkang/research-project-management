package cn.edu.rpm.output.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 成果草稿保存/修订命令（T-OUT-008；技术文档 05 §4.4）。
 *
 * @param title           标题
 * @param doi             DOI，可空
 * @param patentNo        专利号，可空
 * @param completedAt     完成日期
 * @param metadata        扩展元数据
 * @param evidenceFileIds 证据文件 ID 列表（替换当前草稿/新版本证据）
 */
public record SaveOutputDraftCmd(
        String title,
        String doi,
        String patentNo,
        LocalDate completedAt,
        Map<String, Object> metadata,
        List<String> evidenceFileIds
) {
}
