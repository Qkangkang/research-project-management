package cn.edu.rpm.workflow.application.registry;

import cn.edu.rpm.workflow.domain.port.ApprovalResultHandler;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批结果处理器注册器（T-WF-011；技术文档 03 §3.2）。
 *
 * <p>启动时收集全部 {@link ApprovalResultHandler} 实现并按 {@code bizType} 建索引：</p>
 * <ul>
 *   <li>同一 {@code bizType} 注册多个处理器属于配置错误，启动即失败（fail-fast，
 *       避免运行期静默覆盖）；</li>
 *   <li>{@code bizType} 为空同样是配置错误；</li>
 *   <li>查询未注册类型抛 {@link IllegalArgumentException}（T-WF-011 验收口径）。</li>
 * </ul>
 */
@Component
public class ApprovalResultHandlerRegistry {

    private final Map<String, ApprovalResultHandler> handlers;

    /**
     * 构造注册器（Spring 注入全部处理器实现）。
     *
     * @param handlerList 审批结果处理器列表；可为空列表（尚无业务模块注册）
     * @throws IllegalStateException {@code bizType} 为空或重复注册
     */
    public ApprovalResultHandlerRegistry(List<ApprovalResultHandler> handlerList) {
        Map<String, ApprovalResultHandler> index = new LinkedHashMap<>();
        for (ApprovalResultHandler handler : handlerList) {
            String bizType = handler.bizType();
            if (bizType == null || bizType.isBlank()) {
                throw new IllegalStateException("审批结果处理器 bizType 不能为空: "
                        + handler.getClass().getName());
            }
            if (index.putIfAbsent(bizType, handler) != null) {
                throw new IllegalStateException("审批结果处理器重复注册 bizType=" + bizType);
            }
        }
        this.handlers = Map.copyOf(index);
    }

    /**
     * 按业务类型取得唯一处理器。
     *
     * @param bizType 业务类型（与 {@code wf_instance.biz_type} 一致）
     * @return 对应处理器
     * @throws IllegalArgumentException 未注册该业务类型
     */
    public ApprovalResultHandler get(String bizType) {
        ApprovalResultHandler handler = handlers.get(bizType);
        if (handler == null) {
            throw new IllegalArgumentException("未注册审批结果处理器: " + bizType);
        }
        return handler;
    }

    /**
     * 判断业务类型是否已注册处理器。
     *
     * @param bizType 业务类型
     * @return 已注册返回 {@code true}
     */
    public boolean contains(String bizType) {
        return handlers.containsKey(bizType);
    }
}
