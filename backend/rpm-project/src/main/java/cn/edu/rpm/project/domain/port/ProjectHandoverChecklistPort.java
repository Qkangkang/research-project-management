package cn.edu.rpm.project.domain.port;

import java.util.List;
import java.util.Objects;

/**
 * 交接清单完整性校验端口（T-PRJ-020；技术文档 04 §3.10）。
 *
 * <p>由各业务来源分别实现（风险、轻任务、待办等），应用服务合并全部来源后
 * 要求清单全覆盖，避免遗漏未结事项。新增来源时只需增加适配器，不改应用服务。</p>
 */
public interface ProjectHandoverChecklistPort {

    /**
     * 查询项目下全部未结事项。
     *
     * @param projectId 项目 ID
     * @return 未结事项列表；无未结事项返回空列表
     */
    List<OpenItem> findOpenItems(String projectId);

    /**
     * 未结事项。
     *
     * @param type 事项类型：TASK / RISK
     * @param id   事项 ID
     */
    record OpenItem(String type, String id) {

        /** 校验必填字段。 */
        public OpenItem {
            Objects.requireNonNull(type, "type 不能为空");
            Objects.requireNonNull(id, "id 不能为空");
        }
    }
}
