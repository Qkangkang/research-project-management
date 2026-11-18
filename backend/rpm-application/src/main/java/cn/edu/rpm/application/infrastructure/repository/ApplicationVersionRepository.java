package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;

import java.util.Optional;

/**
 * {@code application_version} 不可变申报版本仓储（T-APP-004）。
 *
 * <p>纯单表数据访问契约，不含业务规则；供申报提交（版本固化 + 幂等）与
 * 补正、决策链路读取版本快照使用。约定：</p>
 * <ul>
 *   <li>插入撞唯一键（uk_application_version_no / uk_application_version_idem）时抛出
 *       Spring {@link org.springframework.dao.DuplicateKeyException}，由调用方据此
 *       判定并发提交或幂等重放；</li>
 *   <li>已提交版本内容不可变：仅状态与审计列可更新，更新以实体 {@code version} 旧值作乐观锁条件。</li>
 * </ul>
 */
public interface ApplicationVersionRepository {

    /**
     * 插入申报版本（提交时固化表单快照）。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成（ASSIGN_UUID）。
     * 同一申报内版本号重复或幂等键重复时抛出
     * {@link org.springframework.dao.DuplicateKeyException}。</p>
     *
     * @param version 待插入的版本 PO
     */
    void insert(ApplicationVersionEntity version);

    /**
     * 按主键查询申报版本。
     *
     * @param id 版本 ID
     * @return 命中的版本；不存在返回空 {@link Optional}
     */
    Optional<ApplicationVersionEntity> findById(String id);

    /**
     * 按申报主档与版本号查询（uk_application_version_no 唯一）。
     *
     * @param applicationId 申报主档 ID
     * @param versionNo     版本号（从 1 起）
     * @return 命中的版本；不存在返回空 {@link Optional}
     */
    Optional<ApplicationVersionEntity> findByApplicationIdAndVersionNo(String applicationId, int versionNo);

    /**
     * 按提交幂等键查询已有版本（uk_application_version_idem 唯一）。
     *
     * <p>提交链路的幂等入口：重复 {@code Idempotency-Key} 直接返回首次版本，
     * 不再生成版本与审批实例（TC-APP-008）。</p>
     *
     * @param idempotencyKey 提交幂等键
     * @return 命中的版本；幂等键未使用过返回空 {@link Optional}
     */
    Optional<ApplicationVersionEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * 查询申报当前草稿版本（{@code status = DRAFT}，版本号最大一条）。
     *
     * <p>保存草稿时命中则更新内容，未命中则新建草稿行；提交时命中则原地固化为
     * {@code SUBMITTED}（保证首次提交 {@code version_no = 1} 而不是草稿 + 提交两行）。</p>
     *
     * @param applicationId 申报主档 ID
     * @return 草稿版本；无草稿返回空 {@link Optional}
     */
    Optional<ApplicationVersionEntity> findDraftByApplicationId(String applicationId);

    /**
     * 查询申报已有最大版本号（无版本返回 0）。
     *
     * <p>提交时 {@code version_no = max + 1}（技术文档 04 §3.2 步骤 4）。</p>
     *
     * @param applicationId 申报主档 ID
     * @return 最大版本号；无版本返回 {@code 0}
     */
    int findMaxVersionNo(String applicationId);

    /**
     * 按乐观锁更新申报版本（仅状态与审计列；实体 {@code version} 旧值作并发条件）。
     *
     * @param version 携带主键与待更新字段的版本 PO
     * @return 受影响行数；0 表示记录不存在或版本冲突
     */
    int update(ApplicationVersionEntity version);
}
