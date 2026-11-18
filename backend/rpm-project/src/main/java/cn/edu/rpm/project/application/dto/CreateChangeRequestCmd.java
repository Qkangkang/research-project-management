package cn.edu.rpm.project.application.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 发起项目变更命令（T-PRJ-019；技术文档 08 §8.1）。
 *
 * <p>{@code payload} 为按 {@code changeType} 受控反序列化的原始映射，服务层按
 * discriminated union 规则逐类型校验后再落库，禁止自由 JSON 直接入库。</p>
 *
 * @param changeType       变更类型：PERIOD / PI / MEMBER / OBJECTIVE / BUDGET / PARTNER
 * @param effectiveDate    变更生效日期
 * @param payload          类型专属载荷
 * @param reason           变更原因（≤2048 字符）
 * @param attachmentFileIds 附件文件 ID 列表
 * @param requestId        请求幂等键
 */
public record CreateChangeRequestCmd(
        String changeType,
        LocalDate effectiveDate,
        Map<String, Object> payload,
        String reason,
        List<String> attachmentFileIds,
        UUID requestId
) {

    /** 防御性复制集合字段。 */
    public CreateChangeRequestCmd {
        payload = payload == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        attachmentFileIds = attachmentFileIds == null ? List.of() : List.copyOf(attachmentFileIds);
    }
}
