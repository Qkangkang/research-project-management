package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ChangeRequestVersionEntity;

import java.util.List;
import java.util.Optional;

/**
 * {@code change_request_version} 变更版本仓储（T-PRJ-008）。
 */
public interface ChangeRequestVersionRepository {

    /**
     * 插入变更版本。
     *
     * @param version 待插入的版本 PO
     */
    void insert(ChangeRequestVersionEntity version);

    /**
     * 按主键查询变更版本。
     *
     * @param id 版本 ID
     * @return 命中的版本；不存在返回空
     */
    Optional<ChangeRequestVersionEntity> findById(String id);

    /**
     * 按变更单 + 版本号查询（uk_change_request_version_no）。
     *
     * @param changeRequestId 变更单 ID
     * @param versionNo       版本号
     * @return 命中的版本；不存在返回空
     */
    Optional<ChangeRequestVersionEntity> findByChangeRequestIdAndVersionNo(String changeRequestId,
                                                                           int versionNo);

    /**
     * 按变更单查询全部版本（按版本号升序，供历史追溯）。
     *
     * @param changeRequestId 变更单 ID
     * @return 版本列表；无命中返回空列表
     */
    List<ChangeRequestVersionEntity> findByChangeRequestId(String changeRequestId);

    /**
     * 查询变更单当前最大版本号（无版本返回 0）。
     *
     * @param changeRequestId 变更单 ID
     * @return 最大版本号
     */
    int findMaxVersionNo(String changeRequestId);

    /**
     * 回填批准后生成的新基线 ID。
     *
     * @param version 携带主键的新基线信息
     * @return 受影响行数
     */
    int update(ChangeRequestVersionEntity version);
}
