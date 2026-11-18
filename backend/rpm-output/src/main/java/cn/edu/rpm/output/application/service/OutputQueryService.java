package cn.edu.rpm.output.application.service;

import cn.edu.rpm.common.api.PageVO;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.output.application.dto.DuplicateCandidateVO;
import cn.edu.rpm.output.application.dto.LibrarySearchQuery;
import cn.edu.rpm.output.application.dto.OutputDetailVO;
import cn.edu.rpm.output.application.dto.OutputLibraryDetailVO;
import cn.edu.rpm.output.application.dto.OutputLibraryItemVO;
import cn.edu.rpm.output.application.dto.OutputSearchQuery;
import cn.edu.rpm.output.application.dto.OutputStatItem;
import cn.edu.rpm.output.application.dto.OutputStatsQuery;
import cn.edu.rpm.output.application.dto.OutputSummaryVO;
import cn.edu.rpm.output.domain.model.OutputPermissions;
import cn.edu.rpm.output.domain.model.OutputResourceTypes;
import cn.edu.rpm.output.domain.model.ProjectAccessInfo;
import cn.edu.rpm.output.domain.port.OutputProjectAccessPort;
import cn.edu.rpm.output.domain.service.OutputDuplicateService;
import cn.edu.rpm.output.infrastructure.entity.OutputDuplicateCandidateEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputEvidenceEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputProjectRelationEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputReviewEntity;
import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputLibraryRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputSearchRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputStatRow;
import cn.edu.rpm.output.infrastructure.repository.OutputEvidenceRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputPage;
import cn.edu.rpm.output.infrastructure.repository.OutputProjectRelationRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputQueryRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputReviewRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputVersionRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 成果查询与全院检索应用服务（T-OUT-014；技术文档 09 §5、07 §5.1）。
 *
 * <p>可见性口径：</p>
 * <ul>
 *   <li>成果详情：成果所有者/部门/科研管理等数据范围内可见；无权直接访问他人未公开成果
 *       返回 {@code RPM-COMMON-403}（TC-OUT-014）；</li>
 *   <li>「我的成果 + 已公开成果」检索：SQL 固定 {@code owner_id = 当前用户 OR
 *       status = 'APPROVED'}，他人未公开成果不进入结果；</li>
 *   <li>全院成果库：仅 {@code APPROVED}，详情只返回元数据（TC-OSR-001～003）；</li>
 *   <li>统计按成果 ID 去重，不含 DRAFT/SUBMITTED 等未公开状态（TC-OSR-005）。</li>
 * </ul>
 *
 * <p>{@code size > 100} 统一按 {@code RPM-RPT-001}（条件过宽）拒绝（TC-OSR-004）；
 * 查询不在循环中访问数据库（RULE-DB2），审核意见按版本集合批量查询。</p>
 */
