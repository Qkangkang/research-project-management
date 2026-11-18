package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ProjectRiskEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code project_risk} 项目风险仓储（T-PRJ-006）。
 */
public interface ProjectRiskRepository {

    /**
     * 插入风险。
     *
     * @param risk 待插入的风险 PO
     */
    void insert(ProjectRiskEntity risk);

    /**
     * 按主键查询风险。
     *
     * @param id 风险 ID
     * @return 命中的风险；不存在返回空
     */
    Optional<ProjectRiskEntity> findById(String id);

    /**
     * 按项目与状态查询风险。
     *
     * @param projectId 项目 ID
     * @param status    风险状态
     * @return 风险列表；无命中返回空列表
     */
    List<ProjectRiskEntity> findByProjectIdAndStatus(String projectId, String status);

    /**
     * 查询项目下全部未结风险（OPEN / PROCESSING / ESCALATED）。
     *
     * @param projectId 项目 ID
     * @return 未结风险列表；无命中返回空列表
     */
    List<ProjectRiskEntity> findOpenByProjectId(String projectId);

    /**
     * 查询项目全部风险（按创建时间倒序）。
     *
     * @param projectId 项目 ID
     * @return 风险列表；无命中返回空列表
     */
    List<ProjectRiskEntity> findByProjectId(String projectId);

    /**
     * 批量转移项目未结风险责任人（交接生效）。
     *
     * @param projectId  项目 ID
     * @param fromUserId 原责任人用户 ID
     * @param toUserId   新责任人用户 ID
     * @return 受影响行数
     */
    int transferOwner(String projectId, String fromUserId, String toUserId);

    /**
     * 按责任人与截止日期查询逾期未结风险。
     *
     * @param ownerId 责任人用户 ID
     * @param date    截止日期上界（含）
     * @return 风险列表；无命中返回空列表
     */
    List<ProjectRiskEntity> findOverdueByOwner(String ownerId, LocalDate date);

    /**
     * 查询截止日期前未结风险（全局逾期扫描，TC-EXEC-019）。
     *
     * @param date 截止日期上界（含当天）
     * @return 风险列表；无命中返回空列表
     */
    List<ProjectRiskEntity> findOpenDueBy(LocalDate date);

    /**
     * 按来源规则查询自动生成风险（幂等）。
     *
     * @param sourceRule 来源规则编码
     * @return 命中风险；不存在返回空
     */
    Optional<ProjectRiskEntity> findBySourceRule(String sourceRule);

    /**
     * 按乐观锁更新风险。
     *
     * @param risk 携带主键与旧版本的风险 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ProjectRiskEntity risk);
}
