package cn.edu.rpm.file.infrastructure.repository;

import cn.edu.rpm.file.domain.model.FileAccessLevel;
import cn.edu.rpm.file.domain.model.FileRef;
import cn.edu.rpm.file.infrastructure.entity.FileRefEntity;
import cn.edu.rpm.file.infrastructure.mapper.FileRefMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileRefRepositoryImpl} 纯单元测试（T-FILE-002 先行测试）。
 *
 * <p>断言文件业务引用插入、按 {@code biz_type + biz_id} 查询、按文件查引用与解除引用；
 * 解除引用后文件不再被业务引用（成为孤立文件，由保留策略清理）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileRefRepositoryImplTest {

    private static final String REF_ID = "0123456789abcdef0123456789abcdef";
    private static final UUID REF_UUID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final UUID FILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BIZ_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CREATED_BY = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private FileRefMapper fileRefMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<FileRefEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<FileRefEntity> entityCaptor;

    private FileRefRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FileRefEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new FileRefRepositoryImpl(fileRefMapper);
    }

    private static FileRefEntity entity() {
        FileRefEntity entity = new FileRefEntity();
        entity.setId(REF_ID);
        entity.setFileId(FILE_ID.toString().replace("-", ""));
        entity.setBizType("APPLICATION");
        entity.setBizId(BIZ_ID.toString().replace("-", ""));
        entity.setPurpose("APPLICATION_ATTACHMENT");
        entity.setAccessLevel("PROJECT");
        entity.setCreatedBy(CREATED_BY.toString().replace("-", ""));
        entity.setCreatedAt(OffsetDateTime.of(2026, 11, 17, 9, 30, 0, 0, ZoneOffset.ofHours(8)));
        return entity;
    }

    private static FileRef newRef() {
        return new FileRef(null, FILE_ID, "APPLICATION", BIZ_ID, "APPLICATION_ATTACHMENT",
                FileAccessLevel.PROJECT, CREATED_BY, null);
    }

    @Test
    void insertReturnsGeneratedIdAndProjectsEveryColumn() {
        java.util.concurrent.atomic.AtomicReference<String> idPassedToInsert =
                new java.util.concurrent.atomic.AtomicReference<>("pre-set");
        when(fileRefMapper.insert(ArgumentMatchers.any(FileRefEntity.class))).thenAnswer(invocation -> {
            FileRefEntity inserted = invocation.getArgument(0);
            idPassedToInsert.set(inserted.getId());
            inserted.setId(REF_ID);
            return 1;
        });

        String generatedId = repository.insert(newRef());

        assertThat(generatedId).isEqualTo(REF_ID);
        assertThat(idPassedToInsert.get()).isNull();
        verify(fileRefMapper).insert(entityCaptor.capture());
        FileRefEntity entity = entityCaptor.getValue();
        assertThat(entity.getId()).isEqualTo(REF_ID);
        assertThat(entity.getFileId()).isEqualTo("11111111111111111111111111111111");
        assertThat(entity.getBizType()).isEqualTo("APPLICATION");
        assertThat(entity.getBizId()).isEqualTo("22222222222222222222222222222222");
        assertThat(entity.getPurpose()).isEqualTo("APPLICATION_ATTACHMENT");
        assertThat(entity.getAccessLevel()).isEqualTo("PROJECT");
        assertThat(entity.getCreatedBy()).isEqualTo("33333333333333333333333333333333");
    }

    @Test
    void findByBizTypeAndBizIdFiltersBothColumns() {
        when(fileRefMapper.selectList(ArgumentMatchers.<Wrapper<FileRefEntity>>any()))
                .thenReturn(List.of(entity()));

        List<FileRef> result = repository.findByBizTypeAndBizId("APPLICATION", BIZ_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileId()).isEqualTo(FILE_ID);
        assertThat(result.get(0).accessLevel()).isEqualTo(FileAccessLevel.PROJECT);
        verify(fileRefMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<FileRefEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("biz_type =").contains("biz_id =");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("APPLICATION", "22222222222222222222222222222222");
    }

    @Test
    void findByBizReturnsEmptyWhenBusinessObjectHasNoFiles() {
        when(fileRefMapper.selectList(ArgumentMatchers.<Wrapper<FileRefEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findByBizTypeAndBizId("EXPENSE_REQUEST", BIZ_ID)).isEmpty();
    }

    @Test
    void findByFileIdFiltersFileColumn() {
        when(fileRefMapper.selectList(ArgumentMatchers.<Wrapper<FileRefEntity>>any()))
                .thenReturn(List.of(entity()));

        List<FileRef> result = repository.findByFileId(FILE_ID);

        assertThat(result).hasSize(1);
        verify(fileRefMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("file_id =");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("11111111111111111111111111111111");
    }

    @Test
    void deleteByIdReturnsAffectedRows() {
        when(fileRefMapper.deleteById(REF_ID)).thenReturn(1);

        assertThat(repository.deleteById(REF_UUID)).isEqualTo(1);
    }

    @Test
    void deleteByIdReturnsZeroWhenReferenceAlreadyGone() {
        when(fileRefMapper.deleteById(REF_ID)).thenReturn(0);

        assertThat(repository.deleteById(REF_UUID)).isZero();
    }

    @Test
    void entityMapsEveryFileRefColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(FileRefEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("file_ref");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "file_id", "biz_type", "biz_id", "purpose", "access_level",
                        "created_by", "created_at");
    }
}
