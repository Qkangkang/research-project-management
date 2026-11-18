package cn.edu.rpm.application.application.dto;

import java.util.List;
import java.util.Map;

/**
 * 提交申报命令。
 *
 * @param expectedVersion 客户端读取到的申报主档版本号
 * @param comment         提交说明
 * @param formData        当前表单数据（JSON 对象）
 * @param attachments     附件引用
 * @param collaborators   协作成员（申请人本人固定为 PI）
 */
public record SubmitApplicationCmd(
        int expectedVersion,
        String comment,
        Map<String, Object> formData,
        List<AttachmentCmd> attachments,
        List<CollaboratorCmd> collaborators
) {

    /**
     * 附件引用命令。
     *
     * @param purpose 附件用途（如 ETHICS）
     * @param fileId  文件 ID
     */
    public record AttachmentCmd(String purpose, String fileId) {
    }

    /**
     * 协作成员命令。
     *
     * @param userId           用户 ID
     * @param role             成员角色
     * @param contributionDesc 贡献说明
     */
    public record CollaboratorCmd(String userId, String role, String contributionDesc) {
    }
}
