package cn.edu.rpm.application.domain.service;

import cn.edu.rpm.common.util.CanonicalJsonHasher;

import java.util.Map;

/**
 * 申报内容规范化与摘要（T-APP-014；技术文档 04 §3.2 步骤 4）。
 *
 * <p>不可变版本必须可复算：{@code form_data} 先转为键有序、无多余空白的规范 JSON，
 * 再计算 SHA-256（64 位小写十六进制）写入 {@code application_version.content_hash}。</p>
 *
 * <p>Batch-12 起规范化与摘要规则抽取到 {@link CanonicalJsonHasher}（rpm-common），
 * 项目基线快照复用同一规则；本类保留原有 API 并委托公共工具，行为不变。</p>
 */
public class ApplicationContentHasher {

    private final CanonicalJsonHasher canonicalJsonHasher = new CanonicalJsonHasher();

    /**
     * 生成规范 JSON（对象键递归排序，数组保序）。
     *
     * @param formData 表单数据
     * @return 规范 JSON 文本
     */
    public String canonicalJson(Map<String, Object> formData) {
        return canonicalJsonHasher.canonicalJson(formData);
    }

    /**
     * 计算 UTF-8 文本的 SHA-256。
     *
     * @param canonicalJson 规范 JSON 文本
     * @return 64 位小写十六进制摘要
     */
    public String sha256(String canonicalJson) {
        return canonicalJsonHasher.sha256(canonicalJson);
    }
}
