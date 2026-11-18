package cn.edu.rpm.project.application.service;

import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.application.domain.model.ProjectCreationResult;
import cn.edu.rpm.application.domain.port.ProjectCreationPort;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.project.domain.service.MilestoneService;
import cn.edu.rpm.project.domain.service.ProjectNumberGenerator;
import cn.edu.rpm.project.infrastructure.entity.ProjectBaselineEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectBaselineRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 立项创建服务（T-PRJ-018；实现 {@link ProjectCreationPort}；技术文档 04 §3.4）。
 *
 * <p>一次批准生成：{@code project}（{@code PENDING_START}）→ 1 条初始
 * {@code project_baseline}（{@code EFFECTIVE}，快照哈希稳定）→ 基线内里程碑
 * （TC-APP-028）。幂等键 {@code source_application_version_id}：同一版本重复调用
 * 返回首次生成的 {@code project_id / project_no / baseline_id}，不再产生第二个项目
 * （TC-APP-029）；数据库 {@code uk_project_source_version} 兜底并发。</p>
 *
 * <p>本方法加入调用方事务（审批决策、审计、outbox 与立项同提交同回滚）。</p>
 */
@Service
public class ProjectCreationService implements ProjectCreationPort {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectCreationService.class);

    private final ProjectRepository projectRepository;

    private final ProjectBaselineRepository projectBaselineRepository;

    private final ProjectNumberGenerator projectNumberGenerator;

    private final MilestoneService milestoneService;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    /**
     * 构建立项创建服务。
     *
     * @param projectRepository         项目仓储
     * @param projectBaselineRepository 基线仓储
     * @param projectNumberGenerator    项目编号生成器
     * @param milestoneService          里程碑服务
     * @param jsonHasher                规范化 JSON/SHA-256 工具
     * @param clock                     时间基准
     */
    public ProjectCreationService(ProjectRepository projectRepository,
                                  ProjectBaselineRepository projectBaselineRepository,
                                  ProjectNumberGenerator projectNumberGenerator,
                                  MilestoneService milestoneService,
                                  CanonicalJsonHasher jsonHasher,
                                  Clock clock) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectBaselineRepository = Objects.requireNonNull(projectBaselineRepository,
                "projectBaselineRepository 不能为空");
        this.projectNumberGenerator = Objects.requireNonNull(projectNumberGenerator,
                "projectNumberGenerator 不能为空");
        this.milestoneService = Objects.requireNonNull(milestoneService, "milestoneService 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 按已批准申报版本幂等创建立项。
     *
     * @param command 立项创建命令
     * @return 项目/基线标识；重复调用返回首次结果
     * @throws BizException 参数非法 {@code RPM-COMMON-400}；编号生成失败
     *                      {@code RPM-PROJ-003}；并发冲突 {@code RPM-COMMON-409}
     */
    @Override
    @Transactional
    public ProjectCreationResult createFromVersion(ProjectCreationCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        String sourceVersionId = requireText(command.sourceApplicationVersionId(),
                "source_application_version_id 不能为空");

        Optional<ProjectEntity> existing = projectRepository
                .findBySourceApplicationVersionId(sourceVersionId);
        if (existing.isPresent()) {
            return toExistingResult(existing.get());
        }

        String sourceType = command.sourceType() == null || command.sourceType().isBlank()
                ? ProjectCreationCommand.SOURCE_INTERNAL : command.sourceType().trim();
        if (!ProjectCreationCommand.SOURCE_INTERNAL.equals(sourceType)
                && !ProjectCreationCommand.SOURCE_EXTERNAL.equals(sourceType)) {
            throw new BizException(ErrorCode.COMMON_400, "来源类型非法：" + sourceType);
        }
        String title = requireText(command.title(), "项目名称不能为空");
        String projectType = requireText(command.projectType(), "项目类型不能为空");
        String deptId = requireText(command.deptId(), "承担部门不能为空");
        String piId = requireText(command.piId(), "项目负责人不能为空");
        validateDates(command.approvedStartDate(), command.approvedEndDate());

        OffsetDateTime now = clock.instant().atZone(clock.getZone()).toOffsetDateTime();
        String projectNo = projectNumberGenerator.next();

        ProjectEntity project = new ProjectEntity();
        project.setProjectNo(projectNo);
        project.setTitle(title);
        project.setProjectType(projectType);
        project.setDeptId(deptId);
        project.setPiId(piId);
        project.setStatus(ProjectEntity.STATUS_PENDING_START);
        project.setSourceType(sourceType);
        project.setSourceApplicationVersionId(sourceVersionId);
        project.setApprovedAt(now);
        project.setStartDate(command.approvedStartDate());
        project.setEndDate(command.approvedEndDate());
        project.setFinancialProjectNo(command.financialProjectNo());
        project.setExternalApprovalNo(command.externalApprovalNo());
        project.setExternalContractNo(command.externalContractNo());

        try {
            projectRepository.insert(project);
        } catch (DuplicateKeyException ex) {
            LOG.warn("立项创建并发冲突 sourceVersionId={}", sourceVersionId);
            Optional<ProjectEntity> winner = projectRepository
                    .findBySourceApplicationVersionId(sourceVersionId);
            if (winner.isPresent()) {
                return toExistingResult(winner.get());
            }
            throw new BizException(ErrorCode.PROJ_003, "项目编号冲突，请重试");
        }

        String snapshotJson = jsonHasher.canonicalJson(command.snapshot());
        String snapshotHash = jsonHasher.sha256(snapshotJson);

        ProjectBaselineEntity baseline = new ProjectBaselineEntity();
        baseline.setProjectId(project.getId());
        baseline.setVersionNo(1);
        baseline.setSnapshot(snapshotJson);
        baseline.setSnapshotHash(snapshotHash);
        baseline.setEffectiveAt(now);
        baseline.setStatus(ProjectBaselineEntity.STATUS_EFFECTIVE);
        projectBaselineRepository.insert(baseline);

        project.switchBaseline(baseline.getId());
        int updated = projectRepository.update(project);
        if (updated == 0) {
            throw new BizException(ErrorCode.COMMON_409, "项目已被并发修改，请刷新后重试");
        }

        milestoneService.createFromBaseline(project.getId(), baseline.getId(), command.milestones());
        return new ProjectCreationResult(project.getId(), project.getProjectNo(), baseline.getId());
    }

    private ProjectCreationResult toExistingResult(ProjectEntity project) {
        String baselineId = project.getCurrentBaselineId();
        if (baselineId == null) {
            baselineId = projectBaselineRepository.findEffectiveByProjectId(project.getId())
                    .map(ProjectBaselineEntity::getId)
                    .orElse(null);
        }
        return new ProjectCreationResult(project.getId(), project.getProjectNo(), baselineId);
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BizException(ErrorCode.COMMON_400, "批准开始日期与结束日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BizException(ErrorCode.COMMON_400, "批准开始日期不能晚于结束日期");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return value.trim();
    }
}
