package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.api.dto.ApplicationDetailView;
import cn.edu.rpm.application.api.dto.ApplicationListItem;
import cn.edu.rpm.application.api.dto.ApplicationOptionsView;
import cn.edu.rpm.application.application.dto.ApplicationListQuery;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.port.ApplicationWorkflowLauncher;
import cn.edu.rpm.application.domain.port.WorkflowLaunch;
import cn.edu.rpm.application.infrastructure.entity.ApplyBatchEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplyFormTemplateEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplyBatchRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplyFormTemplateRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.ProjectQuery;
import cn.edu.rpm.iam.application.dto.ProjectScope;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.iam.application.service.UserDirectoryService;
import cn.edu.rpm.organization.application.service.OrgUnitQueryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 申报查询应用服务：列表、详情与新建申报选项。
 */
@Service
public class ApplicationQueryAppService {

    private final ApplicationRepository applicationRepository;

    private final ApplicationVersionRepository versionRepository;

    private final ApplyBatchRepository batchRepository;

    private final ApplyFormTemplateRepository templateRepository;

    private final AuthorizationService authorizationService;

    private final UserDirectoryService userDirectoryService;

    private final OrgUnitQueryService orgUnitQueryService;

    private final ApplicationWorkflowLauncher workflowLauncher;

    private final ObjectMapper objectMapper;

