package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.domain.model.FieldPolicyCodes;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 敏感字段脱敏服务（T-IAM-010，技术文档 03 §2.4）。
 *
 * <p>脱敏必须在服务端完成，返回 DTO 中不得包含明文（TC-IAM-005）。策略码即字段级权限码：
 * {@code policyCodes} 列出本次目标涉及的敏感字段组，若 {@link AuthContext#permissionCodes()}
 * 未持有对应策略码，则按默认规则脱敏：</p>
 * <ul>
 *   <li>银行账号/收款信息（{@link FieldPolicyCodes#FINANCE}）→ 只显示后 4 位，
 *       形如 {@code **** **** **** 3456}；</li>
 *   <li>身份证件（{@link FieldPolicyCodes#ID_CARD}）→ 只显示前 1 后 1；</li>
 *   <li>联系方式（{@link FieldPolicyCodes#CONTACT}）→ 中间 4 位掩码；</li>
 *   <li>专家身份（{@link FieldPolicyCodes#EXPERT_IDENTITY}）→ 姓名/单位置 {@code null}。</li>
 * </ul>
 *
 * <p>实现约定了 DTO 形态：必须是可变 JavaBean（字段或 setter 可写），支持嵌套对象、
 * 集合/数组/Map 值递归；对不可写目标快速失败，禁止静默放行明文。类型判断基于字段名
 * （兼容 {@code bankAccount / bank_account} 两种命名），不依赖任何序列化框架。</p>
 */
public class FieldMaskService {

    /** 反射遍历深度上限，防御异常对象图。 */
    private static final int MAX_DEPTH = 8;

    private static final Set<String> FINANCE_FIELDS = Set.of("bankaccount", "bankcardno", "payeeaccount", "accountnumber");
    private static final Set<String> ID_CARD_FIELDS = Set.of("idcard", "idcardno");
    private static final Set<String> CONTACT_FIELDS = Set.of("mobile", "mobilephone", "phone", "contactphone");
    private static final Set<String> EXPERT_FIELDS = Set.of("reviewername", "reviewerdept", "expertname", "expertdept");

    /**
     * 对目标对象按策略码脱敏，返回同一实例（原地脱敏）。
     *
     * @param target      待脱敏 DTO（可变 JavaBean；可为 {@code null}）
     * @param ctx         授权上下文（提供已持有的字段级权限码）
     * @param policyCodes 本次目标涉及的字段策略码集合（见 {@link FieldPolicyCodes}）
     * @param <T>         DTO 类型
     * @return 脱敏后的同一实例；{@code target} 为 {@code null} 时返回 {@code null}
     */
    public <T> T mask(T target, AuthContext ctx, Set<String> policyCodes) {
        if (target == null) {
            return null;
        }
        if (policyCodes == null || policyCodes.isEmpty()) {
            return target;
        }
        Set<String> heldCodes = ctx == null ? Set.of() : ctx.permissionCodes();
        Set<String> applicable = new HashSet<>();
        for (String policyCode : policyCodes) {
            if (!heldCodes.contains(policyCode)) {
                applicable.add(policyCode);
            }
        }
        if (applicable.isEmpty()) {
            return target;
        }
        maskObject(target, applicable, new IdentityHashMap<>(), 0);
        return target;
    }

    private void maskObject(Object target, Set<String> applicable,
                            IdentityHashMap<Object, Boolean> visited, int depth) {
        if (target == null || depth > MAX_DEPTH || isJdkType(target.getClass())) {
            return;
        }
        if (visited.put(target, Boolean.TRUE) != null) {
            return;
        }
        if (target instanceof Collection<?> collection) {
            for (Object element : collection) {
                maskObject(element, applicable, visited, depth + 1);
            }
            return;
        }
        if (target instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                maskObject(value, applicable, visited, depth + 1);
            }
            return;
        }
        Class<?> type = target.getClass();
        if (type.isArray()) {
            int length = Array.getLength(target);
            for (int i = 0; i < length; i++) {
                maskObject(Array.get(target, i), applicable, visited, depth + 1);
            }
            return;
        }
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (!field.trySetAccessible()) {
                    throw new IllegalStateException("无法访问脱敏字段: " + current.getName() + "#" + field.getName());
                }
                Object value = readField(field, target);
                String policyCode = policyOf(field.getName());
                if (value instanceof String text && policyCode != null && applicable.contains(policyCode)) {
                    writeField(field, target, maskValue(policyCode, text));
                } else if (value != null && needsTraversal(value.getClass())) {
                    maskObject(value, applicable, visited, depth + 1);
                }
            }
        }
    }

    private static Object readField(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("读取脱敏字段失败: " + field.getName(), ex);
        }
    }

    private static void writeField(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("写入脱敏字段失败: " + field.getName(), ex);
        }
    }

    private static boolean isJdkType(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || type.getName().startsWith("java.");
    }

    private static boolean needsTraversal(Class<?> type) {
        if (type.isArray()) {
            return true;
        }
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return true;
        }
        return !isJdkType(type);
    }

    private static String policyOf(String fieldName) {
        String normalized = fieldName.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        if (FINANCE_FIELDS.contains(normalized)) {
            return FieldPolicyCodes.FINANCE;
        }
        if (ID_CARD_FIELDS.contains(normalized)) {
            return FieldPolicyCodes.ID_CARD;
        }
        if (CONTACT_FIELDS.contains(normalized)) {
            return FieldPolicyCodes.CONTACT;
        }
        if (EXPERT_FIELDS.contains(normalized)) {
            return FieldPolicyCodes.EXPERT_IDENTITY;
        }
        return null;
    }

    private static String maskValue(String policyCode, String text) {
        if (FieldPolicyCodes.FINANCE.equals(policyCode)) {
            return maskFinance(text);
        }
        if (FieldPolicyCodes.ID_CARD.equals(policyCode)) {
            return maskIdCard(text);
        }
        if (FieldPolicyCodes.CONTACT.equals(policyCode)) {
            return maskContact(text);
        }
        if (FieldPolicyCodes.EXPERT_IDENTITY.equals(policyCode)) {
            return null;
        }
        return text;
    }

    private static String maskFinance(String text) {
        String digits = text.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "****";
        }
        if (digits.length() <= 4) {
            return "****";
        }
        List<String> groups = new ArrayList<>();
        int end = digits.length();
        while (end > 0) {
            int start = Math.max(0, end - 4);
            groups.add(0, digits.substring(start, end));
            end = start;
        }
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) {
                masked.append(' ');
            }
            masked.append(i == groups.size() - 1 ? groups.get(i) : "****");
        }
        return masked.toString();
    }

    private static String maskIdCard(String text) {
        if (text.length() <= 2) {
            return "*".repeat(text.length());
        }
        return text.charAt(0) + "*".repeat(text.length() - 2) + text.charAt(text.length() - 1);
    }

    private static String maskContact(String text) {
        if (text.length() >= 8) {
            return text.substring(0, 3) + "*".repeat(text.length() - 7) + text.substring(text.length() - 4);
        }
        if (text.length() > 2) {
            return text.charAt(0) + "*".repeat(text.length() - 2) + text.charAt(text.length() - 1);
        }
        return "*".repeat(text.length());
    }
}
