package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code iam_user} 人员账号仓储接口（T-IAM-001）。
 *
 * <p>纯单表 CRUD 数据访问契约，不含业务规则；供 IAM 授权判定与 SSO 绑定链路使用。
 * 约定：</p>
 * <ul>
 *   <li>插入撞唯一键（uk_iam_user_employee_no / uk_iam_user_sso_subject）时抛出
 *       Spring {@link org.springframework.dao.DuplicateKeyException}，由全局异常处理器统一转换；</li>
 *   <li>更新以实体 {@code version} 旧值为乐观锁条件（RULE-DB1 乐观锁出口）。</li>
 * </ul>
 */
public interface IamUserRepository {

    /**
     * 插入人员账号。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成（ASSIGN_UUID）；
     * 工号或 SSO 标识重复时抛 {@link org.springframework.dao.DuplicateKeyException}。</p>
     *
     * @param user 待插入的人员账号 PO
     */
    void insert(IamUserEntity user);

    /**
     * 按主键查询人员账号。
     *
     * @param id 人员账号主键
     * @return 命中的人员账号；不存在返回空 {@link Optional}
     */
    Optional<IamUserEntity> findById(String id);

    /**
     * 按工号查询人员账号（uk_iam_user_employee_no 唯一）。
     *
     * @param employeeNo 工号
     * @return 命中的人员账号；不存在返回空 {@link Optional}
     */
    Optional<IamUserEntity> findByEmployeeNo(String employeeNo);

    /**
     * 按院方 SSO 唯一标识查询人员账号（uk_iam_user_sso_subject 唯一），
     * 用于 SSO 登录时的账号匹配。
     *
     * @param ssoSubject SSO 唯一标识（如 OIDC sub、LDAP uid 映射值）
     * @return 命中的人员账号；不存在返回空 {@link Optional}
     */
    Optional<IamUserEntity> findBySsoSubject(String ssoSubject);

    /**
     * 按主键批量查询人员账号（成员列表批量填充姓名与部门，RULE-DB2 禁止 N+1）。
     *
     * <p>单次 {@code IN} 查询；入参为空返回空列表；返回顺序不保证与入参一致，
     * 调用方应按 {@link IamUserEntity#getId()} 建索引。</p>
     *
     * @param ids 人员账号 ID 集合
     * @return 命中的人员账号列表；无命中返回空列表
     */
    List<IamUserEntity> findByIds(Collection<String> ids);

    /**
     * 查询生效账号（下拉选项用），按显示姓名稳定排序。
     *
     * @param limit 最大返回条数
     * @return 生效账号列表
     */
    List<IamUserEntity> findActive(int limit);

    /**
     * 按乐观锁更新人员账号（实体 {@code version} 旧值作并发条件）。
     *
     * @param user 携带主键与待更新字段的人员账号 PO
     * @return 受影响行数；0 表示记录不存在或版本冲突
     */
    int update(IamUserEntity user);
}
