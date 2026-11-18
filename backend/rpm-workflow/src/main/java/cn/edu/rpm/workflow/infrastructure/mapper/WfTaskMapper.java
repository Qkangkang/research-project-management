package cn.edu.rpm.workflow.infrastructure.mapper;

import cn.edu.rpm.workflow.infrastructure.entity.WfTaskEntity;
import cn.edu.rpm.workflow.infrastructure.repository.WorkflowTaskRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code wf_task} 表 Mapper（T-WF-005 / T-WF-009）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；{@code updateById} 由乐观锁拦截器自动追加
 * {@code version} 条件。待办分页需要联表 {@code wf_instance} 补充 {@code biz_type}/
 * {@code biz_id}，SQL 统一维护在 {@code resources/mapper/WfTaskMapper.xml}
 * （禁止注解 SQL，便于 DBA 审阅与索引对齐）。</p>
 */
@Mapper
public interface WfTaskMapper extends BaseMapper<WfTaskEntity> {

    /**
     * 分页查询指定处理人的任务（联表 wf_instance 带出业务类型与业务 ID）。
     *
     * <p>排序：{@code due_at ASC NULLS LAST, created_at ASC}，与
     * idx_wf_task_assignee_status (assignee_id, status, due_at) 对齐。</p>
     *
     * @param assigneeId 处理人用户 ID（32 位无连字符文本）
     * @param status     任务状态
     * @param bizType    业务类型过滤；为空不过滤
     * @param offset     偏移量
     * @param limit      每页条数
     * @return 任务投影列表
     */
    List<WorkflowTaskRow> selectTaskPage(@Param("assigneeId") String assigneeId,
                                         @Param("status") String status,
                                         @Param("bizType") String bizType,
                                         @Param("offset") long offset,
                                         @Param("limit") int limit);

    /**
     * 统计指定处理人的任务总数（分页 total）。
     *
     * @param assigneeId 处理人用户 ID
     * @param status     任务状态
     * @param bizType    业务类型过滤；为空不过滤
     * @return 总记录数
     */
    long countTaskPage(@Param("assigneeId") String assigneeId,
                       @Param("status") String status,
                       @Param("bizType") String bizType);
}
