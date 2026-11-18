package cn.edu.rpm.execution.infrastructure.adapter;

import cn.edu.rpm.execution.infrastructure.repository.DeliverableRepository;
import cn.edu.rpm.project.domain.port.MilestoneEvidencePort;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 里程碑完成证据适配器（T-PRJ-014；技术文档 05 §5.1）。
 *
 * <p>{@code rpm-project} 定义 {@link MilestoneEvidencePort}，执行模块以交付物仓储实现，
 * 保持 execution → project 单向依赖。证据口径：存在
 * {@code deliverable.status = ACTIVE} 且归属同一项目与里程碑的交付物。</p>
 */
@Component
public class DeliverableMilestoneEvidenceAdapter implements MilestoneEvidencePort {

    private final DeliverableRepository deliverableRepository;

    public DeliverableMilestoneEvidenceAdapter(DeliverableRepository deliverableRepository) {
        this.deliverableRepository = Objects.requireNonNull(deliverableRepository,
                "deliverableRepository 不能为空");
    }

    @Override
    public boolean hasEvidence(String projectId, String milestoneId) {
        return deliverableRepository.existsActiveByMilestoneId(projectId, milestoneId);
    }
}
