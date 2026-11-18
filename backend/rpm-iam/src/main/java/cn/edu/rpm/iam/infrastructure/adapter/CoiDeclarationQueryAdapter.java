package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.common.util.UuidUtils;
import cn.edu.rpm.iam.domain.model.CoiDeclarationView;
import cn.edu.rpm.iam.domain.port.CoiDeclarationQueryPort;
import cn.edu.rpm.iam.infrastructure.repository.CoiDeclarationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link CoiDeclarationQueryPort} 的利益冲突声明适配器（Batch-4 / T-IAM-011）。
 *
 * <p>把 {@code coi_declaration} PO 投影为领域只读视图（仅"相关人 + 状态"两个字段），
 * 使领域层不依赖 MyBatis 注解与实体。项目尚未引入 MapStruct（技术文档未列入依赖清单），
 * 此处为两字段投影的显式转换，不承载业务规则。</p>
 */
@Repository
public class CoiDeclarationQueryAdapter implements CoiDeclarationQueryPort {

    private final CoiDeclarationRepository coiDeclarationRepository;

    public CoiDeclarationQueryAdapter(CoiDeclarationRepository coiDeclarationRepository) {
        this.coiDeclarationRepository = coiDeclarationRepository;
    }

    @Override
    public List<CoiDeclarationView> findByBizAndExpert(String bizType, String bizId, String expertId) {
        return coiDeclarationRepository.findByBizAndExpert(bizType, bizId, expertId).stream()
                .map(entity -> new CoiDeclarationView(UuidUtils.parse(entity.getRelatedUserId()), entity.getStatus()))
                .toList();
    }
}
