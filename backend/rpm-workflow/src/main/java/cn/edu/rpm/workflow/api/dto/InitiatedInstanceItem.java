package cn.edu.rpm.workflow.api.dto;

import cn.edu.rpm.workflow.domain.dto.InitiatedInstanceView;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * 我发起的审批实例列表项（{@code GET /api/v1/workflow/instances/initiated}）。
 *
 * @param instanceId 审批实例 ID
 * @param bizType    业务类型
 * @param bizId      业务对象 ID
 * @param status     实例状态
 * @param startedAt  发起时间
 * @param endedAt    结束时间；进行中为空
 */
public record InitiatedInstanceItem(
        @JsonProperty("instance_id") String instanceId,
        @JsonProperty("biz_type") String bizType,
        @JsonProperty("biz_id") String bizId,
        @JsonProperty("status") String status,
        @JsonProperty("started_at") OffsetDateTime startedAt,
        @JsonProperty("ended_at") OffsetDateTime endedAt
) {

    /**
     * 由实例视图构造列表项。
     *
     * @param view 实例视图
     * @return 列表项
     */
    public static InitiatedInstanceItem from(InitiatedInstanceView view) {
        return new InitiatedInstanceItem(
                view.instanceId().toString(),
                view.bizType(),
                view.bizId().toString(),
                view.status(),
                view.startedAt(),
                view.endedAt());
    }
}
