package cn.edu.rpm.funding.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.funding.application.service.BudgetAppService;
import cn.edu.rpm.project.domain.port.BudgetChangePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 预算变更端口实现（T-FUND-017；技术文档 08 §8.2 BUDGET 行）。
 *
 * <p>批准预算变更时由 {@code rpm-project} 的变更回调调用；解析载荷中的
 * {@code budgetVersionDraftId}，激活该草稿并替代旧生效版本，同时生成
 * {@code ADJUST} 资金事件。实现位于 {@code rpm-funding}，避免项目模块反向依赖。</p>
 */
@Component
public class BudgetChangeAdapter implements BudgetChangePort {

    private final BudgetAppService budgetAppService;

    private final ObjectMapper objectMapper;

    /**
     * 构造预算变更端口。
     *
     * @param budgetAppService 预算应用服务
     * @param objectMapper     JSON 解析器
     */
    public BudgetChangeAdapter(BudgetAppService budgetAppService, ObjectMapper objectMapper) {
        this.budgetAppService = Objects.requireNonNull(budgetAppService, "budgetAppService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    /**
     * 应用预算变更：激活调整草稿并生成资金事件。
     *
     * @param command 预算变更命令
     * @return 生效后的预算版本 ID
     * @throws BizException 载荷非法 {@code RPM-COMMON-400}；版本不匹配或非草稿
     *                      {@code RPM-COMMON-422}
     */
    @Override
    public String applyBudgetChange(BudgetChangeCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        String draftId = extractDraftId(command.payloadJson());
        return budgetAppService.applyApprovedChange(command.projectId(), draftId,
                command.changeRequestId(), command.changeVersionId(), command.operatorId()).budgetVersionId();
    }

    private String extractDraftId(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "预算变更载荷不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            JsonNode draftId = root.get("budgetVersionDraftId");
            if (draftId == null || draftId.isNull() || draftId.asText().isBlank()) {
                throw new BizException(ErrorCode.COMMON_400,
                        "预算变更载荷缺少 budgetVersionDraftId");
            }
            return draftId.asText();
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.COMMON_400, "预算变更载荷不是合法 JSON");
        }
    }
}
