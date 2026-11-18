package cn.edu.rpm.organization.application.service;

import cn.edu.rpm.organization.api.dto.OrgUnitOptionView;
import cn.edu.rpm.organization.infrastructure.entity.OrgUnitEntity;
import cn.edu.rpm.organization.infrastructure.repository.OrgUnitRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组织只读查询服务（下拉选项与名称回填）。
 */
@Service
public class OrgUnitQueryService {

    private final OrgUnitRepository orgUnitRepository;

    public OrgUnitQueryService(OrgUnitRepository orgUnitRepository) {
        this.orgUnitRepository = orgUnitRepository;
    }

    /**
     * 全部生效组织选项。
     *
     * @return 组织选项列表
     */
    public List<OrgUnitOptionView> listActiveOptions() {
        return orgUnitRepository.findAllActive().stream()
                .map(OrgUnitOptionView::from)
                .toList();
    }

    /**
     * 按 ID 批量取组织名称。
     *
     * @param ids 组织 ID 集合
     * @return ID → 名称；无命中为空 Map
     */
    public Map<String, String> findNames(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return orgUnitRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(OrgUnitEntity::getId, OrgUnitEntity::getName, (a, b) -> a));
    }

    /**
     * 按 ID 取单个组织名称。
     *
     * @param id 组织 ID
     * @return 名称；不存在为空
     */
    public Optional<String> findName(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return orgUnitRepository.findById(id).map(OrgUnitEntity::getName);
    }
}
