package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ExternalProjectRegistrationEntity;

import java.util.Optional;

/**
 * {@code external_project_registration} 外部项目登记仓储（T-APP-007）。
 *
 * <p>纯单表数据访问契约，不含业务规则；供外部项目登记立项链路写入与回读
 * 批复/合同证据使用。约定：</p>
 * <ul>
 *   <li>插入撞唯一键（uk_external_registration_application）时抛出 Spring
 *       {@link org.springframework.dao.DuplicateKeyException}，调用方据此判定重复登记；</li>
 *   <li>更新以实体 {@code version} 旧值作乐观锁条件。</li>
 * </ul>
 */
public interface ExternalProjectRegistrationRepository {

    /**
     * 插入外部项目登记。
     *
     * @param registration 待插入的登记 PO
     */
    void insert(ExternalProjectRegistrationEntity registration);

    /**
     * 按主键查询外部项目登记。
     *
     * @param id 登记主键
     * @return 命中的登记；不存在返回空 {@link Optional}
     */
    Optional<ExternalProjectRegistrationEntity> findById(String id);

    /**
     * 按申报主档查询登记（uk_external_registration_application 唯一）。
     *
     * @param applicationId 申报主档 ID
     * @return 命中的登记；未登记返回空 {@link Optional}
     */
    Optional<ExternalProjectRegistrationEntity> findByApplicationId(String applicationId);

    /**
     * 按合同编号查询登记（重复登记校验，TC-APP-035）。
     *
     * <p>{@code contract_no} 未建唯一索引，业务层在插入前据此判重；
     * 并发重复由调用方事务与唯一键兜底。</p>
     *
     * @param contractNo 外部合同编号
     * @return 命中的登记；未登记返回空 {@link Optional}
     */
    java.util.Optional<ExternalProjectRegistrationEntity> findByContractNo(String contractNo);

    /**
     * 按乐观锁更新外部项目登记（实体 {@code version} 旧值作并发条件）。
     *
     * @param registration 携带主键与待更新字段的登记 PO
     * @return 受影响行数；0 表示记录不存在或版本冲突
     */
    int update(ExternalProjectRegistrationEntity registration);
}