    public ApplicationQueryAppService(ApplicationRepository applicationRepository,
                                      ApplicationVersionRepository versionRepository,
                                      ApplyBatchRepository batchRepository,
                                      ApplyFormTemplateRepository templateRepository,
                                      AuthorizationService authorizationService,
                                      UserDirectoryService userDirectoryService,
                                      OrgUnitQueryService orgUnitQueryService,
                                      ApplicationWorkflowLauncher workflowLauncher,
                                      ObjectMapper objectMapper) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository,
                "applicationRepository 不能为空");
        this.versionRepository = Objects.requireNonNull(versionRepository,
                "versionRepository 不能为空");
        this.batchRepository = Objects.requireNonNull(batchRepository, "batchRepository 不能为空");
        this.templateRepository = Objects.requireNonNull(templateRepository,
                "templateRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.userDirectoryService = Objects.requireNonNull(userDirectoryService,
                "userDirectoryService 不能为空");
        this.orgUnitQueryService = Objects.requireNonNull(orgUnitQueryService,
                "orgUnitQueryService 不能为空");
        this.workflowLauncher = Objects.requireNonNull(workflowLauncher,
                "workflowLauncher 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    /**
     * 分页查询申报。
     *
     * @param query 查询条件
     * @param auth  当前授权上下文
     * @return 申报分页
     */
    public PageVO<ApplicationListItem> list(ApplicationListQuery query, AuthContext auth) {
        if (!auth.hasPermission(ApplicationPermissions.VIEW)) {
            throw new BizException(ErrorCode.COMMON_403);
        }
        ProjectScope scope = authorizationService.resolveProjectScope(auth,
                ProjectQuery.ofAction(ApplicationPermissions.VIEW));
        String applicantId = scope.scopeAll() ? null : UuidUtils.formatCompact(auth.userId());
        Set<String> deptIds = scope.deptIds().stream().map(UuidUtils::formatCompact)
                .collect(java.util.stream.Collectors.toSet());
        ApplicationRepository.ApplicationPageResult page = applicationRepository.search(
                new ApplicationRepository.ApplicationSearchCriteria(
                        query.keyword(), query.projectType(), query.status(), query.batchId(),
                        applicantId, deptIds, scope.scopeAll(), query.page(), query.size()));
        List<ApplicationEntity> entities = page.items();
        Map<String, String> userNames = userDirectoryService.findNames(
                entities.stream().map(ApplicationEntity::getApplicantId).toList());
        Map<String, String> deptNames = orgUnitQueryService.findNames(
                entities.stream().map(ApplicationEntity::getDeptId).toList());
        Map<String, String> batchNames = new HashMap<>();
        for (ApplyBatchEntity batch : batchRepository.findOpen()) {
            batchNames.put(batch.getId(), batch.getName());
        }
        List<ApplicationListItem> items = entities.stream()
                .map(entity -> new ApplicationListItem(
                        entity.getId(),
                        entity.getApplicationNo(),
                        entity.getBatchId(),
                        batchNames.get(entity.getBatchId()),
                        entity.getProjectType(),
                        entity.getTitle(),
                        entity.getApplicantId(),
                        userNames.get(entity.getApplicantId()),
                        entity.getDeptId(),
                        deptNames.get(entity.getDeptId()),
                        entity.getStatus(),
                        entity.getCurrentVersionId(),
                        entity.getVersion(),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()))
                .toList();
        return PageVO.of(items, query.page(), query.size(), page.total());
    }

    /**
     * 查询申报详情。
     *
     * @param applicationId 申报 ID
     * @param auth          当前授权上下文
     * @return 申报详情
     */
    public ApplicationDetailView detail(String applicationId, AuthContext auth) {
        ApplicationEntity application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报不存在"));
        if (!canView(application, auth)) {
            throw new BizException(ErrorCode.COMMON_403);
        }
        Map<String, String> deptNames = orgUnitQueryService.findNames(
                List.of(application.getDeptId()));
        Map<String, String> userNames = userDirectoryService.findNames(
                List.of(application.getApplicantId()));
        String batchName = batchRepository.findById(application.getBatchId())
                .map(ApplyBatchEntity::getName).orElse(null);
        String workflowInstanceId = findWorkflowInstanceId(application);
        return new ApplicationDetailView(
                application.getId(),
                application.getApplicationNo(),
                application.getBatchId(),
                batchName,
                application.getProjectType(),
                application.getTitle(),
                application.getApplicantId(),
                userNames.get(application.getApplicantId()),
                application.getDeptId(),
                deptNames.get(application.getDeptId()),
                application.getStatus(),
                application.getCurrentVersionId(),
                application.getVersion(),
                currentFormData(application),
                List.of(),
                workflowInstanceId,
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    private String findWorkflowInstanceId(ApplicationEntity application) {
        if (application.getCurrentVersionId() == null) {
            return null;
        }
        return workflowLauncher.findLatest(
                        UuidUtils.parse(application.getId()),
                        UuidUtils.parse(application.getCurrentVersionId()))
                .map(WorkflowLaunch::instanceId)
                .map(UuidUtils::format)
                .orElse(null);
    }

    /**
     * 新建申报下拉选项。
     *
     * @return 开放批次与有效项目类型
     */
    public ApplicationOptionsView options() {
        List<ApplicationOptionsView.BatchOption> batches = batchRepository.findOpen().stream()
                .map(batch -> new ApplicationOptionsView.BatchOption(
                        batch.getId(), batch.getBatchNo(), batch.getName(), batch.getYear(),
                        batch.getStatus(), batch.getStartAt(), batch.getEndAt()))
                .toList();
        List<ApplicationOptionsView.ProjectTypeOption> projectTypes =
                templateRepository.findAllEffective().stream()
                        .map(template -> new ApplicationOptionsView.ProjectTypeOption(
                                template.getProjectType(), template.getId(),
                                template.getVersionNo(), template.getFormSchema()))
                        .toList();
        return new ApplicationOptionsView(batches, projectTypes);
    }

    private boolean canView(ApplicationEntity application, AuthContext auth) {
        if (!auth.hasPermission(ApplicationPermissions.VIEW)) {
            return false;
        }
        ProjectScope scope = authorizationService.resolveProjectScope(auth,
                ProjectQuery.ofAction(ApplicationPermissions.VIEW));
        if (scope.scopeAll()) {
            return true;
        }
        String userId = UuidUtils.formatCompact(auth.userId());
        if (userId.equals(compact(application.getApplicantId()))) {
            return true;
        }
        if (scope.deptIds().contains(parseUuid(application.getDeptId()))) {
            return true;
        }
        return scope.grantedProjectIds().contains(parseUuid(application.getId()));
    }

    private Map<String, Object> currentFormData(ApplicationEntity application) {
        Optional<ApplicationVersionEntity> version =
                versionRepository.findDraftByApplicationId(application.getId());
        if (version.isEmpty() && application.getCurrentVersionId() != null) {
            version = versionRepository.findById(application.getCurrentVersionId());
        }
        if (version.isEmpty() || version.get().getFormData() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(version.get().getFormData(),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String compact(String id) {
        return id == null ? null : id.replace("-", "");
    }

    private static UUID parseUuid(String id) {
        try {
            return UuidUtils.parse(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
