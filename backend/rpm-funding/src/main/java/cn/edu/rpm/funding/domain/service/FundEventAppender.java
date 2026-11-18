package cn.edu.rpm.funding.domain.service;

import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.funding.domain.model.FundEventCommand;
import cn.edu.rpm.funding.infrastructure.entity.BudgetBalanceEntity;
import cn.edu.rpm.funding.infrastructure.entity.FundEventEntity;
import cn.edu.rpm.funding.infrastructure.repository.BudgetBalanceRepository;
import cn.edu.rpm.funding.infrastructure.repository.FundEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 资金事件追加器（T-FUND-016；技术文档 05 §1.2、08 §10）。
 *
 * <p>一次追加完成三件事：按幂等键查原事件、锁定 {@code budget_balance} 行并按矩阵更新投影、
 * 追加不可变 {@code fund_event}。三项在同一本地事务内；重复幂等键返回原事件，
 * 载荷不一致抛 {@code RPM-FUND-003}。并发唯一冲突时回查原事件并走同一校验。</p>
 *
 * <p>{@code ADJUST} 金额允许带符号（正增负减），事件落库保存绝对值；
 * {@code REVERSE} 必须指向原事件，按原事件类型反向冲抵。</p>
 */
@Service
public class FundEventAppender {

    /** 资金事件编号规则编码（{@code number_rule.rule_code}）。 */
    public static final String RULE_FUND_EVENT_NO = "FUND_EVENT_NO";

    private final FundEventRepository fundEventRepository;

    private final BudgetBalanceRepository budgetBalanceRepository;

    private final NumberGenerator numberGenerator;

    private final Clock clock;

    /**
     * 构造资金事件追加器。
     *
     * @param fundEventRepository     资金事件仓储
     * @param budgetBalanceRepository 预算余额仓储
     * @param numberGenerator         业务编号生成器
     * @param clock                   时间基准
     */
    public FundEventAppender(FundEventRepository fundEventRepository,
                             BudgetBalanceRepository budgetBalanceRepository,
                             NumberGenerator numberGenerator,
                             Clock clock) {
        this.fundEventRepository = Objects.requireNonNull(fundEventRepository,
                "fundEventRepository 不能为空");
        this.budgetBalanceRepository = Objects.requireNonNull(budgetBalanceRepository,
                "budgetBalanceRepository 不能为空");
        this.numberGenerator = Objects.requireNonNull(numberGenerator, "numberGenerator 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 追加资金事件并同事务更新余额投影（TC-FUND-033、TC-FUND-034、TC-FUND-110、TC-FUND-111）。
     *
     * @param command 追加命令
     * @return 新事件；幂等命中时返回原事件
     * @throws BizException 参数非法 {@code RPM-COMMON-400}；重复事件载荷不一致
     *                      {@code RPM-FUND-003}；科目余额行缺失 {@code RPM-FUND-002}；
     *                      投影更新版本冲突 {@code RPM-COMMON-409}
     */
    @Transactional
    public FundEventEntity append(FundEventCommand command) {
        validate(command);
        BigDecimal storedAmount = storedAmount(command);

        var existing = fundEventRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return requireConsistent(existing.get(), command, storedAmount);
        }

        FundEventEntity original = loadOriginalForReverse(command);
        BudgetBalanceEntity balance = budgetBalanceRepository.selectForUpdate(command.budgetLineId())
                .orElseThrow(() -> new BizException(ErrorCode.FUND_002, "预算科目余额不存在"));

        FundEventProjection.apply(balance, command.eventType(), command.amount(),
                original == null ? null : original.getEventType());

        FundEventEntity event = buildEvent(command, storedAmount);
        fundEventRepository.insert(event);
        if (budgetBalanceRepository.update(balance) == 0) {
            throw new BizException(ErrorCode.COMMON_409, "预算余额投影版本冲突");
        }
        return event;
    }

    private void validate(FundEventCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        String type = command.eventType();
        if (type == null || !FundEventProjection.EVENT_TYPES.contains(type.trim())) {
            throw new BizException(ErrorCode.COMMON_400, "未知资金事件类型");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "资金事件幂等键不能为空");
        }
        if (command.budgetLineId() == null || command.budgetLineId().isBlank()
                || command.budgetVersionId() == null || command.budgetVersionId().isBlank()
                || command.projectId() == null || command.projectId().isBlank()) {
            throw new BizException(ErrorCode.COMMON_400, "资金事件关联标识不能为空");
        }
        BigDecimal amount = command.amount();
        if (amount == null) {
            throw new BizException(ErrorCode.COMMON_400, "事件金额不能为空");
        }
        if (FundEventEntity.TYPE_ADJUST.equals(command.eventType())) {
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                throw new BizException(ErrorCode.COMMON_400, "ADJUST 金额不能为 0");
            }
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.COMMON_400, "事件金额必须为正数");
        }
        if (FundEventEntity.TYPE_REVERSE.equals(command.eventType())
                && (command.reversalOfEventId() == null || command.reversalOfEventId().isBlank())) {
            throw new BizException(ErrorCode.COMMON_400, "REVERSE 必须指定被冲正事件");
        }
    }

    private FundEventEntity loadOriginalForReverse(FundEventCommand command) {
        if (!FundEventEntity.TYPE_REVERSE.equals(command.eventType())) {
            return null;
        }
        FundEventEntity original = fundEventRepository.findById(command.reversalOfEventId())
                .orElseThrow(() -> new BizException(ErrorCode.FUND_003, "被冲正事件不存在"));
        if (FundEventEntity.TYPE_REVERSE.equals(original.getEventType())) {
            throw new BizException(ErrorCode.FUND_003, "冲正事件不可再次冲正");
        }
        if (original.getAmount().compareTo(command.amount()) != 0) {
            throw new BizException(ErrorCode.FUND_003, "冲正金额必须等于原事件金额");
        }
        return original;
    }

    private FundEventEntity buildEvent(FundEventCommand command, BigDecimal storedAmount) {
        FundEventEntity event = new FundEventEntity();
        event.setId(UuidUtils.formatCompact(java.util.UUID.randomUUID()));
        event.setEventNo(numberGenerator.next(RULE_FUND_EVENT_NO));
        event.setProjectId(command.projectId());
        event.setExpenseRequestId(command.expenseRequestId());
        event.setBudgetVersionId(command.budgetVersionId());
        event.setBudgetLineId(command.budgetLineId());
        event.setEventType(command.eventType());
        event.setAmount(storedAmount);
        event.setOccurredAt(OffsetDateTime.now(clock));
        event.setExternalNo(command.externalNo());
        event.setIdempotencyKey(command.idempotencyKey());
        event.setReversalOfEventId(command.reversalOfEventId());
        event.setPayload(command.payload() == null ? "{}" : command.payload());
        if (command.actorId() != null) {
            event.setCreatedBy(UuidUtils.formatCompact(command.actorId()));
        }
        return event;
    }

    private static FundEventEntity requireConsistent(FundEventEntity existing,
                                                     FundEventCommand command,
                                                     BigDecimal storedAmount) {
        if (!Objects.equals(existing.getEventType(), command.eventType())
                || existing.getAmount().compareTo(storedAmount) != 0
                || !UuidUtils.sameId(existing.getBudgetLineId(), command.budgetLineId())) {
            throw new BizException(ErrorCode.FUND_003, "重复资金事件且载荷不一致");
        }
        return existing;
    }

    private static BigDecimal storedAmount(FundEventCommand command) {
        return command.amount().abs();
    }
}
