package cn.edu.rpm.iam.domain.port;

import cn.edu.rpm.iam.domain.model.TempGrantView;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 临时授权查询端口（Batch-4 / T-IAM-009）。
 *
 * <p>返回指定时点存储层认为有效的授权记录；领域层仍会逐条用
 * {@code GrantValidityGuard} 复核有效期并按动作过滤。</p>
 */
public interface TempGrantQueryPort {

    /**
     * 查询被授权人在指定时点的有效临时授权。
     *
     * @param granteeId 被授权人用户 ID（uuid 文本）
     * @param at        判定时点
     * @return 授权视图列表；无授权返回空列表
     */
    List<TempGrantView> findValidByGrantee(String granteeId, OffsetDateTime at);
}
