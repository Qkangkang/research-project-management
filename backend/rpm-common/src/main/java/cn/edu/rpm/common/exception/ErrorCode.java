package cn.edu.rpm.common.exception;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务错误码枚举，与《04-核心业务接口设计.md》§1.3 错误码表逐条对齐（共 68 条）。
 *
 * <p>命名规则 {@code RPM-{模块}-{三位}}；{@code httpStatus} 为文档约定的 HTTP 状态。
 * 禁止在代码中臆造表外错误码；新增错误码必须先修订接口文档。</p>
 */
public enum ErrorCode {

    // ===== RPM-COMMON 通用 =====
    COMMON_400("RPM-COMMON-400", 400, "参数校验失败"),
    COMMON_401("RPM-COMMON-401", 401, "未登录或会话失效"),
    COMMON_403("RPM-COMMON-403", 403, "无权限"),
    COMMON_404("RPM-COMMON-404", 404, "资源不存在"),
    COMMON_409("RPM-COMMON-409", 409, "版本冲突或重复操作"),
    COMMON_422("RPM-COMMON-422", 422, "业务规则不满足"),
    COMMON_429("RPM-COMMON-429", 429, "请求过于频繁"),
    COMMON_500("RPM-COMMON-500", 500, "未预期服务端错误"),

    // ===== RPM-APPLY 申报 =====
    APPLY_001("RPM-APPLY-001", 422, "申报表单校验失败"),
    APPLY_002("RPM-APPLY-002", 409, "申报版本冲突"),
    APPLY_003("RPM-APPLY-003", 422, "批次已关闭"),
    APPLY_004("RPM-APPLY-004", 422, "当前状态不允许该动作"),
    APPLY_005("RPM-APPLY-005", 422, "外部项目证明材料缺失"),
    APPLY_006("RPM-APPLY-006", 422, "专家回避声明未完成或冲突未处置"),
    APPLY_007("RPM-APPLY-007", 422, "评分表项缺失、分值越界或总分与模板重算不符"),
    APPLY_008("RPM-APPLY-008", 422, "汇总条件不满足（有效提交不足）"),

    // ===== RPM-WF 审批 =====
    WF_001("RPM-WF-001", 422, "未找到可用审批模板"),
    WF_002("RPM-WF-002", 422, "审批节点无处理人"),
    WF_003("RPM-WF-003", 422, "禁止审批本人事项"),
    WF_004("RPM-WF-004", 409, "任务已被处理"),
    WF_005("RPM-WF-005", 409, "审批版本已过期"),
    WF_006("RPM-WF-006", 422, "存在未处理的利益冲突"),

    // ===== RPM-PROJ 项目 =====
    PROJ_001("RPM-PROJ-001", 404, "项目不存在"),
    PROJ_002("RPM-PROJ-002", 422, "项目状态不允许该动作"),
    PROJ_003("RPM-PROJ-003", 409, "项目编号重复"),
    PROJ_004("RPM-PROJ-004", 422, "项目基线缺失"),
    PROJ_005("RPM-PROJ-005", 422, "成员变更冲突"),
    PROJ_006("RPM-PROJ-006", 422, "交接条件不满足"),

    // ===== RPM-EXEC 执行 =====
    EXEC_001("RPM-EXEC-001", 409, "同一周期报告已存在"),
    EXEC_002("RPM-EXEC-002", 422, "报告已逾期"),
    EXEC_003("RPM-EXEC-003", 422, "里程碑缺少完成判定"),
    EXEC_004("RPM-EXEC-004", 422, "交付物文件无效"),
    EXEC_005("RPM-EXEC-005", 404, "任务不存在或非被授权人"),
    EXEC_006("RPM-EXEC-006", 422, "任务状态迁移不合法"),

    // ===== RPM-FUND 经费 =====
    FUND_001("RPM-FUND-001", 422, "可用余额不足"),
    FUND_002("RPM-FUND-002", 422, "预算版本未生效或科目不匹配"),
    FUND_003("RPM-FUND-003", 409, "重复资金事件且载荷不一致"),
    FUND_004("RPM-FUND-004", 422, "预算明细或科目不合法"),
    FUND_005("RPM-FUND-005", 422, "支出状态不允许该动作"),
    FUND_006("RPM-FUND-006", 422, "需先走预算调整或特批"),
    FUND_007("RPM-FUND-007", 409, "预算版本并发生效冲突"),
    FUND_008("RPM-FUND-008", 422, "存在未关闭对账差异"),
    FUND_009("RPM-FUND-009", 422, "支付金额、币种或外部单号异常"),

    // ===== RPM-OUT 成果 =====
    OUT_001("RPM-OUT-001", 200, "疑似重复提示，成果仍保存"),
    OUT_002("RPM-OUT-002", 422, "成果证据缺失"),
    OUT_003("RPM-OUT-003", 422, "成果项目关联无效"),
    OUT_004("RPM-OUT-004", 422, "成果审核状态不允许"),

    // ===== RPM-ACC 验收 =====
    ACC_001("RPM-ACC-001", 422, "验收条件未满足"),
    ACC_002("RPM-ACC-002", 422, "验收快照哈希不一致"),
    ACC_003("RPM-ACC-003", 422, "验收状态不允许该动作"),
    ACC_004("RPM-ACC-004", 422, "归档完整性校验失败"),
    ACC_005("RPM-ACC-005", 422, "验收意见不完整"),
    ACC_006("RPM-ACC-006", 422, "项目已冻结或结论非法"),
    ACC_007("RPM-ACC-007", 409, "归档包已接收"),
    ACC_008("RPM-ACC-008", 422, "存在未确认的验收标准重定任务"),

    // ===== RPM-INT 集成 =====
    INT_001("RPM-INT-001", 503, "外部系统超时或不可用"),
    INT_002("RPM-INT-002", 401, "回调签名校验失败"),
    INT_003("RPM-INT-003", 422, "回调报文或事件类型非法"),
    INT_004("RPM-INT-004", 409, "外部单号冲突"),

    // ===== RPM-RPT 报表 =====
    RPT_001("RPM-RPT-001", 422, "导出条件过宽或字段非法"),
    RPT_002("RPM-RPT-002", 410, "导出文件已过期"),

    // ===== RPM-FILE 文件 =====
    FILE_001("RPM-FILE-001", 422, "文件未就绪或未通过扫描"),
    FILE_002("RPM-FILE-002", 422, "文件类型、大小或内容不合法"),

    // ===== RPM-SYS 系统 =====
    SYS_001("RPM-SYS-001", 422, "模板条件冲突或不可解析"),
    SYS_002("RPM-SYS-002", 422, "配置值非法"),
    SYS_003("RPM-SYS-003", 409, "审批模板发布冲突"),

    // ===== RPM-PORT 外部链接 =====
    PORT_001("RPM-PORT-001", 410, "链接无效、过期或已消费"),
    PORT_002("RPM-PORT-002", 403, "链接目标无权限");

    private final String code;

    private final int httpStatus;

    private final String defaultMessage;

    ErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 按错误码字符串反查枚举。
     *
     * @param code 错误码字符串，如 {@code RPM-COMMON-404}
     * @return 对应枚举；未命中返回 {@code null}
     */
    public static ErrorCode fromCode(String code) {
        return LOOKUP.get(code);
    }

    private static final Map<String, ErrorCode> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toMap(ErrorCode::getCode, Function.identity()));
}
