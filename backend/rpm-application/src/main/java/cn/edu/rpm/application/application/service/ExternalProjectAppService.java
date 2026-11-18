package cn.edu.rpm.application.application.service;

import cn.edu.rpm.application.application.dto.ExternalRegistrationResult;
import cn.edu.rpm.application.application.dto.RegisterExternalProjectCmd;
import cn.edu.rpm.application.domain.model.ApplicationPermissions;
import cn.edu.rpm.application.domain.model.ProjectCreationCommand;
import cn.edu.rpm.application.domain.model.ProjectCreationResult;
import cn.edu.rpm.application.domain.port.ProjectCreationPort;
import cn.edu.rpm.application.infrastructure.entity.ApplicationEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import cn.edu.rpm.application.infrastructure.entity.ApplyBatchEntity;
import cn.edu.rpm.application.infrastructure.entity.ExternalProjectRegistrationEntity;
import cn.edu.rpm.application.infrastructure.repository.ApplicationRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplicationVersionRepository;
import cn.edu.rpm.application.infrastructure.repository.ApplyBatchRepository;
import cn.edu.rpm.application.infrastructure.repository.ExternalProjectRegistrationRepository;
import cn.edu.rpm.audit.domain.model.AuditEvent;
import cn.edu.rpm.audit.domain.service.AuditRecorder;
import cn.edu.rpm.common.domain.model.OutboxEvent;
import cn.edu.rpm.common.domain.port.OutboxAppendPort;
import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.CanonicalJsonHasher;
import cn.edu.rpm.common.util.TraceIds;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.infrastructure.repository.FileObjectRepository;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 外部项目登记应用服务（T-APP-020；技术文档 04 §3.5）。
 *
 * <p>外部项目凭批复/合同直接登记立项，不启动内部评审实例：</p>
 * <ol>
 *   <li>校验权限、批复号/合同号至少一个、核验人、证明材料可用（TC-APP-032/033）；</li>
 *   <li>创建 {@code EXTERNAL} 申报与 {@code APPROVED} 版本快照；</li>
 *   <li>写 {@code external_project_registration}（三个外部编号独立保存，TC-APP-042）；</li>
 *   <li>调用 {@link ProjectCreationPort} 幂等生成项目、基线与里程碑；</li>
 *   <li>写 outbox 与审计。</li>
 * </ol>
 *
 * <p>同一合同号重复登记返回 {@code RPM-COMMON-409}（TC-APP-035）；证明材料缺失或
 * 未就绪返回 {@code RPM-APPLY-005} / {@code RPM-FILE-001}。</p>
 */
@Service
public class ExternalProjectAppService {

    /** 审计动作：外部项目登记。 */
    public static final String AUDIT_REGISTERED = "EXTERNAL_PROJECT_REGISTERED";

    private static final String OBJECT_APPLICATION = "APPLICATION";

    private static final String USER_STATUS_ACTIVE = "ACTIVE";

    private final ApplicationRepository applicationRepository;

    private final ApplicationVersionRepository applicationVersionRepository;

    private final ApplyBatchRepository applyBatchRepository;

    private final ExternalProjectRegistrationRepository registrationRepository;

    private final ProjectCreationPort projectCreationPort;

    private final FileObjectRepository fileObjectRepository;

    private final NumberGenerator numberGenerator;

    private final OutboxAppendPort outboxAppendPort;

    private final AuthorizationService authorizationService;

    private final AuditRecorder auditRecorder;

    private final CanonicalJsonHasher jsonHasher;

    private final Clock clock;

