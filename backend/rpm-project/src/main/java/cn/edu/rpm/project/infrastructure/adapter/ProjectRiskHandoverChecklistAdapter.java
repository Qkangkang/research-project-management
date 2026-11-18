package cn.edu.rpm.project.infrastructure.adapter;

import cn.edu.rpm.project.domain.port.ProjectHandoverChecklistPort;
import cn.edu.rpm.project.infrastructure.entity.ProjectRiskEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectRiskRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 交接清单的风险来源适配器（T-PRJ-020；技术文档 04 §3.10）。
 *
 * <p>把项目下未结风险（OPEN / PROCESSING / ESCALATED）纳入交接清单完整性校验。
 * 轻任务/待办来源由对应模块后续提供独立适配器，应用服务按端口列表合并。</p>
 */
@Component
public class ProjectRiskHandoverChecklistAdapter implements ProjectHandoverChecklistPort {

    /** 事项类型：风险。 */
    public static final String TYPE_RISK = "RISK";

    private final ProjectRiskRepository projectRiskRepository;

    /**
     * 构造适配器。
     *
     * @param projectRiskRepository 项目风险仓储
     */
    public ProjectRiskHandoverChecklistAdapter(ProjectRiskRepository projectRiskRepository) {
        this.projectRiskRepository = Objects.requireNonNull(projectRiskRepository,
                "projectRiskRepository 不能为空");
    }

    @Override
    public List<OpenItem> findOpenItems(String projectId) {
        return projectRiskRepository.findOpenByProjectId(projectId).stream()
                .map(ProjectRiskEntity::getId)
                .map(id -> new OpenItem(TYPE_RISK, id))
                .toList();
    }
}
