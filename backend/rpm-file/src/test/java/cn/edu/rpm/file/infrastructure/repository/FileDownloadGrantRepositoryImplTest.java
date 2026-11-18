package cn.edu.rpm.file.infrastructure.repository;

import cn.edu.rpm.file.domain.model.FileDownloadGrant;
import cn.edu.rpm.file.infrastructure.entity.FileDownloadGrantEntity;
import cn.edu.rpm.file.infrastructure.mapper.FileDownloadGrantMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileDownloadGrantRepositoryImpl} 纯单元测试（T-FILE-005 先行测试；
 * TC-IAM-017 / TC-IAM-018）。
 *
 * <p>断言签发落库字段、按 token 查询、批量撤销使用单条参数化 UPDATE，
 * 实体列与 V06 DDL 逐列对齐。伪造 token 查询由唯一索引 + token 校验兜底。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileDownloadGrantRepositoryImplTest {

    private static final String GRANT_ID = "00112233445566778899aabbccddeeff";
    private static final UUID GRANT_UUID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final String FILE_ID = "11111111111111111111111111111111";
    private static final UUID FILE_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String USER_ID = "22222222222222222222222222222222";
    private static final UUID USER_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final Instant T0 = Instant.parse("2026-11-17T06:00:00Z");
    private static final Instant EXPIRES = T0.plusSeconds(300);

    @Mock
    private FileDownloadGrantMapper fileDownloadGrantMapper;

    @Captor
    private ArgumentCaptor<FileDownloadGrantEntity> entityCaptor;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<FileDownloadGrantEntity>> queryCaptor;

    @Captor
    private ArgumentCaptor<LambdaUpdateWrapper<FileDownloadGrantEntity>> updateCaptor;

    private FileDownloadGrantRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FileDownloadGrantEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new FileDownloadGrantRepositoryImpl(fileDownloadGrantMapper);
    }

    private static FileDownloadGrantEntity grantEntity() {
        FileDownloadGrantEntity entity = new FileDownloadGrantEntity();
        entity.setId(GRANT_ID);
        entity.setFileId(FILE_ID);
        entity.setGrantedTo(USER_ID);
        entity.setGrantToken(TOKEN);
        entity.setCreatedAt(OffsetDateTime.of(2026, 11, 17, 14, 0, 0, 0, ZoneOffset.ofHours(8)));
        entity.setExpiresAt(OffsetDateTime.of(2026, 11, 17, 14, 5, 0, 0, ZoneOffset.ofHours(8)));
        return entity;
    }

    @Test
    void saveInsertsGrantWithExactExpiryAndCompactUuids() {
        FileDownloadGrant grant = FileDownloadGrant.issue(FILE_UUID, USER_UUID, T0, EXPIRES);
        grant.revoke(T0.plusSeconds(10));

        repository.save(grant);

        verify(fileDownloadGrantMapper).insert(entityCaptor.capture());
        FileDownloadGrantEntity entity = entityCaptor.getValue();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getFileId()).isEqualTo(FILE_ID);
        assertThat(entity.getGrantedTo()).isEqualTo(USER_ID);
        assertThat(entity.getGrantToken()).isEqualTo(grant.getToken());
        assertThat(entity.getCreatedAt().toInstant()).isEqualTo(T0);
        assertThat(entity.getExpiresAt().toInstant()).isEqualTo(EXPIRES);
        assertThat(entity.getRevokedAt().toInstant()).isEqualTo(T0.plusSeconds(10));
        assertThat(java.time.Duration.between(T0, entity.getExpiresAt().toInstant()))
                .isEqualTo(java.time.Duration.ofSeconds(300));
    }

    @Test
    void saveRejectsNullGrant() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("下载签名不能为空");
    }

    @Test
    void findByTokenFiltersGrantTokenColumnAndReturnsDomain() {
        when(fileDownloadGrantMapper.selectOne(ArgumentMatchers.any(LambdaQueryWrapper.class)))
                .thenReturn(grantEntity());

        Optional<FileDownloadGrant> result = repository.findByToken(TOKEN);

        assertThat(result).isPresent();
        FileDownloadGrant grant = result.get();
        assertThat(grant.getId()).isEqualTo(GRANT_UUID);
        assertThat(grant.getFileId()).isEqualTo(FILE_UUID);
        assertThat(grant.getGrantedTo()).isEqualTo(USER_UUID);
        assertThat(grant.getToken()).isEqualTo(TOKEN);
        assertThat(grant.getExpiresAt()).isEqualTo(EXPIRES);
        assertThat(grant.getRevokedAt()).isNull();
        assertThat(grant.isUsableAt(T0.plusSeconds(299))).isTrue();
        verify(fileDownloadGrantMapper).selectOne(queryCaptor.capture());
        LambdaQueryWrapper<FileDownloadGrantEntity> wrapper = queryCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("grant_token =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(TOKEN);
    }

    @Test
    void findByTokenReturnsEmptyForBlankOrUnknownToken() {
        assertThat(repository.findByToken(" ")).isEmpty();
        when(fileDownloadGrantMapper.selectOne(ArgumentMatchers.any(LambdaQueryWrapper.class)))
                .thenReturn(null);

        assertThat(repository.findByToken("ffffffffffffffffffffffffffffffff")).isEmpty();
    }

    @Test
    void revokeAllForFileIssuesSingleParameterizedUpdate() {
        when(fileDownloadGrantMapper.update(ArgumentMatchers.isNull(),
                ArgumentMatchers.<Wrapper<FileDownloadGrantEntity>>any())).thenReturn(2);

        int revoked = repository.revokeAllForFile(FILE_UUID, T0.plusSeconds(10));

        assertThat(revoked).isEqualTo(2);
        verify(fileDownloadGrantMapper).update(ArgumentMatchers.isNull(), updateCaptor.capture());
        LambdaUpdateWrapper<FileDownloadGrantEntity> wrapper = updateCaptor.getValue();
        assertThat(wrapper.getSqlSet()).contains("revoked_at");
        assertThat(wrapper.getSqlSegment()).contains("file_id =").contains("revoked_at IS NULL");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(FILE_ID);
    }

    @Test
    void revokeAllForFileRejectsNullArguments() {
        assertThatThrownBy(() -> repository.revokeAllForFile(null, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.revokeAllForFile(FILE_UUID, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void entityMapsEveryFileDownloadGrantColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(FileDownloadGrantEntity.class);

        assertThat(tableInfo.getTableName()).isEqualTo("file_download_grant");
        assertThat(tableInfo.getFieldList())
                .extracting(TableFieldInfo::getColumn)
                .containsExactlyInAnyOrder(
                        "file_id", "granted_to", "grant_token", "expires_at", "revoked_at", "created_at");
    }
}
