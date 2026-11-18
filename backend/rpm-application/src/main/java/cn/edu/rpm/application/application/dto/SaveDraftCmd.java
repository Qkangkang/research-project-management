package cn.edu.rpm.application.application.dto;

import java.util.Map;

/**
 * 保存申报草稿命令。
 *
 * @param expectedVersion 客户端读取到的申报主档版本号
 * @param title           新标题；为空表示不修改
 * @param formData        表单数据（JSON 对象）
 */
public record SaveDraftCmd(
        int expectedVersion,
        String title,
        Map<String, Object> formData
) {
}
