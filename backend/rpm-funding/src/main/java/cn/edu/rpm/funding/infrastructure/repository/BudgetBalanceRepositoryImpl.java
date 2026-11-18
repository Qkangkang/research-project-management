package cn.edu.rpm.funding.infrastructure.repository;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.domain.model.BudgetRebuildResult;
import cn.edu.rpm.funding.domain.service.FundEventProjection;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetLineEntity;
import cn.edu.rpm.funding.infrastructure.entity.BudgetVersionEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.mapper.BudgetBalanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code budget_balance} 预算余额投影仓储实现（T-FUND-003 / T-FUND-018）。
 *
 * <p>除常规 CRUD 外，提供按科目批量查询与全量重放重建；重建与在线追加共用
 * {@link FundEventProjection} 矩阵，保证「投影可由事件全量重建」。</p>
 */
@Repository
public class BudgetBalanceRepositoryImpl implements BudgetBalanceRepository {

    private final BudgetBalanceMapper budgetBalanceMapper;

    private final BudgetVersionRepository budgetVersionRepository;

    private final BudgetLineRepository budgetLineRepository;

    private final FundEventRepository fundEventRepository;

    /**
     * 构造余额投影仓储。
     *
     * @param budgetBalanceMapper     余额 Mapper
     * @param budgetVersionRepository 预算版本仓储（定位生效版本）
     * @param budgetLineRepository    预算科目仓储（重建范围）
     * @param fundEventRepository     资金事件仓储（重放来源）
     */
    public BudgetBalanceRepositoryImpl(BudgetBalanceMapper budgetBalanceMapper,
                                       BudgetVersionRepository budgetVersionRepository,
                                       BudgetLineRepository budgetLineRepository,
                                       FundEventRepository fundEventRepository) {
        this.budgetBalanceMapper = Objects.requireNonNull(budgetBalanceMapper, "budgetBalanceMapper 不能为空");
        this.budgetVersionRepository = Objects.requireNonNull(budgetVersionRepository,
                "budgetVersionRepository 不能为空");
        this.budgetLineRepository = Objects.requireNonNull(budgetLineRepository,
                "budgetLineRepository 不能为空");
        this.fundEventRepository = Objects.requireNonNull(fundEventRepository,
                "fundEventRepository 不能为空");
    }

    @Override
    public void insert(BudgetBalanceEntity balance) {
        budgetBalanceMapper.insert(balance);
    }

    @Override
    public Optional<BudgetBalanceEntity> findByBudgetLineId(String budgetLineId) {
        return Optional.ofNullable(budgetBalanceMapper.selectOne(
                new LambdaQueryWrapper<BudgetBalanceEntity>()
                        .eq(BudgetBalanceEntity::getBudgetLineId, budgetLineId)));
    }

    @Override
    public Optional<BudgetBalanceEntity> selectForUpdate(String budgetLineId) {
        return Optional.ofNullable(budgetBalanceMapper.selectByBudgetLineIdForUpdate(budgetLineId));
    }

    @Override
    public List<BudgetBalanceEntity> findByBudgetLineIds(Collection<String> budgetLineIds) {
        if (budgetLineIds == null || budgetLineIds.isEmpty()) {
            return List.of();
        }
        return budgetBalanceMapper.selectList(new LambdaQueryWrapper<BudgetBalanceEntity>()
                .in(BudgetBalanceEntity::getBudgetLineId, budgetLineIds));
    }

    @Override
    public int update(BudgetBalanceEntity balance) {
        return budgetBalanceMapper.updateById(balance);
    }

