package cn.edu.rpm.project.domain.model;

/**
 * 项目状态迁移动作（T-PRJ-012；技术文档 01 §2.4.2 状态机）。
 *
 * <p>每个动作映射到 {@code ProjectEntity} 上的一个显式迁移方法，
 * 禁止通过 {@code setStatus()} 裸改状态。非法迁移统一抛 {@code RPM-PROJ-002}。</p>
 */
public enum ProjectTransitionAction {

    /** 暂停：{@code EXECUTING → SUSPENDED}。 */
    PAUSE,

    /** 恢复：{@code SUSPENDED → EXECUTING}。 */
    RESUME,

    /** 提交验收：{@code EXECUTING → ACCEPTING}（TC-PRJ-034）。 */
    SUBMIT_ACCEPTANCE,

    /** 验收退回整改：{@code ACCEPTING → EXECUTING}（TC-PRJ-031）。 */
    RETURN_TO_EXECUTING,

    /** 结题：{@code ACCEPTING → COMPLETED}（TC-PRJ-032）。 */
    COMPLETE,

    /** 终止：非终态 → {@code TERMINATED}（TC-PRJ-009～011、TC-PRJ-033）。 */
    TERMINATE
}
