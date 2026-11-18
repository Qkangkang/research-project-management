package cn.edu.rpm.output.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 成果查重打分策略（T-OUT-010）。
 *
 * <p>分值属于业务配置，禁止硬编码在查重逻辑内：由 rpm-boot 从
 * {@code rpm.output.duplicate.*} 外部化配置装配后注入领域服务。
 * 低于 {@link #confirmThreshold()} 的候选仅为提示，必须人工确认，系统不自动合并（BR-21）。</p>
 *
 * @param doiScore         DOI 完全相同得分
 * @param patentScore      专利号完全相同得分
 * @param ownerScore       完成人 + 名称组合相同得分
 * @param titleScore       名称相同但完成人/DOI 不同得分（低分提示）
 * @param confirmThreshold 人工确认阈值
 */
public record DuplicateMatchPolicy(
        BigDecimal doiScore,
        BigDecimal patentScore,
        BigDecimal ownerScore,
        BigDecimal titleScore,
        BigDecimal confirmThreshold
) {

    /** 校验分值范围（0～1）与阈值。 */
    public DuplicateMatchPolicy {
        requireScore(doiScore, "doiScore");
        requireScore(patentScore, "patentScore");
        requireScore(ownerScore, "ownerScore");
        requireScore(titleScore, "titleScore");
        requireScore(confirmThreshold, "confirmThreshold");
    }

    /**
     * 判断得分是否达到人工确认阈值。
     *
     * @param score 匹配得分
     * @return 达到阈值返回 {@code true}
     */
    public boolean reachesConfirmThreshold(BigDecimal score) {
        return score != null && score.compareTo(confirmThreshold) >= 0;
    }

    private static void requireScore(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(field + " 必须在 0～1 之间");
        }
    }
}
