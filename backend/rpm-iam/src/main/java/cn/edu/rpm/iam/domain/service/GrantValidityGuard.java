package cn.edu.rpm.iam.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;

import java.time.Instant;

/**
 * 临时授权有效期守护（T-IAM-012，技术文档 03 §2.5、验证用例 TC-IAM-007/008）。
 *
 * <p>判定口径：{@code valid_from <= requestTime < valid_to}（左闭右开）。
 * 请求时间正好等于 {@code valid_to} 时视为已过期；{@code valid_to} 为空不符合
 * {@code iam_temp_grant} 的 DDL 约束（ck_temp_grant_valid 要求非空），一律按失效处理。
 * 该判定必须使用 {@code AuthContext.requestTime} 的可注入时点，禁止隐式取系统时间。</p>
 */
public class GrantValidityGuard {

    /**
     * 校验授权窗口有效；失效抛 {@code RPM-COMMON-403}（不泄露授权是否存在）。
     *
     * @param validFrom   授权有效期起（含）
     * @param validTo     授权有效期止（不含）
     * @param requestTime 请求时点
     * @throws BizException 窗口为空或请求时点不在窗口内
     */
    public void check(Instant validFrom, Instant validTo, Instant requestTime) {
        if (!isValid(validFrom, validTo, requestTime)) {
            throw new BizException(ErrorCode.COMMON_403, "临时授权已过期或尚未生效");
        }
    }

    /**
     * 判断授权窗口在请求时点是否有效（不抛异常版本，供数据范围解析过滤使用）。
     *
     * @param validFrom   授权有效期起（含）
     * @param validTo     授权有效期止（不含）
     * @param requestTime 请求时点
     * @return 有效返回 {@code true}；任一参数为空或越界返回 {@code false}
     */
    public boolean isValid(Instant validFrom, Instant validTo, Instant requestTime) {
        if (validFrom == null || validTo == null || requestTime == null) {
            return false;
        }
        return !requestTime.isBefore(validFrom) && requestTime.isBefore(validTo);
    }
}
