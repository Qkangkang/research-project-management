package cn.edu.rpm.project.domain.service;

import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;

import java.util.Objects;

/**
 * 项目编号生成器（T-PRJ-010；技术文档 02 §3.7 / 04 §3.4）。
 *
 * <p>复用 {@link NumberGenerator} 的 {@code PROJECT_NO} 规则生成
 * {@code KY-{年}-{6位}}；生成后查重，同号冲突重试 3 次，仍失败抛
 * {@code RPM-PROJ-003}（TC-APP-040 / TC-APP-041）。数据库唯一约束
 * {@code uk_project_no} 仍是并发下的最终兜底。</p>
 */
public class ProjectNumberGenerator {

    /** 项目编号规则编码（{@code number_rule.rule_code}）。 */
    public static final String RULE_PROJECT_NO = "PROJECT_NO";

    /** 允许的总尝试次数（首次 + 3 次重试）。 */
    static final int MAX_ATTEMPTS = 4;

    private final NumberGenerator numberGenerator;

    private final ProjectRepository projectRepository;

    /**
     * 构造项目编号生成器。
     *
     * @param numberGenerator   通用编号生成器
     * @param projectRepository 项目仓储（查重用）
     */
    public ProjectNumberGenerator(NumberGenerator numberGenerator,
                                  ProjectRepository projectRepository) {
        this.numberGenerator = Objects.requireNonNull(numberGenerator, "numberGenerator 不能为空");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository 不能为空");
    }

    /**
     * 生成下一个唯一项目编号。
     *
     * @return 形如 {@code KY-2026-000456} 的项目编号
     * @throws BizException 连续 4 次命中已存在编号 {@code RPM-PROJ-003}
     */
    public String next() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String candidate = numberGenerator.next(RULE_PROJECT_NO);
            if (!projectRepository.existsByProjectNo(candidate)) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.PROJ_003, "项目编号生成冲突，请重试");
    }
}
