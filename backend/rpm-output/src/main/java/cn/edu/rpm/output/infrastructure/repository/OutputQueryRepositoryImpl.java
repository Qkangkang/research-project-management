package cn.edu.rpm.output.infrastructure.repository;

import cn.edu.rpm.output.infrastructure.mapper.OutputQueryMapper;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputLibraryRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputSearchRow;
import cn.edu.rpm.output.infrastructure.mapper.dto.OutputStatRow;
import cn.edu.rpm.output.infrastructure.mapper.param.OutputLibraryQueryParam;
import cn.edu.rpm.output.infrastructure.mapper.param.OutputSearchParam;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;

/**
 * 成果检索与统计仓储实现（T-OUT-014）。
 *
 * <p>分页计算在本层完成（{@code offset = (page - 1) * size}）；分组维度只接受
 * 白名单 {@code TYPE / YEAR / DEPT}，未知维度快速失败（防 SQL 结构注入）。</p>
 */
@Repository
public class OutputQueryRepositoryImpl implements OutputQueryRepository {

    /** 分组维度：成果类型。 */
    public static final String GROUP_TYPE = "TYPE";

    /** 分组维度：完成年度。 */
    public static final String GROUP_YEAR = "YEAR";

    /** 分组维度：部门。 */
    public static final String GROUP_DEPT = "DEPT";

    private final OutputQueryMapper outputQueryMapper;

    public OutputQueryRepositoryImpl(OutputQueryMapper outputQueryMapper) {
        this.outputQueryMapper = outputQueryMapper;
    }

    @Override
    public OutputPage<OutputLibraryRow> searchLibrary(String keyword, String outputType, String deptId,
                                                      Integer year, int page, int size) {
        OutputLibraryQueryParam param = new OutputLibraryQueryParam();
        param.setKeyword(trimToNull(keyword));
        param.setOutputType(trimToNull(outputType));
        param.setDeptId(trimToNull(deptId));
        param.setYear(year);
        param.setSize(size);
        param.setOffset(offset(page, size));
        List<OutputLibraryRow> rows = outputQueryMapper.selectLibraryPage(param);
        long total = outputQueryMapper.countLibrary(param);
        return new OutputPage<>(rows, total);
    }

    @Override
    public OutputPage<OutputSearchRow> searchVisible(String ownerId, String keyword, String outputType,
                                                     String status, String deptId, Integer year,
                                                     int page, int size) {
        OutputSearchParam param = new OutputSearchParam();
        param.setOwnerId(ownerId);
        param.setKeyword(trimToNull(keyword));
        param.setOutputType(trimToNull(outputType));
        param.setStatus(trimToNull(status));
        param.setDeptId(trimToNull(deptId));
        param.setYear(year);
        param.setSize(size);
        param.setOffset(offset(page, size));
        List<OutputSearchRow> rows = outputQueryMapper.selectVisiblePage(param);
        long total = outputQueryMapper.countVisible(param);
        return new OutputPage<>(rows, total);
    }

    @Override
    public List<OutputStatRow> stats(String groupBy, String deptId, Integer year) {
        String normalized = groupBy == null ? "" : groupBy.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case GROUP_TYPE -> outputQueryMapper.statsByType(trimToNull(deptId), year);
            case GROUP_YEAR -> outputQueryMapper.statsByYear(trimToNull(deptId));
            case GROUP_DEPT -> outputQueryMapper.statsByDept(year);
            default -> throw new IllegalArgumentException("不支持的统计分组维度: " + groupBy);
        };
    }

    private static int offset(int page, int size) {
        return (Math.max(page, 1) - 1) * size;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
