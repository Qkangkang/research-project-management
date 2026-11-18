package cn.edu.rpm.project.infrastructure.repository;

import cn.edu.rpm.project.infrastructure.entity.ChangeRequestEntity;
import cn.edu.rpm.project.infrastructure.mapper.ChangeRequestMapper;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChangeRequestRepositoryImpl} 纯单元测试（T-PRJ-007 先行测试）。
 */
@ExtendWith(MockitoExtension.class)
class ChangeRequestRepositoryImplTest {

    private static final String PROJECT_ID = "8d7c6b5a4e3f4a2b9c1d0e2f3a4b5c6d";

    @Mock
    private ChangeRequestMapper changeRequestMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ChangeRequestEntity>> wrapperCaptor;

    private ChangeRequestRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChangeRequestEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new ChangeRequestRepositoryImpl(changeRequestMapper);
    }

    @Test
    void insertKeepsChangeTypeAndDraftStatus() {
        ChangeRequestEntity change = new ChangeRequestEntity();
        change.setProjectId(PROJECT_ID);
        change.setChangeType(ChangeRequestEntity.TYPE_GOAL);
        change.setStatus(ChangeRequestEntity.STATUS_DRAFT);

        repository.insert(change);

        verify(changeRequestMapper).insert(change);
        assertThat(change.getChangeType()).isEqualTo(ChangeRequestEntity.TYPE_GOAL);
    }

    @Test
    void findByProjectIdAndStatusFiltersBothOrderedDesc() {
        when(changeRequestMapper.selectList(ArgumentMatchers.<Wrapper<ChangeRequestEntity>>any()))
                .thenReturn(List.of());

        repository.findByProjectIdAndStatus(PROJECT_ID, ChangeRequestEntity.STATUS_IN_REVIEW);

        verify(changeRequestMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("project_id =", "status =")
                .containsIgnoringCase("order by created_at desc");
    }

    @Test
    void selectForUpdateDelegatesToLockingStatement() {
        when(changeRequestMapper.selectByIdForUpdate("cccccccccccccccccccccccccccccccc"))
                .thenReturn(new ChangeRequestEntity());

        assertThat(repository.selectForUpdate("cccccccccccccccccccccccccccccccc")).isPresent();
        verify(changeRequestMapper).selectByIdForUpdate("cccccccccccccccccccccccccccccccc");
    }

    @Test
    void mapperXmlCarriesForUpdateClause() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("mapper/ChangeRequestMapper.xml")) {
            assertThat(in).isNotNull();
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).contains("selectByIdForUpdate");
            assertThat(xml).containsIgnoringCase("for update");
        }
    }

    @Test
    void submitForReviewRequiresDraftOrReturned() {
        ChangeRequestEntity change = new ChangeRequestEntity();
        change.setStatus(ChangeRequestEntity.STATUS_RETURNED);

        change.submitForReview("version-1");

        assertThat(change.getStatus()).isEqualTo(ChangeRequestEntity.STATUS_IN_REVIEW);
        assertThat(change.getCurrentVersionId()).isEqualTo("version-1");
    }
}
