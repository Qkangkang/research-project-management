package cn.edu.rpm.iam.domain.port;

import cn.edu.rpm.iam.domain.model.CoiDeclarationView;

import java.util.List;

/**
 * 利益冲突声明查询端口（{@code coi_declaration} 的领域只读投影）。
 *
 * <p>职责分离守护通过本端口判定"专家与申请人同部门时是否已完成回避处置"，
 * 避免领域层直接依赖 {@code CoiDeclarationEntity} / MyBatis 注解（DEP-2）。</p>
 */
public interface CoiDeclarationQueryPort {

    /**
     * 查询某专家对指定业务对象的全部利益冲突声明。
     *
     * @param bizType  业务对象类型，如 {@code REVIEW}
     * @param bizId    业务对象 ID（uuid 文本）
     * @param expertId 专家用户 ID（uuid 文本）
     * @return 声明视图列表；无声明返回空列表
     */
    List<CoiDeclarationView> findByBizAndExpert(String bizType, String bizId, String expertId);
}