    /**
     * 构造外部项目登记服务。
     *
     * @param applicationRepository       申报主档仓储
     * @param applicationVersionRepository 申报版本仓储
     * @param applyBatchRepository        申报批次仓储
     * @param registrationRepository      外部登记仓储
     * @param projectCreationPort         立项创建端口（幂等）
     * @param fileObjectRepository        文件元数据仓储
     * @param numberGenerator             申报编号生成器
     * @param outboxAppendPort            Outbox 追加端口
     * @param authorizationService        统一授权服务
     * @param auditRecorder               审计记录器
     * @param jsonHasher                  规范化 JSON/SHA-256 工具
     * @param clock                       时间基准
     */
    public ExternalProjectAppService(ApplicationRepository applicationRepository,
                                     ApplicationVersionRepository applicationVersionRepository,
                                     ApplyBatchRepository applyBatchRepository,
                                     ExternalProjectRegistrationRepository registrationRepository,
                                     ProjectCreationPort projectCreationPort,
                                     FileObjectRepository fileObjectRepository,
                                     NumberGenerator numberGenerator,
                                     OutboxAppendPort outboxAppendPort,
                                     AuthorizationService authorizationService,
                                     AuditRecorder auditRecorder,
                                     CanonicalJsonHasher jsonHasher,
                                     Clock clock) {
        this.applicationRepository = Objects.requireNonNull(applicationRepository,
                "applicationRepository 不能为空");
        this.applicationVersionRepository = Objects.requireNonNull(applicationVersionRepository,
                "applicationVersionRepository 不能为空");
        this.applyBatchRepository = Objects.requireNonNull(applyBatchRepository,
                "applyBatchRepository 不能为空");
        this.registrationRepository = Objects.requireNonNull(registrationRepository,
                "registrationRepository 不能为空");
        this.projectCreationPort = Objects.requireNonNull(projectCreationPort,
                "projectCreationPort 不能为空");
        this.fileObjectRepository = Objects.requireNonNull(fileObjectRepository,
                "fileObjectRepository 不能为空");
        this.numberGenerator = Objects.requireNonNull(numberGenerator, "numberGenerator 不能为空");
        this.outboxAppendPort = Objects.requireNonNull(outboxAppendPort, "outboxAppendPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder 不能为空");
        this.jsonHasher = Objects.requireNonNull(jsonHasher, "jsonHasher 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 外部项目登记立项（TC-APP-032 / TC-APP-033 / TC-APP-034 / TC-APP-035 / TC-APP-042）。
     *
     * @param cmd  登记命令
     * @param auth 当前请求授权上下文
     * @return 申报、版本与项目标识
     * @throws BizException 证明材料缺失 {@code RPM-APPLY-005}；文件不可用 {@code RPM-FILE-001}；
     *                      无权限 {@code RPM-COMMON-403}；批次不存在 {@code RPM-COMMON-404}；
     *                      合同号重复 {@code RPM-COMMON-409}；参数非法 {@code RPM-COMMON-400}
     */
    @Transactional
    public ExternalRegistrationResult register(RegisterExternalProjectCmd cmd, AuthContext auth) {
        Objects.requireNonNull(cmd, "cmd 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");

        String approvalNo = trimToNull(cmd.approvalNo());
        String contractNo = trimToNull(cmd.contractNo());
        if (approvalNo == null && contractNo == null) {
            throw new BizException(ErrorCode.APPLY_005, "外部项目必须提供批复号或合同号");
        }
        if (cmd.verifiedBy() == null || cmd.verifiedBy().isBlank()) {
            throw new BizException(ErrorCode.APPLY_005, "外部项目必须提供核验人");
        }
        String verifiedBy = cmd.verifiedBy().trim();
        if (cmd.evidenceFileIds().isEmpty()) {
            throw new BizException(ErrorCode.APPLY_005, "外部项目必须上传证明材料");
        }
        String title = requireText(cmd.title(), "项目名称不能为空");
        String projectType = requireText(cmd.projectType(), "项目类型不能为空");
        String deptId = requireText(cmd.deptId(), "承担部门不能为空");
        String piId = requireText(cmd.piId(), "项目负责人不能为空");
        validateDates(cmd.startDate(), cmd.endDate());

        ApplyBatchEntity batch = applyBatchRepository.findById(cmd.batchId())
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "申报批次不存在"));
        requirePermission(batch, deptId, auth);
        if (contractNo != null && registrationRepository.findByContractNo(contractNo).isPresent()) {
            throw new BizException(ErrorCode.COMMON_409, "同一合同号已登记");
        }
        requireAvailableEvidence(cmd.evidenceFileIds());

        OffsetDateTime now = OffsetDateTime.now(clock);
        String actorId = UuidUtils.formatCompact(auth.userId());
        Map<String, Object> snapshot = buildSnapshot(cmd, approvalNo, contractNo);
        String canonicalJson = jsonHasher.canonicalJson(snapshot);
        String contentHash = jsonHasher.sha256(canonicalJson);

        ApplicationEntity application = new ApplicationEntity();
        application.setId(UuidUtils.formatCompact(UUID.randomUUID()));
        application.setApplicationNo(numberGenerator.next(NumberGenerator.RULE_APPLICATION_NO));
        application.setBatchId(batch.getId());
        application.setProjectType(projectType);
        application.setApplicantId(actorId);
        application.setDeptId(deptId);
        application.setTitle(title);
        application.setSourceType(ApplicationEntity.SOURCE_EXTERNAL);
        application.setStatus(ApplicationEntity.STATUS_APPROVED);
        application.setVersion(0);
        applicationRepository.insert(application);

        ApplicationVersionEntity version = ApplicationVersionEntity.newSubmitted(
                application.getId(), 1, canonicalJson, contentHash,
                UuidUtils.formatCompact(UuidUtils.parse(verifiedBy)), now,
                "EXTERNAL:" + (contractNo != null ? contractNo : approvalNo));
        version.markApproved();
        applicationVersionRepository.insert(version);
        application.setCurrentVersionId(version.getId());
        if (applicationRepository.update(application) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "申报已被并发修改，请重试");
        }

