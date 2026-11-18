package cn.edu.rpm.organization.infrastructure.repository;

import cn.edu.rpm.organization.infrastructure.entity.OrgUnitEntity;
import cn.edu.rpm.organization.infrastructure.mapper.OrgUnitMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OrgUnitRepositoryImpl} 纯单元测试（T-ORG-001 先行测试）。
 *
 * <p>验收锚点：插入组织成功；按 {@code parent_id} 查询子节点（idx_org_unit_parent，
 * 按 sort_order 稳定排序）；按 {@code path} 前缀 LIKE 查询子树。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrgUnitRepositoryImplTest {

    private static final String ROOT_ID = "d1111111111111111111111111111111";
    private static final String CHILD_ID = "d2222222222222222222222222222222";
    private static final String ROOT_PATH = "/UNIV/CSE";

    @Mock
    private OrgUnitMapper orgUnitMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<OrgUnitEntity>> wrapperCaptor;

    private OrgUnitRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OrgUnitEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new OrgUnitRepositoryImpl(orgUnitMapper);
    }

    private static OrgUnitEntity newUnit(String id, String code, String name, String path) {
        OrgUnitEntity unit = new OrgUnitEntity();
        unit.setId(id);
        unit.setCode(code);
        unit.setName(name);
        unit.setUnitType("DEPARTMENT");
        unit.setPath(path);
        unit.setStatus("ACTIVE");
        return unit;
    }

    @Test
    void insertDelegatesToMapper() {
        OrgUnitEntity unit = newUnit(ROOT_ID, "CSE", "计算机学院", ROOT_PATH);
        when(orgUnitMapper.insert(same(unit))).thenReturn(1);

        repository.insert(unit);

        verify(orgUnitMapper).insert(same(unit));
    }

    @Test
    void findByIdReturnsOrgUnit() {
        OrgUnitEntity unit = newUnit(ROOT_ID, "CSE", "计算机学院", ROOT_PATH);
        when(orgUnitMapper.selectById(ROOT_ID)).thenReturn(unit);

        Optional<OrgUnitEntity> result = repository.findById(ROOT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("CSE");
        assertThat(result.get().getName()).isEqualTo("计算机学院");
        assertThat(result.get().getUnitType()).isEqualTo("DEPARTMENT");
        assertThat(result.get().getPath()).isEqualTo(ROOT_PATH);
    }

    @Test
    void findByIdReturnsEmptyWhenOrgUnitMissing() {
        when(orgUnitMapper.selectById("no-such-id")).thenReturn(null);

        assertThat(repository.findById("no-such-id")).isEmpty();
    }

    @Test
    void findChildrenByParentIdFiltersParentColumnAndSortsBySortOrder() {
        OrgUnitEntity child = newUnit(CHILD_ID, "CSE01", "计算机科学与技术系", ROOT_PATH + "/CSE01");
        child.setParentId(ROOT_ID);
        child.setSortOrder(2);
        when(orgUnitMapper.selectList(ArgumentMatchers.<Wrapper<OrgUnitEntity>>any()))
                .thenReturn(List.of(child));

        List<OrgUnitEntity> children = repository.findChildrenByParentId(ROOT_ID);

        assertThat(children).hasSize(1);
        assertThat(children.get(0).getParentId()).isEqualTo(ROOT_ID);
        assertThat(children.get(0).getSortOrder()).isEqualTo(2);
        assertThat(children.get(0).getCode()).isEqualTo("CSE01");
        verify(orgUnitMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<OrgUnitEntity> wrapper = wrapperCaptor.getValue();
        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("parent_id =");
        assertThat(sql).contains("sort_order");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(ROOT_ID);
    }

    @Test
    void findChildrenByParentIdReturnsEmptyListWhenNoChild() {
        when(orgUnitMapper.selectList(ArgumentMatchers.<Wrapper<OrgUnitEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findChildrenByParentId("leaf-node-id")).isEmpty();
    }

    @Test
    void findSubtreeByPathMatchesPathPrefix() {
        OrgUnitEntity child = newUnit(CHILD_ID, "CSE01", "计算机科学与技术系", ROOT_PATH + "/CSE01");
        child.setParentId(ROOT_ID);
        when(orgUnitMapper.selectList(ArgumentMatchers.<Wrapper<OrgUnitEntity>>any()))
                .thenReturn(List.of(child));

        List<OrgUnitEntity> subtree = repository.findSubtreeByPath(ROOT_PATH);

        assertThat(subtree).hasSize(1);
        assertThat(subtree.get(0).getPath()).isEqualTo(ROOT_PATH + "/CSE01");
        verify(orgUnitMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<OrgUnitEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("path LIKE");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(ROOT_PATH + "%");
    }

    @Test
    void findSubtreeByPathReturnsEmptyListWhenNoMatch() {
        when(orgUnitMapper.selectList(ArgumentMatchers.<Wrapper<OrgUnitEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findSubtreeByPath("/UNIV/NO-SUCH-BRANCH")).isEmpty();
    }

    @Test
    void findByIdsUsesBatchQuery() {
        OrgUnitEntity root = newUnit(ROOT_ID, "CSE", "计算机学院", ROOT_PATH);
        OrgUnitEntity child = newUnit(CHILD_ID, "CSE01", "计算机科学与技术系", ROOT_PATH + "/CSE01");
        when(orgUnitMapper.selectBatchIds(List.of(ROOT_ID, CHILD_ID))).thenReturn(List.of(root, child));

        List<OrgUnitEntity> units = repository.findByIds(List.of(ROOT_ID, CHILD_ID));

        assertThat(units).hasSize(2);
        assertThat(units.get(0).getPath()).isEqualTo(ROOT_PATH);
        assertThat(units.get(1).getPath()).isEqualTo(ROOT_PATH + "/CSE01");
        verify(orgUnitMapper).selectBatchIds(List.of(ROOT_ID, CHILD_ID));
    }

    @Test
    void findByIdsReturnsEmptyWithoutQueryForEmptyInput() {
        assertThat(repository.findByIds(List.of())).isEmpty();
        verifyNoInteractions(orgUnitMapper);
    }

    @Test
    void findSubtreeByPathsMatchesAnyPrefixInSingleQuery() {
        OrgUnitEntity child = newUnit(CHILD_ID, "CSE01", "计算机科学与技术系", ROOT_PATH + "/CSE01");
        when(orgUnitMapper.selectList(ArgumentMatchers.<Wrapper<OrgUnitEntity>>any()))
                .thenReturn(List.of(child));

        List<OrgUnitEntity> subtree = repository.findSubtreeByPaths(List.of(ROOT_PATH, "/UNIV/MATH"));

        assertThat(subtree).hasSize(1);
        assertThat(subtree.get(0).getPath()).isEqualTo(ROOT_PATH + "/CSE01");
        verify(orgUnitMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<OrgUnitEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("path LIKE");
        assertThat(wrapper.getSqlSegment()).contains("OR");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(ROOT_PATH + "%", "/UNIV/MATH%");
    }

    @Test
    void findSubtreeByPathsReturnsEmptyWithoutQueryForEmptyInput() {
        assertThat(repository.findSubtreeByPaths(List.of())).isEmpty();
        verifyNoInteractions(orgUnitMapper);
    }
}
