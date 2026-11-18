package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code application} 申报主档仓储（T-APP-003）。
 *
 * <p>纯单表数据访问契约，不含业务规则；供申报创建、提交（悲观锁 + 幂等）、
 * 形式审查与撤回链路使用。约定：</p>
 * <ul>
 *   <li>插入撞唯一键（uk_application_no）时抛出 Spring
 *       {@link org.springframework.dao.DuplicateKeyException}；</li>
 *   <li>{@link #selectForUpdate(String)} 只在调用方事务内持锁有效；</li>
 *   <li>更新以实体 {@code version} 旧值作乐观锁条件。</li>
 * </ul>
 */
public interface ApplicationRepository {

    /**
     * 插入申报主档（创建草稿）。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成（ASSIGN_UUID）；
     * 申报编号重复时抛 {@link org.springframework.dao.DuplicateKeyException}。</p>
     *
     * @param application 待插入的申报 PO
     */
    void insert(ApplicationEntity application);

    /**
     * 按主键查询申报主档。
     *
     * @param id 申报主档 ID
     * @return 命中的申报；不存在返回空 {@link Optional}
     */
    Optional<ApplicationEntity> findById(String id);

    /**
     * 按申报编号查询（uk_application_no 唯一）。
     *
     * @param applicationNo 申报编号
     * @return 命中的申报；不存在返回空 {@link Optional}
     */
    Optional<ApplicationEntity> findByApplicationNo(String applicationNo);

    /**
     * 按申请人与状态查询申报列表，走 idx_application_applicant_status
     * (applicant_id, status, updated_at DESC)。
     *
     * <p>按 {@code updated_at} 倒序返回，供「我的申报」列表直接使用。</p>
     *
     * @param applicantId 申请人用户 ID
     * @param status      申报状态
     * @return 命中列表；无命中返回空列表
     */
    List<ApplicationEntity> findByApplicantIdAndStatus(String applicantId, String status);

    /**
     * 分页查询申报主档，列表与「我的申报」共用。
     *
     * @param criteria 查询条件
     * @return 当前页数据与总数
     */
    ApplicationPageResult search(ApplicationSearchCriteria criteria);

    /**
     * 按主键加行级排他锁查询（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>申报提交事务的第一步：锁定主档后校验状态与 {@code expected_version}，
     * 串行化同一申报的并发提交（TC-APP-009）。必须在已开启的事务中调用，
     * 锁随事务提交/回滚释放。</p>
     *
     * @param applicationId 申报主档 ID
     * @return 加锁命中的申报；不存在返回空 {@link Optional}
     */
    Optional<ApplicationEntity> selectForUpdate(String applicationId);

    /**
     * 按乐观锁更新申报主档（实体 {@code version} 旧值作并发条件）。
     *
     * @param application 携带主键与待更新字段的申报 PO
     * @return 受影响行数；0 表示记录不存在或版本冲突
     */
    int update(ApplicationEntity application);

    /**
     * 申报列表查询条件。
     *
     * @param keyword    编号/标题关键字
     * @param projectType 项目类型
     * @param status     申报状态
     * @param batchId    批次 ID
     * @param applicantId 非全院范围时限制申请人；可为空
     * @param deptIds    非全院范围时可见部门集合
     * @param scopeAll   是否全院数据范围
     * @param page       页码，从 1 开始
     * @param size       每页条数
     */
    record ApplicationSearchCriteria(
            String keyword,
            String projectType,
            String status,
            String batchId,
            String applicantId,
            Set<String> deptIds,
            boolean scopeAll,
            int page,
            int size
    ) {

        /**
         * 规范化查询条件。
         */
        public ApplicationSearchCriteria {
            deptIds = deptIds == null ? Set.of() : Set.copyOf(deptIds);
        }
    }

    /**
     * 申报分页结果。
     *
     * @param items 当前页主档
     * @param total 总记录数
     */
    record ApplicationPageResult(List<ApplicationEntity> items, long total) {

        /**
         * 规范化列表字段。
         */
        public ApplicationPageResult {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
