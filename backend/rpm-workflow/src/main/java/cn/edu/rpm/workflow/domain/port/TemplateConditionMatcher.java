package cn.edu.rpm.workflow.domain.port;

import cn.edu.rpm.workflow.domain.dto.TemplateRoutingContext;
import cn.edu.rpm.workflow.domain.model.BudgetConditionRange;

import java.util.Optional;

/**
 * 模板路由条件解析与匹配端口（T-WF-012 / T-WF-013；技术文档 03 §3.3）。
 *
 * <p>条件表达式格式（受控 JSON，字段/运算符白名单见 03 §3.3）：</p>
 * <pre>{@code
 * {"conditions":[
 *   {"field":"project_type","operator":"EQ","value":"YEAR_PLAN"},
 *   {"field":"budget_total","operator":"BETWEEN","value":[1000000,2000000]}
 * ]}
 * }</pre>
 * <p>空串或 {@code {}} 表示无条件命中。引用白名单外字段、未知运算符或格式非法时
 * 抛 {@code RPM-SYS-001}（模板条件冲突或不可解析）。</p>
 */
public interface TemplateConditionMatcher {

    /**
     * 判断路由上下文是否满足条件表达式。
     *
     * @param conditionExpr 条件表达式 JSON；空串/null 视为无条件
     * @param context       路由上下文
     * @return 满足返回 {@code true}
     * @throws cn.edu.rpm.common.exception.BizException 条件非法 {@code RPM-SYS-001}
     */
    boolean matches(String conditionExpr, TemplateRoutingContext context);

    /**
     * 校验条件表达式结构、字段白名单与运算符/值类型（不执行匹配）。
     *
     * <p>模板发布前调用：条件不可解析时禁止发布，避免运行期路由失败
     * （TC-APP-017 同批发布校验的一部分）。</p>
     *
     * @param conditionExpr 条件表达式 JSON；空串/null 视为无条件（通过）
     * @throws cn.edu.rpm.common.exception.BizException 条件非法 {@code RPM-SYS-001}
     */
    void validate(String conditionExpr);

    /**
     * 提取条件表达式中的预算金额区间（用于发布时重叠检测）。
     *
     * <p>仅识别 {@code budget_total} 的 {@code GT/GTE/LT/LTE/BETWEEN} 条件；
     * 多条金额条件取第一条；无金额条件返回 {@link Optional#empty()}。</p>
     *
     * @param conditionExpr 条件表达式 JSON；空串/null 返回空
     * @return 金额区间；无金额条件返回 {@link Optional#empty()}
     * @throws cn.edu.rpm.common.exception.BizException 条件非法 {@code RPM-SYS-001}
     */
    Optional<BudgetConditionRange> findBudgetRange(String conditionExpr);
}
