package cn.edu.rpm.workflow.infrastructure.entity;

import cn.edu.rpm.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * {@code wf_delegation} 代理审批 PO（T-WF-006）。
 *
 * <p>对应《02-数据模型与数据库设计.md》§3.2 与 V01 基线 DDL。委托在
 * {@code valid_from <= at < valid_to} 且 {@code status = ACTIVE} 时生效；</p>
 * <ul>
 *   <li>ck_wf_delegation_self：委托人不得代理自己；</li>
 *   <li>ck_wf_delegation_valid：valid_to 必须晚于 valid_from；</li>
 *   <li>查询走 idx_wf_delegation_delegate_valid (delegate_id, valid_from, valid_to)。</li>
 * </ul>
 *
 * <p>代理审批结果必须记录真实操作者与 {@code on_behalf_of}（委托人 ID），
 * 审计口径见 TC-IAM-009。</p>
 */
@TableName("wf_delegation")
public class WfDelegationEntity extends BaseEntity {

    /** 委托状态：生效中。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 委托状态：已过期。 */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** 委托状态：已撤销。 */
    public static final String STATUS_REVOKED = "REVOKED";

    /** 委托人用户 ID（iam_user.id）。 */
    @TableField("delegator_id")
    private String delegatorId;

    /** 代理人用户 ID（iam_user.id）；ck_wf_delegation_self 禁止与委托人相同。 */
    @TableField("delegate_id")
    private String delegateId;

    /** 委托范围（jsonb 文本，如审批业务类型/模板范围）；为空表示默认范围。 */
    @TableField("scope")
    private String scope;

    /** 生效起始时刻（含）。 */
    @TableField("valid_from")
    private OffsetDateTime validFrom;

    /** 生效结束时刻（不含）；ck_wf_delegation_valid 保证晚于 valid_from。 */
    @TableField("valid_to")
    private OffsetDateTime validTo;

    /** 委托状态：ACTIVE / EXPIRED / REVOKED。 */
    @TableField("status")
    private String status;

    public String getDelegatorId() {
        return delegatorId;
    }

    public void setDelegatorId(String delegatorId) {
        this.delegatorId = delegatorId;
    }

    public String getDelegateId() {
        return delegateId;
    }

    public void setDelegateId(String delegateId) {
        this.delegateId = delegateId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public OffsetDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(OffsetDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public OffsetDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(OffsetDateTime validTo) {
        this.validTo = validTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
