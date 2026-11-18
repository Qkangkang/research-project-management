package cn.edu.rpm.workflow.infrastructure.mapper;

import cn.edu.rpm.workflow.infrastructure.entity.WfInstanceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code wf_instance} 表 Mapper（T-WF-004）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；「按业务版本取最近实例」「我发起的分页」
 * 的 SQL 统一维护在 {@code resources/mapper/WfInstanceMapper.xml}（禁止注解 SQL）。
 * 部分唯一索引 {@code uk_wf_instance_running} 由数据库 DDL 保证，
 * 重复启动由数据库抛唯一冲突。</p>
 */
@Mapper
public interface WfInstanceMapper extends BaseMapper<WfInstanceEntity> {

    /**
     * 按业务版本查询最近一次审批实例（含终态）。
     *
     * @param bizType      业务类型
     * @param bizId        业务对象 ID
     * @param bizVersionId 业务版本 ID
     * @return 最近实例；无记录返回 {@code null}
     */
    WfInstanceEntity selectLatestByBiz(@Param("bizType") String bizType,
                                       @Param("bizId") String bizId,
                                       @Param("bizVersionId") String bizVersionId);

    /**
     * 查询我发起的审批实例（started_at 倒序分页）。
     *
     * @param initiatorId 发起人用户 ID（32 位十六进制）
     * @param offset      偏移量
     * @param limit       每页条数
     * @return 实例列表；无命中返回空列表
     */
    List<WfInstanceEntity> selectInitiatedPage(@Param("initiatorId") String initiatorId,
                                               @Param("offset") long offset,
                                               @Param("limit") int limit);

    /**
     * 统计我发起的审批实例总数。
     *
     * @param initiatorId 发起人用户 ID（32 位十六进制）
     * @return 总条数
     */
    long countInitiated(@Param("initiatorId") String initiatorId);
}
