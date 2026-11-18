package cn.edu.rpm.workflow.infrastructure.repository;

import cn.edu.rpm.workflow.infrastructure.entity.WfTemplateEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code wf_template} 审批模板仓储（T-WF-002）。
 *
 * <p>纯单表数据访问契约：模板编码全局唯一（uk_wf_template_code），
 * 按项目类型筛选供模板路由使用（技术文档 03 §3.3）。</p>
 */
public interface WfTemplateRepository {

    /**
     * 插入审批模板。
     *
     * <p>{@code code} 重复时由数据库唯一约束抛出冲突，由调用方转换为业务错误。</p>
     *
     * @param template 待插入的模板 PO
     */
    void insert(WfTemplateEntity template);

    /**
     * 按主键查询模板。
     *
     * @param id 模板 ID
     * @return 模板；不存在返回 {@link Optional#empty()}
     */
    Optional<WfTemplateEntity> findById(String id);

    /**
     * 按业务编码查询模板（uk_wf_template_code）。
     *
     * @param code 模板编码
     * @return 模板；不存在返回 {@link Optional#empty()}
     */
    Optional<WfTemplateEntity> findByCode(String code);

    /**
     * 按适用项目类型查询模板列表。
     *
     * @param projectType 项目类型
     * @return 该类型的模板列表；无命中返回空列表
     */
    List<WfTemplateEntity> findByProjectType(String projectType);

    /**
     * 查询全部模板（模板路由需同时考虑项目类型为空与不限类型的模板）。
     *
     * @return 模板列表；无数据返回空列表
     */
    List<WfTemplateEntity> findAll();

    /**
     * 更新模板（乐观锁：携带旧 {@code version}）。
     *
     * @param template 待更新模板（version 为读取到的旧值）
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(WfTemplateEntity template);
}
