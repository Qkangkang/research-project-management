package cn.edu.rpm.boot.config;

import cn.edu.rpm.output.domain.model.DuplicateMatchPolicy;
import cn.edu.rpm.output.domain.service.OutputDuplicateService;
import cn.edu.rpm.output.domain.service.OutputSnapshotService;
import cn.edu.rpm.output.domain.service.OutputTypeTemplateResolver;
import cn.edu.rpm.output.infrastructure.repository.OutputDuplicateCandidateRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputEvidenceRepository;
import cn.edu.rpm.output.infrastructure.repository.OutputVersionRepository;
import cn.edu.rpm.output.infrastructure.repository.ResearchOutputRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * 成果领域服务装配（Batch-19 / Batch-20；T-OUT-007 / T-OUT-010 / T-OUT-015）。
 *
 * <p>成果领域服务保持纯 Java（不标注 {@code @Service}），由 rpm-boot 统一装配；
 * 查重分值与人工确认阈值按 {@code rpm.output.duplicate.*} 外置，禁止硬编码（BR-21）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class OutputDomainConfig {

    /**
     * 成果类型模板解析器（必填字段、DOI 格式、完成日期校验）。
     *
     * @return 类型模板解析器
     */
    @Bean
    public OutputTypeTemplateResolver outputTypeTemplateResolver() {
        return new OutputTypeTemplateResolver();
    }

    /**
     * 成果查重领域服务。
     *
     * <p>默认分值：DOI/专利号 = 1.00（强匹配），完成人+名称 = 0.90，
     * 名称相同但完成人不同 = 0.60（低分提示）；确认阈值 0.90。</p>
     *
     * @param researchOutputRepository     成果仓储
     * @param duplicateCandidateRepository 疑似重复候选仓储
     * @param doiScore                     DOI 匹配得分
     * @param patentScore                  专利号匹配得分
     * @param ownerScore                   完成人+名称匹配得分
     * @param titleScore                   名称匹配低分
     * @param confirmThreshold             人工确认阈值
     * @return 查重领域服务
     */
    @Bean
    public OutputDuplicateService outputDuplicateService(
            ResearchOutputRepository researchOutputRepository,
            OutputDuplicateCandidateRepository duplicateCandidateRepository,
            @Value("${rpm.output.duplicate.doi-score:1.00}") BigDecimal doiScore,
            @Value("${rpm.output.duplicate.patent-score:1.00}") BigDecimal patentScore,
            @Value("${rpm.output.duplicate.owner-score:0.90}") BigDecimal ownerScore,
            @Value("${rpm.output.duplicate.title-score:0.60}") BigDecimal titleScore,
            @Value("${rpm.output.duplicate.confirm-threshold:0.90}") BigDecimal confirmThreshold) {
        DuplicateMatchPolicy policy = new DuplicateMatchPolicy(
                doiScore, patentScore, ownerScore, titleScore, confirmThreshold);
        return new OutputDuplicateService(researchOutputRepository,
                duplicateCandidateRepository, policy);
    }

    /**
     * 成果快照冻结领域服务。
     *
     * @param researchOutputRepository 成果仓储
     * @param outputVersionRepository  成果版本仓储
     * @param outputEvidenceRepository 成果证据仓储
     * @return 快照服务
     */
    @Bean
    public OutputSnapshotService outputSnapshotService(
            ResearchOutputRepository researchOutputRepository,
            OutputVersionRepository outputVersionRepository,
            OutputEvidenceRepository outputEvidenceRepository) {
        return new OutputSnapshotService(researchOutputRepository, outputVersionRepository,
                outputEvidenceRepository);
    }
}
