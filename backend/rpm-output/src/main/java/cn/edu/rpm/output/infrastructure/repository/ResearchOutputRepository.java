package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.entity.ResearchOutputEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code research_output} 成果主档仓储（T-OUT-001）。
 *
 * <p>纯单表数据访问契约，不含业务规则。约定：</p>
 * <ul>
 *   <li>同 DOI / 同专利号允许重复保存（无唯一约束），查重只做提示不阻断（BR-21）；</li>
 *   <li>更新以实体 {@code version} 旧值作乐观锁条件；</li>
 *   <li>列表与详情必须由调用方注入数据范围/权限条件，仓储不做授权判断。</li>
 * </ul>
 */
public interface ResearchOutputRepository {

    /**
     * 插入成果主档。
     *
     * @param output 待插入的成果 PO
     */
    void insert(ResearchOutputEntity output);

    /**
     * 按主键查询成果。
     *
     * @param id 成果 ID
     * @return 命中的成果；不存在返回空
     */
    Optional<ResearchOutputEntity> findById(String id);

    /**
     * 按 DOI 精确查询（查重提示用，可返回多条）。
     *
     * @param doi DOI
     * @return 命中的成果列表；无命中返回空列表
     */
    List<ResearchOutputEntity> findByDoi(String doi);

    /**
     * 按专利号精确查询（查重提示用，可返回多条）。
     *
     * @param patentNo 专利号
     * @return 命中的成果列表；无命中返回空列表
     */
    List<ResearchOutputEntity> findByPatentNo(String patentNo);

    /**
     * 按标题精确查询（去首尾空格后比较，查重提示用）。
     *
     * @param title 标题
     * @return 命中的成果列表；无命中返回空列表
     */
    List<ResearchOutputEntity> findByTitle(String title);

    /**
     * 按完成人与状态查询（「我的成果」与按状态筛选）。
     *
     * @param ownerId 完成人用户 ID
     * @param status  成果状态
     * @return 命中的成果列表；无命中返回空列表
     */
    List<ResearchOutputEntity> findByOwnerIdAndStatus(String ownerId, String status);

    /**
     * 按主键集合批量查询（项目成果列表，禁止循环单查）。
     *
     * @param ids 成果 ID 集合
     * @return 命中的成果列表；空集合返回空列表
     */
    List<ResearchOutputEntity> findByIds(Collection<String> ids);

    /**
     * 按乐观锁更新成果主档。
     *
     * @param output 携带主键与旧版本的成果 PO
     * @return 受影响行数；0 表示版本冲突或记录不存在
     */
    int update(ResearchOutputEntity output);
}
