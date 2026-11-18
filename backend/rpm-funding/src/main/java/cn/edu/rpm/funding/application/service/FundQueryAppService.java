package cn.edu.rpm.funding.application.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.application.dto.FundLineSummary;
import cn.edu.rpm.funding.application.dto.FundSummary;
import cn.edu.rpm.funding.domain.model.FundingPermissions;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetBalanceRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetLineRepository;
import cn.edu.rpm.funding.infrastructure.repository.BudgetVersionRepository;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.AuthDecision;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.model.ResourceTypes;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.project.infrastructure.entity.ProjectEntity;
import cn.edu.rpm.project.infrastructure.entity.ProjectMemberEntity;
import cn.edu.rpm.project.infrastructure.repository.ProjectMemberRepository;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 经费汇总查询服务（T-FUND-023；技术文档 05 §3.5、07 §4.1）。
 *
 * <p>读取项目当前生效预算版本与余额投影，按科目返回
 * {@code approved / reserved / approved_unpaid / paid / available} 五项金额及合计；
 * 项目无预算版本时返回全零与空科目列表，不抛 500。</p>
 */
@Service
public class FundQueryAppService {

    private final BudgetVersionRepository budgetVersionRepository;

    private final BudgetLineRepository budgetLineRepository;

    private final BudgetBalanceRepository budgetBalanceRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final AuthorizationService authorizationService;

    private final Clock clock;

    /**
     * 构造经费汇总查询服务。
     *
     * @param budgetVersionRepository 预算版本仓储
     * @param budgetLineRepository    预算科目仓储
     * @param budgetBalanceRepository 余额投影仓储
     * @param projectRepository       项目仓储
     * @param projectMemberRepository 项目成员仓储（授权装载）
     * @param authorizationService    统一授权服务
     * @param clock                   时间基准
     */
    public FundQueryAppService(BudgetVersionRepository budgetVersionRepository,
                               BudgetLineRepository budgetLineRepository,
                               BudgetBalanceRepository budgetBalanceRepository,
                               ProjectRepository projectRepository,
                               ProjectMemberRepository projectMemberRepository,
                               AuthorizationService authorizationService,
                               Clock clock) {
        this.budgetVersionRepository = Objects.requireNonNull(budgetVersionRepository,
                "budgetVersionRepository 不能为空");
        this.budgetLineRepository = Objects.requireNonNull(budgetLineRepository,
                "budgetLineRepository 不能为空");
        this.budgetBalanceRepository = Objects.requireNonNull(budgetBalanceRepository,
                "budgetBalanceRepository 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
        this.projectMemberRepository = Objects.requireNonNull(projectMemberRepository,
                "projectMemberRepository 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 查询项目经费汇总（TC-FUND-037、TC-FUND-038）。
     *
     * @param projectId 项目 ID
     * @param auth      当前请求授权上下文
     * @return 经费汇总（无预算时四项金额为零、{@code lines=[]}）
     * @throws BizException 项目不存在 {@code RPM-PROJ-001}；无权限 {@code RPM-COMMON-403}
     */
    @Transactional(readOnly = true)
    public FundSummary summary(String projectId, AuthContext auth) {
        Objects.requireNonNull(auth, "auth 不能为空");
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BizException(ErrorCode.PROJ_001));
        authorize(project, auth);

        Optional<BudgetVersionEntity> effective = budgetVersionRepository
                .findEffectiveByProjectId(project.getId());
        if (effective.isEmpty()) {
            return new FundSummary(OffsetDateTime.now(clock),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of());
        }
        List<BudgetLineEntity> lines = budgetLineRepository
                .findByBudgetVersionId(effective.get().getId());
        Map<String, BudgetBalanceEntity> balances = budgetBalanceRepository
                .findByBudgetLineIds(lines.stream().map(BudgetLineEntity::getId).toList()).stream()
                .collect(Collectors.toMap(BudgetBalanceEntity::getBudgetLineId, balance -> balance));

        List<FundLineSummary> summaries = lines.stream()
                .map(line -> toLine(line, balances.get(line.getId())))
                .toList();
        return new FundSummary(
                OffsetDateTime.now(clock),
                total(summaries, FundLineSummary::approvedAmount),
                total(summaries, FundLineSummary::reservedAmount),
                total(summaries, FundLineSummary::approvedUnpaidAmount),
                total(summaries, FundLineSummary::paidAmount),
                total(summaries, FundLineSummary::availableAmount),
                summaries);
    }

    private static FundLineSummary toLine(BudgetLineEntity line, BudgetBalanceEntity balance) {
        return new FundLineSummary(
                line.getId(),
                line.getSubjectCode(),
                line.getSubjectName(),
                amount(balance == null ? null : balance.getApprovedAmount()),
                amount(balance == null ? null : balance.getReservedAmount()),
                amount(balance == null ? null : balance.getApprovedUnpaidAmount()),
                amount(balance == null ? null : balance.getPaidAmount()),
                balance == null ? BigDecimal.ZERO : balance.availableAmount());
    }

    private static BigDecimal total(List<FundLineSummary> lines,
                                    java.util.function.Function<FundLineSummary, BigDecimal> getter) {
        return lines.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void authorize(ProjectEntity project, AuthContext auth) {
        ResourceRef resource = new ResourceRef(
                ResourceTypes.PROJECT,
                UuidUtils.parse(project.getId()),
                UuidUtils.parse(project.getPiId()),
                UuidUtils.parse(project.getDeptId()),
                activeMemberIds(project.getId()));
        AuthDecision decision = authorizationService.check(auth,
                Action.of(FundingPermissions.VIEW), resource);
        if (!decision.allowed()) {
            throw new BizException(ErrorCode.COMMON_403);
        }
    }

    private Set<UUID> activeMemberIds(String projectId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return projectMemberRepository.findActiveByProjectId(projectId, now).stream()
                .map(ProjectMemberEntity::getUserId)
                .map(UuidUtils::parse)
                .collect(Collectors.toSet());
    }
}
