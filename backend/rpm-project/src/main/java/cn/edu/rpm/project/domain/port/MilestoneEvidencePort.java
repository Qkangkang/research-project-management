package cn.edu.rpm.project.domain.port;

/**
 * 里程碑完成证据查询端口（T-PRJ-014；技术文档 05 §5.1、07 §3.2）。
 *
 * <p>证据实体（交付物）由 {@code rpm-execution} 持有，本端口定义在
 * {@code rpm-project}、由执行模块的适配器实现，避免 project → execution 的循环依赖
 * （模块依赖规则：execution → project 单向）。</p>
 */
public interface MilestoneEvidencePort {

    /**
     * 判断里程碑是否已上传有效证据（{@code deliverable.status = ACTIVE}）。
     *
     * @param projectId   项目 ID
     * @param milestoneId 里程碑 ID
     * @return 存在有效证据返回 {@code true}
     */
    boolean hasEvidence(String projectId, String milestoneId);
}
