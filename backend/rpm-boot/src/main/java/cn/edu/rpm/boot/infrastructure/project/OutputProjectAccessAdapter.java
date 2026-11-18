package cn.edu.rpm.boot.infrastructure.project;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.output.domain.model.ProjectAccessInfo;
import cn.edu.rpm.output.domain.port.OutputProjectAccessPort;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 成果模块项目访问适配器（T-OUT-012；技术文档 05 §4.3）。
 *
 * <p>依赖方向 {@code output ← boot → project}：成果模块只定义端口，
 * 本适配器在 rpm-boot 中组合 {@link ProjectRepository} 与
 * {@link ProjectMemberRepository}，避免成果模块跨模块访问项目表（架构规则 1）。
 * 成员按请求时点的有效记录装载（{@code valid_from/valid_to/status} 口径由仓储保证）。</p>
 */
@Component
public class OutputProjectAccessAdapter implements OutputProjectAccessPort {

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final Clock clock;

    /**
     * 构造适配器。
     *
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 项目成员仓储
     * @param clock                   时间基准（成员有效期判定）
     */
    public OutputProjectAccessAdapter(ProjectRepository projectRepository,
                                      ProjectMemberRepository projectMemberRepository,
                                      Clock clock) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.clock = clock;
    }

    @Override
    public Optional<ProjectAccessInfo> find(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        return projectRepository.findById(UuidUtils.formatCompact(UuidUtils.parse(projectId)))
                .map(this::toAccessInfo);
    }

    private ProjectAccessInfo toAccessInfo(ProjectEntity project) {
        Set<UUID> memberUserIds = projectMemberRepository
                .findActiveByProjectId(project.getId(), OffsetDateTime.now(clock)).stream()
                .map(ProjectMemberEntity::getUserId)
                .filter(userId -> userId != null && !userId.isBlank())
                .map(UuidUtils::parse)
                .collect(Collectors.toUnmodifiableSet());
        return new ProjectAccessInfo(
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                memberUserIds);
    }
}
