package cn.edu.rpm.output.domain.port;

import cn.edu.rpm.output.domain.model.ProjectAccessInfo;

import java.util.Optional;

/**
 * 项目访问信息端口（T-OUT-012；技术文档 05 §4.3）。
 *
 * <p>由 {@code rpm-output} 定义、{@code rpm-boot} 的项目访问适配器实现，
 * 依赖方向为 output ← boot → project：成果模块不直接依赖项目仓储或项目表，
 * 避免跨模块数据访问（架构规则 1）。查询结果用于统一授权判定。</p>
 */
public interface OutputProjectAccessPort {

    /**
     * 查询项目的数据范围信息（负责人、部门、当前有效成员）。
     *
     * @param projectId 项目 ID（32 位或标准 uuid 文本）
     * @return 命中的项目信息；不存在返回空
     */
    Optional<ProjectAccessInfo> find(String projectId);
}
