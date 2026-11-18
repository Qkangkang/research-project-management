package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplyFormTemplateEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code apply_form_template} 项目类型表单模板仓储（T-APP-002）。
 *
 * <p>纯单表数据访问契约，不含业务规则；供申报创建（模板存在校验）与
 * 申报提交（表单校验）链路使用。约定：</p>
 * <ul>
 *   <li>插入撞唯一键（uk_apply_form_type_version）时抛出 Spring
 *       {@link org.springframework.dao.DuplicateKeyException}；</li>
 *   <li>更新以实体 {@code version} 旧值作乐观锁条件。</li>
 * </ul>
 */
public interface ApplyFormTemplateRepository {

    /**
     * 插入表单模板。
     *
     * @param template 待插入的模板 PO
     */
    void insert(ApplyFormTemplateEntity template);

    /**
     * 按主键查询表单模板。
     *
     * @param id 模板主键
     * @return 命中的模板；不存在返回空 {@link Optional}
     */
    Optional<ApplyFormTemplateEntity> findById(String id);

    /**
     * 按项目类型与版本号查询模板（uk_apply_form_type_version 唯一）。
     *
     * @param projectType 项目类型
     * @param versionNo   模板版本号
     * @return 命中的模板；不存在返回空 {@link Optional}
     */
    Optional<ApplyFormTemplateEntity> findByProjectTypeAndVersionNo(String projectType, int versionNo);

    /**
     * 查询指定项目类型当前有效的表单模板。
     *
     * <p>判定口径（与 {@code GET /apply-form-templates/{projectType}} 一致）：</p>
     * <ul>
     *   <li>{@code status = PUBLISHED}；</li>
     *   <li>生效窗口 {@code effective_from IS NULL OR effective_from <= now} 且
     *       {@code effective_to IS NULL OR effective_to > now}；</li>
     *   <li>多条命中取 {@code version_no} 最大者（最新发布版本）。</li>
     * </ul>
     *
     * @param projectType 项目类型
     * @return 当前有效模板；无已发布且生效的模板返回空 {@link Optional}
     */
    Optional<ApplyFormTemplateEntity> findEffectiveByProjectType(String projectType);

    /**
     * 查询每个项目类型当前有效的表单模板（同类型取最新版本）。
     *
     * @return 有效模板列表
     */
    List<ApplyFormTemplateEntity> findAllEffective();

    /**
     * 按乐观锁更新表单模板（实体 {@code version} 旧值作并发条件）。
     *
     * @param template 携带主键与待更新字段的模板 PO
     * @return 受影响行数；0 表示记录不存在或版本冲突
     */
    int update(ApplyFormTemplateEntity template);
}
