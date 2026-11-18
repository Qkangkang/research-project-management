package cn.edu.rpm.output.domain.model;

import java.time.LocalDate;
import java.util.Map;

/**
 * 成果登记/修订的待校验元数据（T-OUT-007）。
 *
 * <p>作为 {@code OutputTypeTemplateResolver.validate(...)} 的入参，
 * 与 API 请求解耦，便于纯单元测试直接构造。</p>
 *
 * @param outputType  成果类型
 * @param title       标题
 * @param doi         DOI，可空
 * @param patentNo    专利号，可空
 * @param completedAt 完成日期
 * @param metadata    扩展元数据
 */
public record OutputDraftData(
        String outputType,
        String title,
        String doi,
        String patentNo,
        LocalDate completedAt,
        Map<String, Object> metadata
) {

    /** 规范化元数据为不可变映射。 */
    public OutputDraftData {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
