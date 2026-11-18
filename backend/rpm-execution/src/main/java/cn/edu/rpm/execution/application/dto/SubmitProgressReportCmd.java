package cn.edu.rpm.execution.application.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 提交周期报告命令（T-EXEC-006；技术文档 04 §3.8）。
 *
 * @param expectedVersion    期望的报告主档乐观锁版本
 * @param content            报告内容（work_done 必填）
 * @param deliverableFileIds 交付物附件文件 ID 列表
 * @param requestId          请求幂等键
 */
public record SubmitProgressReportCmd(
        Integer expectedVersion,
        Map<String, Object> content,
        List<String> deliverableFileIds,
        UUID requestId
) {

    /** 防御性复制集合字段。 */
    public SubmitProgressReportCmd {
        content = content == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(content));
        deliverableFileIds = deliverableFileIds == null ? List.of()
                : List.copyOf(deliverableFileIds);
    }
}
