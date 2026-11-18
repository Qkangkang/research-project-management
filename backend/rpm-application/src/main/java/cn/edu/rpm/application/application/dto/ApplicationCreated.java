package cn.edu.rpm.application.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 创建申报草稿结果（响应形状与《04-核心业务接口设计.md》§3.1 一致）。
 *
 * @param applicationId 申报 ID
 * @param applicationNo 申报编号
 * @param status        申报状态（DRAFT）
 * @param version       主档乐观锁版本号（新建为 0）
 */
public record ApplicationCreated(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("application_no") String applicationNo,
        @JsonProperty("status") String status,
        @JsonProperty("version") int version
) {
}
