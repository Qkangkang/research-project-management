package cn.edu.rpm.project.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 发起负责人交接命令（T-PRJ-020；技术文档 04 §3.10）。
 *
 * @param toUserId    新负责人用户 ID
 * @param effectiveAt 交接生效时间
 * @param pendingItems 待转移事项清单；未结待办与风险必须全部列入
 * @param reason      交接原因
 * @param requestId   请求幂等键
 */
public record InitiateHandoverCmd(
        String toUserId,
        OffsetDateTime effectiveAt,
        List<PendingItem> pendingItems,
        String reason,
        UUID requestId
) {

    /** 防御性复制清单。 */
    public InitiateHandoverCmd {
        pendingItems = pendingItems == null ? List.of() : List.copyOf(pendingItems);
    }

    /**
     * 待转移事项。
     *
     * @param type   事项类型：TASK / RISK
     * @param id     事项 ID
     * @param action 处理动作：TRANSFER / CLOSE / CANCEL
     */
    public record PendingItem(String type, String id, String action) {

        /** 校验必填字段。 */
        public PendingItem {
            Objects.requireNonNull(type, "type 不能为空");
            Objects.requireNonNull(id, "id 不能为空");
        }
    }
}