        ExternalProjectRegistrationEntity registration = new ExternalProjectRegistrationEntity();
        registration.setApplicationId(application.getId());
        registration.setApprovalNo(approvalNo);
        registration.setContractNo(contractNo);
        registration.setFunderName(trimToNull(cmd.funderName()));
        registration.setApprovedAmount(cmd.approvedAmount());
        registration.setVerifiedBy(UuidUtils.formatCompact(UuidUtils.parse(verifiedBy)));
        registration.setVerifiedAt(now);
        registration.setEvidenceSummary(jsonHasher.canonicalJson(cmd.evidenceFileIds()));
        registrationRepository.insert(registration);

        ProjectCreationResult created = projectCreationPort.createFromVersion(
                new ProjectCreationCommand(
                        application.getId(),
                        version.getId(),
                        ProjectCreationCommand.SOURCE_EXTERNAL,
                        title,
                        projectType,
                        deptId,
                        piId,
                        cmd.startDate(),
                        cmd.endDate(),
                        trimToNull(cmd.financialProjectNo()),
                        approvalNo,
                        contractNo,
                        snapshot,
                        List.of()));

        appendOutbox(application, created);
        auditRecorder.record(new AuditEvent(
                TraceIds.currentOrUntracked(),
                auth.userId(),
                null,
                AUDIT_REGISTERED,
                OBJECT_APPLICATION,
                UuidUtils.parse(application.getId()),
                application.getVersion(),
                null,
                contentHash,
                "外部项目登记立项",
                null,
                null,
                clock.instant()));

        return new ExternalRegistrationResult(application.getId(), application.getApplicationNo(),
                version.getId(), created.projectId(), created.projectNo(), created.baselineId());
    }

    private void requireAvailableEvidence(List<String> fileIds) {
        List<UUID> ids = new ArrayList<>(fileIds.size());
        for (String fileId : fileIds) {
            ids.add(parseUuid(fileId, "evidence_file_id"));
        }
        Map<UUID, FileObject> files = new HashMap<>();
        for (FileObject file : fileObjectRepository.findByIds(ids)) {
            files.put(file.getId(), file);
        }
        for (UUID id : ids) {
            FileObject file = files.get(id);
            if (file == null) {
                throw new BizException(ErrorCode.FILE_001, "证明材料不存在或不可用");
            }
            file.requireAvailable();
        }
    }

    private static Map<String, Object> buildSnapshot(RegisterExternalProjectCmd cmd,
                                                     String approvalNo, String contractNo) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source_type", ApplicationEntity.SOURCE_EXTERNAL);
        snapshot.put("title", cmd.title().trim());
        snapshot.put("project_type", cmd.projectType().trim());
        putIfNotNull(snapshot, "funder_name", trimToNull(cmd.funderName()));
        putIfNotNull(snapshot, "approval_no", approvalNo);
        putIfNotNull(snapshot, "contract_no", contractNo);
        putIfNotNull(snapshot, "financial_project_no", trimToNull(cmd.financialProjectNo()));
        putIfNotNull(snapshot, "approved_amount", cmd.approvedAmount());
        snapshot.put("start_date", cmd.startDate().toString());
        snapshot.put("end_date", cmd.endDate().toString());
        snapshot.put("pi_id", cmd.piId().trim());
        snapshot.put("evidence_file_ids", cmd.evidenceFileIds());
        return snapshot;
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void appendOutbox(ApplicationEntity application, ProjectCreationResult created) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application_id", application.getId());
        payload.put("application_no", application.getApplicationNo());
        payload.put("version_id", application.getCurrentVersionId());
        payload.put("project_id", created.projectId());
        payload.put("project_no", created.projectNo());
        payload.put("baseline_id", created.baselineId());
        outboxAppendPort.append(new OutboxEvent(
                UUID.randomUUID(),
                OBJECT_APPLICATION,
                UuidUtils.parse(application.getId()),
                "PROJECT_CREATED",
                payload,
                TraceIds.currentOrUntracked(),
                "PROJECT_CREATED:" + application.getCurrentVersionId()));
    }

    private void requirePermission(ApplyBatchEntity batch, String deptId, AuthContext auth) {
        AuthDecision decision = authorizationService.check(auth,
                Action.of(ApplicationPermissions.EXTERNAL_REGISTER),
                new ResourceRef(ResourceTypes.PROJECT,
                        UuidUtils.parse(batch.getId()),
                        auth.userId(),
                        UuidUtils.parse(deptId),
                        Set.of()));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BizException(ErrorCode.COMMON_400, "项目开始日期与结束日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BizException(ErrorCode.COMMON_400, "项目开始日期不能晚于结束日期");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UuidUtils.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, field + " 非法");
        }
    }
}