@Service
public class OutputQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(OutputQueryService.class);

    /** 默认每页条数。 */
    static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限（09 §5.2）。 */
    static final int MAX_PAGE_SIZE = 100;

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final ResearchOutputRepository researchOutputRepository;

    private final OutputVersionRepository outputVersionRepository;

    private final OutputEvidenceRepository outputEvidenceRepository;

    private final OutputReviewRepository outputReviewRepository;

    private final OutputProjectRelationRepository relationRepository;

    private final OutputQueryRepository outputQueryRepository;

    private final OutputDuplicateService duplicateService;

    private final OutputProjectAccessPort projectAccessPort;

    private final AuthorizationService authorizationService;

    private final ObjectMapper objectMapper;

    /**
     * 构造查询服务。
     *
     * @param researchOutputRepository 成果仓储
     * @param outputVersionRepository  成果版本仓储
     * @param outputEvidenceRepository 成果证据仓储
     * @param outputReviewRepository   成果审核仓储
     * @param relationRepository       成果-项目关联仓储
     * @param outputQueryRepository    检索统计仓储
     * @param duplicateService         查重领域服务（读取待确认候选）
     * @param projectAccessPort        项目访问信息端口
     * @param authorizationService     统一授权服务（详情数据范围与字段脱敏）
     * @param objectMapper             元数据 JSON 解析器
     */
    public OutputQueryService(ResearchOutputRepository researchOutputRepository,
                              OutputVersionRepository outputVersionRepository,
                              OutputEvidenceRepository outputEvidenceRepository,
                              OutputReviewRepository outputReviewRepository,
                              OutputProjectRelationRepository relationRepository,
                              OutputQueryRepository outputQueryRepository,
                              OutputDuplicateService duplicateService,
                              OutputProjectAccessPort projectAccessPort,
                              AuthorizationService authorizationService,
                              ObjectMapper objectMapper) {
        this.researchOutputRepository = Objects.requireNonNull(researchOutputRepository,
                "researchOutputRepository 不能为空");
        this.outputVersionRepository = Objects.requireNonNull(outputVersionRepository,
                "outputVersionRepository 不能为空");
        this.outputEvidenceRepository = Objects.requireNonNull(outputEvidenceRepository,
                "outputEvidenceRepository 不能为空");
        this.outputReviewRepository = Objects.requireNonNull(outputReviewRepository,
                "outputReviewRepository 不能为空");
        this.relationRepository = Objects.requireNonNull(relationRepository,
                "relationRepository 不能为空");
        this.outputQueryRepository = Objects.requireNonNull(outputQueryRepository,
                "outputQueryRepository 不能为空");
        this.duplicateService = Objects.requireNonNull(duplicateService,
                "duplicateService 不能为空");
        this.projectAccessPort = Objects.requireNonNull(projectAccessPort,
                "projectAccessPort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    /**
     * 成果详情（含证据、历史审核意见、项目关联；不含下载地址）。
     *
     * <p>触发场景：成果详情页。失败语义：成果不存在 {@code RPM-COMMON-404}；
     * 数据范围内不可见 {@code RPM-COMMON-403}。</p>
     *
     * @param outputIdText 成果 ID
     * @param auth         当前请求授权上下文
     * @return 详情视图
     */
    @Transactional(readOnly = true)
    public OutputDetailVO detail(String outputIdText, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        String outputId = UuidUtils.formatCompact(parseUuid(outputIdText));
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        authorizeView(auth, output);

        List<OutputVersionEntity> versions = outputVersionRepository.findByOutputId(outputId);
        OutputVersionEntity current = resolveCurrentVersion(output, versions);
        List<OutputDetailVO.EvidenceVO> evidence = current == null
                ? List.of()
                : outputEvidenceRepository.findByOutputVersionId(current.getId()).stream()
                        .map(OutputQueryService::toEvidenceVo)
                        .toList();
        List<String> versionIds = versions.stream().map(OutputVersionEntity::getId).toList();
        List<OutputDetailVO.ReviewVO> reviews = outputReviewRepository
                .findByOutputVersionIds(versionIds).stream()
                .map(OutputQueryService::toReviewVo)
                .toList();
        List<OutputDetailVO.RelationVO> relations = relationRepository.findByOutputId(outputId).stream()
                .map(OutputQueryService::toRelationVo)
                .toList();
        long relationCount = relationRepository.countByOutputIdAndStatus(
                outputId, OutputProjectRelationEntity.STATUS_ACTIVE);

        OutputDetailVO vo = new OutputDetailVO(
                output.getId(),
                output.getOutputType(),
                output.getTitle(),
                output.getDoi(),
                output.getPatentNo(),
                output.getOwnerId(),
                output.getDeptId(),
                output.getCompletedAt(),
                output.getStatus(),
                output.getCurrentVersionId(),
                current == null ? null : current.getVersionNo(),
                current == null ? null : current.getContentHash(),
                current == null ? Map.of() : parseMetadata(current.getMetadata()),
                evidence,
                reviews,
                relations,
                relationCount);
        // 字段脱敏：成果 DTO 当前无敏感字段策略，统一经授权服务施加（保持入口一致）
        return authorizationService.mask(vo, auth, resource(output));
    }

    /**
     * 「我的成果 + 已公开成果」检索。
     *
     * <p>触发场景：成果列表页。失败语义：无查询权限 {@code RPM-COMMON-403}；
     * 分页参数非法 {@code RPM-COMMON-400}；{@code size > 100} {@code RPM-RPT-001}。</p>
     *
     * @param query 检索条件
     * @param auth  当前请求授权上下文
     * @return 分页结果（无匹配为空列表，非错误）
     */
    @Transactional(readOnly = true)
    public PageVO<OutputSummaryVO> search(OutputSearchQuery query, AuthContext auth) {
        Objects.requireNonNull(query, "query 不能为空");
        Objects.requireNonNull(auth, "auth 不能为空");
        if (!auth.hasPermission(OutputPermissions.VIEW)
                && !auth.hasPermission(OutputPermissions.LIBRARY_VIEW)) {
            throw new BizException(ErrorCode.COMMON_403);
        }
        int page = page(query.page());
        int size = size(query.size());
        OutputPage<OutputSearchRow> result = outputQueryRepository.searchVisible(
                UuidUtils.formatCompact(auth.userId()),
                query.keyword(), query.outputType(), query.status(), query.deptId(),
                query.year(), page, size);
        List<OutputSummaryVO> items = result.rows().stream()
                .map(OutputQueryService::toSummaryVo)
                .toList();
        return PageVO.of(items, page, size, result.total());
    }

    /**
     * 全院成果库检索（仅已审核成果）。
     *
     * <p>触发场景：全院成果库。失败语义：无 {@code output:library:view}
     * {@code RPM-COMMON-403}；分页非法 {@code RPM-COMMON-400}；{@code size > 100}
     * {@code RPM-RPT-001}。</p>
     *
     * @param query 检索条件
     * @param auth  当前请求授权上下文
     * @return 分页元数据行（无匹配为空列表）
     */
    @Transactional(readOnly = true)
    public PageVO<OutputLibraryItemVO> librarySearch(LibrarySearchQuery query, AuthContext auth) {
        Objects.requireNonNull(query, "query 不能为空");
        requireLibraryPermission(auth);
        int page = page(query.page());
        int size = size(query.size());
        OutputPage<OutputLibraryRow> result = outputQueryRepository.searchLibrary(
                query.keyword(), query.outputType(), query.deptId(), query.year(), page, size);
        List<OutputLibraryItemVO> items = result.rows().stream()
                .map(OutputQueryService::toLibraryItemVo)
                .toList();
        return PageVO.of(items, page, size, result.total());
    }

    /**
     * 全院成果库详情（仅元数据，不含证据附件与直链）。
     *
     * <p>触发场景：在成果库点开某条成果。未审核/已撤回成果按不存在处理
     * （{@code RPM-COMMON-404}），不泄露未公开成果存在性（TC-OSR-002/003）。</p>
     *
     * @param outputIdText 成果 ID
     * @param auth         当前请求授权上下文
     * @return 元数据详情
     */
    @Transactional(readOnly = true)
    public OutputLibraryDetailVO libraryDetail(String outputIdText, AuthContext auth) {
        requireLibraryPermission(auth);
        String outputId = UuidUtils.formatCompact(parseUuid(outputIdText));
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .filter(ResearchOutputEntity::isPublic)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        long relationCount = relationRepository.countByOutputIdAndStatus(
                outputId, OutputProjectRelationEntity.STATUS_ACTIVE);
        return new OutputLibraryDetailVO(
                output.getId(),
                output.getOutputType(),
                output.getTitle(),
                output.getDoi(),
                output.getPatentNo(),
                output.getCompletedAt(),
                output.getDeptId(),
                relationCount);
    }

    /**
     * 全院成果统计（按类型/年度/部门分组，成果去重数）。
     *
     * <p>触发场景：成果库统计面板。失败语义：无 {@code output:library:view}
     * {@code RPM-COMMON-403}；分组维度非法 {@code RPM-COMMON-400}。</p>
     *
     * @param query 统计条件
     * @param auth  当前请求授权上下文
     * @return 分组统计列表
     */
    @Transactional(readOnly = true)
    public List<OutputStatItem> stats(OutputStatsQuery query, AuthContext auth) {
        Objects.requireNonNull(query, "query 不能为空");
        requireLibraryPermission(auth);
        String groupBy = query.groupBy() == null ? "" : query.groupBy().trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("TYPE", "YEAR", "DEPT").contains(groupBy)) {
            throw new BizException(ErrorCode.COMMON_400, "分组维度必须为 TYPE / YEAR / DEPT");
        }
        List<OutputStatRow> rows;
        try {
            rows = outputQueryRepository.stats(groupBy, query.deptId(), query.year());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, ex.getMessage());
        }
        return rows.stream()
                .map(row -> new OutputStatItem(row.getGroupKey(),
                        row.getOutputCount() == null ? 0L : row.getOutputCount()))
                .toList();
    }

    /**
     * 查询成果的待确认疑似重复候选（TC-OUT-013）。
     *
     * <p>触发场景：看完登记后的重复提示。不触发重新检测、不合并成果。
     * 失败语义：成果不存在 {@code RPM-COMMON-404}；无权限 {@code RPM-COMMON-403}。</p>
     *
     * @param outputIdText 成果 ID
     * @param auth         当前请求授权上下文
     * @return 候选分页（无候选为空列表、total=0）
     */
    @Transactional(readOnly = true)
    public PageVO<DuplicateCandidateVO> duplicates(String outputIdText, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        String outputId = UuidUtils.formatCompact(parseUuid(outputIdText));
        ResearchOutputEntity output = researchOutputRepository.findById(outputId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_404, "成果不存在"));
        authorizeView(auth, output);
        List<DuplicateCandidateVO> items = duplicateService.pending(outputId).stream()
                .map(candidate -> toCandidateVo(candidate,
                        duplicateService.requiresManualConfirmation(candidate)))
                .toList();
        return PageVO.of(items, 1, items.size(), items.size());
    }

    /**
     * 项目成果列表（仅有效关联且成果未撤回）。
     *
     * <p>触发场景：项目详情页成果页签。失败语义：项目不可见/无权
     * {@code RPM-COMMON-403}。</p>
     *
     * @param projectIdText 项目 ID
     * @param auth          当前请求授权上下文
     * @return 成果摘要列表
     */
    @Transactional(readOnly = true)
    public List<OutputSummaryVO> projectOutputs(String projectIdText, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        String projectId = UuidUtils.formatCompact(parseUuid(projectIdText));
        ProjectAccessInfo info = projectAccessPort.find(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMON_403, "无权访问目标项目"));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(OutputPermissions.PROJECT_VIEW),
                ResourceRef.project(info.projectId(), info.piId(), info.deptId(),
                        info.memberUserIds()));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403, "无权访问目标项目");
        }

        List<String> outputIds = relationRepository
                .findByProjectIdAndStatus(projectId, OutputProjectRelationEntity.STATUS_ACTIVE)
                .stream()
                .map(OutputProjectRelationEntity::getOutputId)
                .distinct()
                .toList();
        if (outputIds.isEmpty()) {
            return List.of();
        }
        return researchOutputRepository.findByIds(outputIds).stream()
                .filter(item -> !ResearchOutputEntity.STATUS_REVOKED.equals(item.getStatus()))
                .map(OutputQueryService::toSummaryFromEntity)
                .toList();
    }

    private OutputVersionEntity resolveCurrentVersion(ResearchOutputEntity output,
                                                      List<OutputVersionEntity> versions) {
        if (output.getCurrentVersionId() != null && !output.getCurrentVersionId().isBlank()) {
            for (OutputVersionEntity version : versions) {
                if (output.getCurrentVersionId().equals(version.getId())) {
                    return version;
                }
            }
            return outputVersionRepository.findById(output.getCurrentVersionId()).orElse(null);
        }
        return versions.isEmpty() ? null : versions.get(0);
    }

    private void authorizeView(AuthContext auth, ResearchOutputEntity output) {
        AuthDecision decision = authorizationService.check(auth, Action.of(OutputPermissions.VIEW),
                resource(output));
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private void requireLibraryPermission(AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        if (!auth.hasPermission(OutputPermissions.LIBRARY_VIEW)) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private static ResourceRef resource(ResearchOutputEntity output) {
        return new ResourceRef(OutputResourceTypes.OUTPUT,
                UuidUtils.parse(output.getId()),
                UuidUtils.parse(output.getOwnerId()),
                UuidUtils.parse(output.getDeptId()),
                Set.of(UuidUtils.parse(output.getOwnerId())));
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, METADATA_TYPE);
        } catch (JsonProcessingException ex) {
            LOG.warn("成果版本元数据 JSON 解析失败，按空对象返回: {}", ex.getMessage());
            return Map.of();
        }
    }

    private static int page(Integer page) {
        int value = page == null ? 1 : page;
        if (value < 1) {
            throw new BizException(ErrorCode.COMMON_400, "page 必须从 1 开始");
        }
        return value;
    }

    private static int size(Integer size) {
        int value = size == null ? DEFAULT_PAGE_SIZE : size;
        if (value < 1) {
            throw new BizException(ErrorCode.COMMON_400, "size 必须大于 0");
        }
        if (value > MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.RPT_001, "每页条数不能超过 " + MAX_PAGE_SIZE);
        }
        return value;
    }

    private static UUID parseUuid(String value) {
        try {
            return UuidUtils.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.COMMON_400, "id 非法");
        }
    }

    private static OutputDetailVO.EvidenceVO toEvidenceVo(OutputEvidenceEntity evidence) {
        return new OutputDetailVO.EvidenceVO(
                evidence.getFileId(), evidence.getEvidenceType(), evidence.getSha256());
    }

    private static OutputDetailVO.ReviewVO toReviewVo(OutputReviewEntity review) {
        return new OutputDetailVO.ReviewVO(
                review.getId(), review.getReviewerId(), review.getDecision(),
                review.getComment(), review.getReviewedAt());
    }

    private static OutputDetailVO.RelationVO toRelationVo(OutputProjectRelationEntity relation) {
        return new OutputDetailVO.RelationVO(
                relation.getId(), relation.getProjectId(), relation.getContributionDesc(),
                relation.getAttributionRatio(), relation.getStatus());
    }

    private static DuplicateCandidateVO toCandidateVo(OutputDuplicateCandidateEntity candidate,
                                                      boolean manualConfirmRequired) {
        return new DuplicateCandidateVO(
                candidate.getId(),
                candidate.getCandidateOutputId(),
                candidate.getMatchType(),
                candidate.getScore(),
                candidate.getStatus(),
                manualConfirmRequired);
    }

    private static OutputSummaryVO toSummaryVo(OutputSearchRow row) {
        return new OutputSummaryVO(row.getId(), row.getOutputType(), row.getTitle(), row.getDoi(),
                row.getPatentNo(), row.getStatus(), row.getOwnerId(), row.getDeptId(),
                row.getCompletedAt());
    }

    private static OutputSummaryVO toSummaryFromEntity(ResearchOutputEntity output) {
        return new OutputSummaryVO(output.getId(), output.getOutputType(), output.getTitle(),
                output.getDoi(), output.getPatentNo(), output.getStatus(), output.getOwnerId(),
                output.getDeptId(), output.getCompletedAt());
    }

    private static OutputLibraryItemVO toLibraryItemVo(OutputLibraryRow row) {
        return new OutputLibraryItemVO(row.getId(), row.getOutputType(), row.getTitle(),
                row.getDoi(), row.getPatentNo(), row.getCompletedAt(), row.getDeptId());
    }
}
