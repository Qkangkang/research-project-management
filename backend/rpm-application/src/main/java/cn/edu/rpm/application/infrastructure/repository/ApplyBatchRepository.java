package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplyBatchEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code apply_batch} 申报批次仓储（T-APP-001）。
 *
 * <p>纯单表数据访问契约，不含业务规则；供申报创建（批次开放校验）与批次管理链路使用。
 * 约定：</p>
 * <ul>
 *   <li>插入撞唯一键（uk_apply_batch_no）时抛出 Spring
 *       {@link org.springframework.dao.DuplicateKeyException}，由全局异常处理器统一转换；</li>
 *   <li>更新以实体 {@code version} 旧值作乐观锁条件（RULE-DB1 乐观锁出口）。</li>
 * </ul>
 */
public interface ApplyBatchRepository {

    /**
     * 插入申报批次。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成（ASSIGN_UUID）；
     * 批次编号重复时抛 {@link org.springframework.dao.DuplicateKeyException}。</p>
     *
     * @param batch 待插入的批次 PO
     */
    void insert(ApplyBatchEntity batch);

    /**
     * 按主键查询申报批次。
     *
     * @param id 批次主键
     * @return 命中的批次；不存在返回空 {@link Optional}
     */
    Optional<ApplyBatchEntity> findById(String id);

    /**
     * 按批次编号查询（uk_apply_batch_no 唯一）。
     *
     * @param batchNo 批次编号
     * @return 命中的批次；不存在返回空 {@link Optional}
     */
    Optional<ApplyBatchEntity> findByBatchNo(String batchNo);

    /**
     * 按年度与状态查询批次列表，走 idx_apply_batch_year_status (year, status)。
     *
     * <p>典型用法：年度开放批次列表（{@code status = OPEN}），供申报创建时选择批次。
     * 批次的开闭由后台任务按起止时间维护，本方法以 {@code status} 为唯一开放判据。</p>
     *
     * @param year   申报年度
     * @param status 批次状态（DRAFT / OPEN / CLOSED / ARCHIVED）
     * @return 命中批次列表；无命中返回空列表
     */
    List<ApplyBatchEntity> findByYearAndStatus(int year, String status);

    /**
     * 查询全部开放批次，供新建申报下拉选择。
     *
     * @return 按年度倒序、开始时间倒序排列的开放批次
     */
    List<ApplyBatchEntity> findOpen();

    /**
     * 按乐观锁更新申报批次（实体 {@code version} 旧值作并发条件）。
     *
     * @param batch 携带主键与待更新字段的批次 PO
     * @return 受影响行数；0 表示记录不存在或版本冲突
     */
    int update(ApplyBatchEntity batch);
}
