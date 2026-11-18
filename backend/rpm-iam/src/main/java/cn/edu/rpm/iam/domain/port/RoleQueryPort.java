package cn.edu.rpm.iam.domain.port;

import cn.edu.rpm.iam.domain.model.RoleView;

import java.util.Collection;
import java.util.List;

/**
 * 角色批量查询端口（Batch-4 / T-IAM-009）。
 *
 * <p>只读投影角色码与授权范围类型，供数据范围解析；批量查询避免循环内 SQL（RULE-DB2）。</p>
 */
public interface RoleQueryPort {

    /**
     * 按角色 ID 批量查询角色。
     *
     * @param roleIds 角色 ID 集合；空集合直接返回空列表
     * @return 角色视图列表
     */
    List<RoleView> findByIds(Collection<String> roleIds);
}
