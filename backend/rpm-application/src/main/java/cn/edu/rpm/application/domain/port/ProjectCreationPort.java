package cn.edu.rpm.application.domain.port;

import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.application.domain.model.ProjectCreationResult;

/**
 * 立项创建端口（T-APP-016；技术文档 01 §1.3 依赖图、04 §3.4）。
 *
 * <p>由 {@code rpm-application} 定义、{@code rpm-project} 的
 * {@code ProjectCreationService} 实现，Bean 装配在 {@code rpm-boot}。
 * 依赖方向为 project → application（project 依赖 application 的端口契约），
 * 避免 application → project 的模块循环依赖。</p>
 *
 * <p><b>幂等契约</b>：{@code source_application_version_id} 为立项幂等键
 * （数据库 {@code uk_project_source_version} 兜底）。同一申报版本重复调用必须返回
 * 首次生成的项目与基线，不得产生第二个项目（TC-APP-029）。</p>
 *
 * <p><b>事务契约</b>：实现方加入调用方事务（不新开事务、不吞异常）；
 * 审批决策、审计、outbox 与立项创建必须同提交、同回滚（RULE-E2）。</p>
 */
public interface ProjectCreationPort {

    /**
     * 按已批准的申报版本幂等创建立项项目。
     *
     * @param command 立项创建命令（不可变；幂等键为
     *                {@link ProjectCreationCommand#sourceApplicationVersionId()}）
     * @return 项目 ID、项目编号与基线 ID；重复调用返回首次结果
     */
    ProjectCreationResult createFromVersion(ProjectCreationCommand command);
}
