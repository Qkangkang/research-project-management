package cn.edu.rpm.iam.infrastructure.repository;

import cn.edu.rpm.iam.infrastructure.entity.CoiDeclarationEntity;

import java.util.List;

/**
 * {@code coi_declaration} 利益冲突声明仓储接口（T-IAM-006）。
 *
 * <p>纯单表 CRUD 数据访问契约；供 Batch-4 SeparationOfDutyGuard 做专家回避校验。
 * 注意 uk_coi_biz_expert 唯一键为 (biz_type, biz_id, expert_id, related_user_id) 四列，
 * 按三列（业务对象 + 专家）查询可能返回多条（同一专家对同一业务对象声明多个相关人）。</p>
 */
public interface CoiDeclarationRepository {

    /**
     * 插入利益冲突声明。
     *
     * <p>主键 {@code id} 由 MyBatis-Plus 在执行期生成；同一
     * (biz_type, biz_id, expert_id, related_user_id) 重复插入时抛
     * {@link org.springframework.dao.DuplicateKeyException}（uk_coi_biz_expert 唯一）。</p>
     *
     * @param declaration 待插入的声明 PO
     */
    void insert(CoiDeclarationEntity declaration);

    /**
     * 按业务对象与专家查询声明（idx_coi_biz_expert 三列对齐）。
     *
     * @param bizType  业务对象类型，如 REVIEW
     * @param bizId    业务对象 ID
     * @param expertId 专家用户 ID
     * @return 该专家对该业务对象的全部声明；无命中返回空列表
     */
    List<CoiDeclarationEntity> findByBizAndExpert(String bizType, String bizId, String expertId);

    /**
     * 按专家查询其全部利益冲突声明。
     *
     * @param expertId 专家用户 ID
     * @return 该专家的全部声明；无命中返回空列表
     */
    List<CoiDeclarationEntity> findByExpertId(String expertId);
}
