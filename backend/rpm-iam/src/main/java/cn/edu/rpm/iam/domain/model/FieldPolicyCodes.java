package cn.edu.rpm.iam.domain.model;

import java.util.Set;

/**
 * 字段级授权策略码（技术文档 03 §2.4「字段脱敏」）。
 *
 * <p>策略码同时是字段级权限码：{@code AuthContext.permissionCodes} 持有该码表示
 * 调用方可见明文，否则对应字段按默认规则脱敏。{@code FieldMaskService} 的
 * {@code policyCodes} 入参使用同一组常量，由资源类型映射得到（见
 * {@link #forResourceType(String)}）。</p>
 */
public final class FieldPolicyCodes {

    /** 银行账号/收款信息：只显示后 4 位。 */
    public static final String FINANCE = "field:finance:read";

    /** 身份证件：只显示前 1 后 1。 */
    public static final String ID_CARD = "field:id_card:read";

    /** 联系方式：中间 4 位掩码。 */
    public static final String CONTACT = "field:contact:read";

    /** 专家身份：对无权限方隐藏姓名/单位。 */
    public static final String EXPERT_IDENTITY = "field:expert:identity:read";

    private FieldPolicyCodes() {
    }

    /**
     * 按资源类型返回需要施加的字段策略码集合。
     *
     * <p>支出详情涉及收款账户（财务敏感）；评审意见涉及专家身份；其余资源默认不施加
     * 字段策略（业务 DTO 自身不得携带无策略保护的敏感明文）。</p>
     *
     * @param resourceType 资源类型（{@link ResourceTypes} 常量）
     * @return 策略码集合；未知类型返回空集合
     */
    public static Set<String> forResourceType(String resourceType) {
        if (ResourceTypes.EXPENSE.equals(resourceType)) {
            return Set.of(FINANCE);
        }
        if (ResourceTypes.REVIEW.equals(resourceType)) {
            return Set.of(EXPERT_IDENTITY);
        }
        return Set.of();
    }
}
