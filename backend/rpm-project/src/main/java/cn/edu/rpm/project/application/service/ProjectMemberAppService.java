package cn.edu.rpm.project.application.service;

import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.FieldPolicyCodes;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.iam.domain.service.FieldMaskService;
import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamUserRepository;
import cn.edu.rpm.project.application.dto.AddProjectMemberCmd;
import cn.edu.rpm.project.application.dto.ProjectMemberVO;
import cn.edu.rpm.project.domain.model.ProjectPermissions;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目成员应用服务（T-PRJ-013；技术文档 04 §2.1、07 §3.1）。
 *
 * <p>新增成员：校验用户存在且 {@code ACTIVE}、有效期合法、同一成员同一角色在当前
 * 时点无有效记录（重复抛 {@code RPM-PROJ-005}；有效期重叠由数据库
 * {@code exc_project_member_overlap} 兜底）。列表：只返回请求时点有效期内成员，
 * 姓名/联系方式批量填充（RULE-DB2），联系方式只返回库内脱敏值并叠加字段策略。</p>
 *
 * <p>授权：新增用 {@code project:member:manage}、列表用 {@code project:view}，
 * 均在应用服务入口通过统一授权服务判定，禁止只依赖 Controller 注解。</p>
 */
@Service
public class ProjectMemberAppService {

    /** 审计动作：新增项目成员。 */
    public static final String AUDIT_MEMBER_ADDED = "PROJECT_MEMBER_ADDED";

    /** 审计对象类型：项目。 */
    static final String OBJECT_PROJECT = "PROJECT";

    /** 人员账号状态：在职可用。 */
    private static final String USER_STATUS_ACTIVE = "ACTIVE";

    private static final int CONTRIBUTION_MAX_LENGTH = 1024;

    private static final int ROLE_MAX_LENGTH = 32;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final IamUserRepository iamUserRepository;

    private final AuthorizationService authorizationService;

    private final FieldMaskService fieldMaskService;

    private final AuditRecorder auditRecorder;

    private final Clock clock;

    /**
     * 构造成员应用服务。
     *
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 成员仓储
     * @param iamUserRepository       人员账号仓储
     * @param authorizationService    统一授权服务
     * @param fieldMaskService        字段脱敏服务
     * @param auditRecorder           审计记录器
     * @param clock                   时间基准
     */
    public ProjectMemberAppService(ProjectRepository projectRepository,
                                   ProjectMemberRepository projectMemberRepository,
                                   IamUserRepository iamUserRepository,
                                   AuthorizationService authorizationService,
                                   FieldMaskService fieldMaskService,
                                   AuditRecorder auditRecorder,
                                   Clock clock) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.iamUserRepository = Objects.requireNonNull(iamUserRepository, "iamUserRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.fieldMaskService = Objects.requireNonNull(fieldMaskService, "fieldMaskService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 新增项目成员（TC-PRJ-014 / TC-PRJ-015）。
     *
     * @param projectId 项目 ID
     * @param cmd       新增命令
     * @param auth      当前请求授权上下文
     * @return 新成员 ID
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}；
     *                      参数非法 {@code RPM-COMMON-400}；用户不可用 {@code RPM-COMMON-422}；
     *                      重复角色 {@code RPM-PROJ-005}
     */
    @Transactional
    public String add(String projectId, AddProjectMemberCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth, ProjectPermissions.MEMBER_MANAGE);

        String userId = requireText(cmd.userId(), "成员用户 ID 不能为空");
        String role = requireText(cmd.projectRole(), "项目角色不能为空");
        if (role.length() > ROLE_MAX_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400, "项目角色长度不能超过 32");
        }
        if (cmd.contributionDesc() != null && cmd.contributionDesc().length() > CONTRIBUTION_MAX_LENGTH) {
            throw new BizException(ErrorCode.COMMON_400, "贡献说明长度不能超过 1024");
        }

        OffsetDateTime now = clock.instant().atZone(clock.getZone()).toOffsetDateTime();
        OffsetDateTime validFrom = cmd.validFrom() == null ? now : cmd.validFrom();
        OffsetDateTime validTo = cmd.validTo();
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new BizException(ErrorCode.COMMON_400, "成员有效期止必须晚于有效期起");
        }

        IamUserEntity user = iamUserRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成员用户不存在"));
        if (!USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BizException(ErrorCode.COMMON_422, "成员用户状态不可用");
        }

        if (projectMemberRepository.existsActiveMember(projectId, userId, role, validFrom)) {
            throw new BizException(ErrorCode.PROJ_005, "成员在该角色下已存在有效记录");
        }

        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setProjectRole(role);
        member.setContributionDesc(cmd.contributionDesc());
        member.setValidFrom(validFrom);
        member.setValidTo(validTo);
        member.setStatus(ProjectMemberEntity.STATUS_ACTIVE);
        projectMemberRepository.insert(member);

        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_MEMBER_ADDED,
                OBJECT_PROJECT,
                UuidUtils.parse(projectId),
                null,
                null,
                null,
                "新增项目成员 userId=" + userId + " role=" + role,
                null,
                null,
                clock.instant()));
        return member.getId();
    }

    /**
     * 查询项目当前有效成员（TC-PRJ-018 / TC-PRJ-019）。
     *
     * <p>只返回请求时点生效的 {@code ACTIVE} 成员；无成员历史返回空列表（不返回 500）。</p>
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 成员视图列表（联系方式脱敏）
     */
    public List<ProjectMemberVO> list(String projectId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth, ProjectPermissions.VIEW);

        OffsetDateTime now = clock.instant().atZone(clock.getZone()).toOffsetDateTime();
        List<ProjectMemberEntity> members = projectMemberRepository.findActiveByProjectId(projectId, now);
        if (members.isEmpty()) {
            return List.of();
        }

        Map<String, IamUserEntity> users = iamUserRepository.findByIds(
                        members.stream().map(ProjectMemberEntity::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(IamUserEntity::getId, Function.identity()));

        return members.stream().map(member -> {
            IamUserEntity user = users.get(member.getUserId());
            ProjectMemberVO vo = new ProjectMemberVO();
            vo.setMemberId(member.getId());
            vo.setUserId(member.getUserId());
            vo.setDisplayName(user == null ? null : user.getDisplayName());
            vo.setProjectRole(member.getProjectRole());
            vo.setContributionDesc(member.getContributionDesc());
            vo.setValidFrom(member.getValidFrom());
            vo.setValidTo(member.getValidTo());
            vo.setStatus(member.getStatus());
            vo.setMobile(user == null ? null : user.getMobileMasked());
            return fieldMaskService.mask(vo, auth, Set.of(FieldPolicyCodes.CONTACT));
        }).toList();
    }

    private void authorize(ProjectEntity project, AuthContext auth, String permissionCode) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth, Action.of(permissionCode), resource);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private Set<UUID> activeMemberIds(String projectId) {
        OffsetDateTime now = clock.instant().atZone(clock.getZone()).toOffsetDateTime();
        return projectMemberRepository.findActiveByProjectId(projectId, now).stream()
                .map(ProjectMemberEntity::getUserId)
                .map(UuidUtils::parse)
                .collect(Collectors.toSet());
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return value.trim();
    }
}
