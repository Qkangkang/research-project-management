package cn.edu.rpm.iam.application.service;

import cn.edu.rpm.iam.api.dto.UserOptionView;
import cn.edu.rpm.iam.infrastructure.entity.IamUserEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamUserRepository;
import cn.edu.rpm.organization.infrastructure.entity.OrgUnitEntity;
import cn.edu.rpm.organization.infrastructure.repository.OrgUnitRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户目录只读服务（下拉选项与名称回填）。
 */
@Service
public class UserDirectoryService {

    private final IamUserRepository userRepository;

    private final OrgUnitRepository orgUnitRepository;

    public UserDirectoryService(IamUserRepository userRepository,
                                OrgUnitRepository orgUnitRepository) {
        this.userRepository = userRepository;
        this.orgUnitRepository = orgUnitRepository;
    }

    /**
     * 生效用户选项，按工号/姓名模糊过滤。
     *
     * @param keyword 关键词；可为空
     * @return 用户选项列表
     */
    public List<UserOptionView> listOptions(String keyword) {
        List<IamUserEntity> users = userRepository.findActive(500);
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<IamUserEntity> filtered = normalized.isEmpty() ? users : users.stream()
                .filter(user -> contains(user.getEmployeeNo(), normalized)
                        || contains(user.getDisplayName(), normalized))
                .toList();
        Map<String, String> deptNames = orgUnitRepository.findByIds(
                        filtered.stream().map(IamUserEntity::getDeptId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(OrgUnitEntity::getId, OrgUnitEntity::getName, (a, b) -> a));
        return filtered.stream()
                .map(user -> new UserOptionView(user.getId(), user.getEmployeeNo(),
                        user.getDisplayName(), user.getDeptId(),
                        deptNames.get(user.getDeptId()), user.getStatus()))
                .toList();
    }

    /**
     * 按 ID 批量取显示名称。
     *
     * @param ids 用户 ID 集合
     * @return ID → 姓名；无命中为空 Map
     */
    public Map<String, String> findNames(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(IamUserEntity::getId, IamUserEntity::getDisplayName,
                        (a, b) -> a));
    }

    /**
     * 按 ID 取单个用户名称。
     *
     * @param id 用户 ID
     * @return 姓名；不存在为空
     */
    public Optional<String> findName(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findById(id).map(IamUserEntity::getDisplayName);
    }

    private static boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }
}
