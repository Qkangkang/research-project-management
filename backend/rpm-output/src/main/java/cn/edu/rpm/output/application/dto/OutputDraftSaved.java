package cn.edu.rpm.output.application.dto;

/**
 * 成果草稿保存/修订结果（T-OUT-008；TC-OUT-011）。
 *
 * @param outputId  成果 ID
 * @param status    成果状态（保存后为 DRAFT）
 * @param versionId 当前版本 ID
 * @param versionNo 当前版本号
 */
public record OutputDraftSaved(
        String outputId,
        String status,
        String versionId,
        int versionNo
) {
}
