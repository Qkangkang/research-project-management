package cn.edu.rpm.file.infrastructure.repository;

import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FileStatus;
import cn.edu.rpm.file.domain.model.ScanStatus;
import cn.edu.rpm.file.infrastructure.entity.FileObjectEntity;
import cn.edu.rpm.file.infrastructure.mapper.FileObjectMapper;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileObjectRepositoryImpl} 纯单元测试（T-FILE-001 先行测试）。
 *
 * <p>断言 file_object 元数据插入、按 SHA-256 去重查询、按 status 筛选与乐观锁更新；
 * 实体列与 V01 DDL 逐列对齐（不继承 BaseEntity，没有 created_by/updated_by）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileObjectRepositoryImplTest {

    private static final String FILE_ID = "0123456789abcdef0123456789abcdef";
    private static final UUID FILE_UUID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final UUID UPLOADER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SHA256 = "a".repeat(64);
    private static final String OBJECT_KEY = "2026/11/0123456789abcdef";

    @Mock
    private FileObjectMapper fileObjectMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<FileObjectEntity>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<FileObjectEntity> entityCaptor;

    private FileObjectRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FileObjectEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new FileObjectRepositoryImpl(fileObjectMapper);
    }

    private static FileObjectEntity availableEntity() {
        FileObjectEntity entity = new FileObjectEntity();
        entity.setId(FILE_ID);
        entity.setBucket("rpm-files");
        entity.setObjectKey(OBJECT_KEY);
        entity.setOriginalName("申报书.pdf");
        entity.setMimeType("application/pdf");
        entity.setSizeBytes(1024L);
        entity.setSha256(SHA256);
        entity.setStatus("AVAILABLE");
        entity.setScanStatus("PASSED");
        entity.setUploadedBy(UPLOADER_ID.toString().replace("-", ""));
        entity.setCreatedAt(OffsetDateTime.of(2026, 11, 17, 9, 0, 0, 0, ZoneOffset.ofHours(8)));
        entity.setVersion(2);
        return entity;
    }

    private static FileObject newDomain() {
        return FileObject.newUpload("rpm-files", OBJECT_KEY, "申报书.pdf", "application/pdf",
                1024L, SHA256, UPLOADER_ID);
    }

    @Test
    void insertReturnsGeneratedIdAndProjectsEveryColumn() {
        java.util.concurrent.atomic.AtomicReference<String> idPassedToInsert =
                new java.util.concurrent.atomic.AtomicReference<>("pre-set");
        when(fileObjectMapper.insert(ArgumentMatchers.any(FileObjectEntity.class))).thenAnswer(invocation -> {
            FileObjectEntity entity = invocation.getArgument(0);
            idPassedToInsert.set(entity.getId());
            entity.setId(FILE_ID);
            return 1;
        });

        String generatedId = repository.insert(newDomain());

        assertThat(generatedId).isEqualTo(FILE_ID);
        assertThat(idPassedToInsert.get()).isNull();
        verify(fileObjectMapper).insert(entityCaptor.capture());
        FileObjectEntity entity = entityCaptor.getValue();
        assertThat(entity.getId()).isEqualTo(FILE_ID);
        assertThat(entity.getBucket()).isEqualTo("rpm-files");
        assertThat(entity.getObjectKey()).isEqualTo(OBJECT_KEY);
        assertThat(entity.getOriginalName()).isEqualTo("申报书.pdf");
        assertThat(entity.getMimeType()).isEqualTo("application/pdf");
        assertThat(entity.getSizeBytes()).isEqualTo(1024L);
        assertThat(entity.getSha256()).isEqualTo(SHA256);
        assertThat(entity.getStatus()).isEqualTo("UPLOADING");
        assertThat(entity.getScanStatus()).isEqualTo("PENDING");
        assertThat(entity.getUploadedBy()).isEqualTo("11111111111111111111111111111111");
    }

    @Test
    void findByIdReturnsDomainObjectWithEnumStates() {
        when(fileObjectMapper.selectById(FILE_ID)).thenReturn(availableEntity());

        Optional<FileObject> result = repository.findById(FILE_UUID);

        assertThat(result).isPresent();
        FileObject file = result.get();
        assertThat(file.getId()).isEqualTo(FILE_UUID);
        assertThat(file.getOriginalName()).isEqualTo("申报书.pdf");
        assertThat(file.getStatus()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(file.getScanStatus()).isEqualTo(ScanStatus.PASSED);
        assertThat(file.getVersion()).isEqualTo(2);
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        when(fileObjectMapper.selectById("99999999999999999999999999999999")).thenReturn(null);

        assertThat(repository.findById(UUID.fromString("99999999-9999-9999-9999-999999999999"))).isEmpty();
    }

    @Test
    void findBySha256FiltersShaColumnAndReturnsFirstMatch() {
        when(fileObjectMapper.selectList(ArgumentMatchers.<Wrapper<FileObjectEntity>>any()))
                .thenReturn(List.of(availableEntity()));

        Optional<FileObject> result = repository.findBySha256(SHA256);

        assertThat(result).isPresent();
        assertThat(result.get().getSha256()).isEqualTo(SHA256);
        verify(fileObjectMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<FileObjectEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("sha256 =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(SHA256);
    }

    @Test
    void findBySha256ReturnsEmptyWhenContentNotUploadedBefore() {
        when(fileObjectMapper.selectList(ArgumentMatchers.<Wrapper<FileObjectEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findBySha256("b".repeat(64))).isEmpty();
    }

    @Test
    void findByStatusFiltersStatusColumn() {
        when(fileObjectMapper.selectList(ArgumentMatchers.<Wrapper<FileObjectEntity>>any()))
                .thenReturn(List.of(availableEntity()));

        List<FileObject> result = repository.findByStatus(FileStatus.AVAILABLE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(FileStatus.AVAILABLE);
        verify(fileObjectMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<FileObjectEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("status =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("AVAILABLE");
    }

    @Test
    void updateReturnsZeroOnOptimisticLockConflict() {
        FileObject file = FileObject.rehydrate(FILE_UUID, "rpm-files", OBJECT_KEY, "申报书.pdf",
                "application/pdf", 1024L, SHA256, FileStatus.AVAILABLE, ScanStatus.PASSED,
                UPLOADER_ID, null, 2);
        when(fileObjectMapper.updateById(ArgumentMatchers.any(FileObjectEntity.class))).thenReturn(0);

        assertThat(repository.update(file)).isZero();
    }

    @Test
    void entityMapsEveryFileObjectColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(FileObjectEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("file_object");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "bucket", "object_key", "original_name", "mime_type", "size_bytes", "sha256",
                        "status", "scan_status", "uploaded_by", "created_at", "updated_at", "version");
    }
}