    /**
     * 全量重放项目资金事件并重建余额投影（T-FUND-018）。
     *
     * <p>范围限定为当前生效预算版本的科目；事件按发生时间升序重放，冲正事件按
     * 被冲正原事件类型反向冲抵。存在偏差时以重放结果修正并计入结果。</p>
     *
     * @param projectId 项目 ID
     * @return 重建结果
     * @throws BizException 投影并发冲突 {@code RPM-COMMON-409}
     */
    @Override
    @Transactional
    public BudgetRebuildResult rebuild(String projectId) {
        Optional<BudgetVersionEntity> effective = budgetVersionRepository.findEffectiveByProjectId(projectId);
        if (effective.isEmpty()) {
            return new BudgetRebuildResult(projectId, 0, 0, BigDecimal.ZERO);
        }
        List<BudgetLineEntity> lines = budgetLineRepository.findByBudgetVersionId(effective.get().getId());
        if (lines.isEmpty()) {
            return new BudgetRebuildResult(projectId, 0, 0, BigDecimal.ZERO);
        }
        Map<String, BudgetBalanceEntity> balanceByLine = new HashMap<>();
        for (BudgetBalanceEntity balance : findByBudgetLineIds(lines.stream()
                .map(BudgetLineEntity::getId).toList())) {
            balanceByLine.put(balance.getBudgetLineId(), balance);
        }

        List<FundEventEntity> events = fundEventRepository.findByProjectId(projectId);
        Map<String, String> eventTypeById = new HashMap<>();
        for (FundEventEntity event : events) {
            eventTypeById.put(event.getId(), event.getEventType());
        }

        int corrected = 0;
        BigDecimal maxDifference = BigDecimal.ZERO;
        for (BudgetLineEntity line : lines) {
            BudgetBalanceEntity expected = new BudgetBalanceEntity();
            expected.setBudgetLineId(line.getId());
            for (FundEventEntity event : events) {
                if (!UuidUtils.sameId(event.getBudgetLineId(), line.getId())) {
                    continue;
                }
                String originalType = FundEventEntity.TYPE_REVERSE.equals(event.getEventType())
                        ? eventTypeById.get(event.getReversalOfEventId())
                        : null;
                FundEventProjection.apply(expected, event.getEventType(), event.getAmount(), originalType);
            }

            BudgetBalanceEntity online = balanceByLine.get(line.getId());
            BigDecimal difference = difference(expected, online);
            if (online == null) {
                BudgetBalanceEntity created = new BudgetBalanceEntity();
                created.setId(UuidUtils.formatCompact(UUID.randomUUID()));
                created.setBudgetLineId(line.getId());
                created.setApprovedAmount(expected.getApprovedAmount());
                created.setReservedAmount(expected.getReservedAmount());
                created.setApprovedUnpaidAmount(expected.getApprovedUnpaidAmount());
                created.setPaidAmount(expected.getPaidAmount());
                insert(created);
                corrected++;
                maxDifference = max(maxDifference, difference);
                continue;
            }
            if (difference.compareTo(BigDecimal.ZERO) > 0) {
                online.setApprovedAmount(expected.getApprovedAmount());
                online.setReservedAmount(expected.getReservedAmount());
                online.setApprovedUnpaidAmount(expected.getApprovedUnpaidAmount());
                online.setPaidAmount(expected.getPaidAmount());
                if (update(online) == 0) {
                    throw new BizException(ErrorCode.COMMON_409, "余额投影版本冲突，重建中止");
                }
                corrected++;
                maxDifference = max(maxDifference, difference);
            }
        }
        return new BudgetRebuildResult(projectId, lines.size(), corrected, maxDifference);
    }

    private static BigDecimal difference(BudgetBalanceEntity expected, BudgetBalanceEntity online) {
        BigDecimal approvedUnpaid = online == null ? BigDecimal.ZERO : online.getApprovedUnpaidAmount();
        BigDecimal reserved = online == null ? BigDecimal.ZERO : online.getReservedAmount();
        BigDecimal paid = online == null ? BigDecimal.ZERO : online.getPaidAmount();
        BigDecimal approved = online == null ? BigDecimal.ZERO : online.getApprovedAmount();
        return max(max(
                expected.getApprovedAmount().subtract(approved).abs(),
                expected.getReservedAmount().subtract(reserved).abs()),
                max(expected.getApprovedUnpaidAmount().subtract(approvedUnpaid).abs(),
                        expected.getPaidAmount().subtract(paid).abs()));
    }

    private static BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }
}
