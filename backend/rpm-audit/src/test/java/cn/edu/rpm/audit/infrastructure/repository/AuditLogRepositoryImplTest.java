package cn.edu.rpm.audit.infrastructure.repository;

import cn.edu.rpm.audit.infrastructure.entity.AuditLogEntity;
import cn.edu.rpm.audit.infrastructure.mapper.AuditLogMapper;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuditLogRepositoryImpl} 纯单元测试（T-AUD-001 先行测试，TC-IAM-020 应用层保障）。
 *
 * <p>不依赖 Spring / 数据库：用 MyBatis-Plus {@link TableInfoHelper} 完成真实注解解析，
 * 断言审计 PO 与 V01 {@code audit_log} 列逐一对齐、仓储只暴露追加与查询能力；
 * 数据库触发器的 {@code UPDATE/DELETE} 拒绝属于集成测试，由下游业务测试阶段执行。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogRepositoryImplTest {

    private static final String LOG_ID = "0123456789abcdef0123456789abcdef";
    private static final String TRACE_ID = "trace-2026-0001";
    private static final String ACTOR_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OBJECT_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String BEFORE_DIGEST = "a".repeat(64);
    private static final String AFTER_DIGEST = "b".repeat(64);

    @Mock
    private AuditLogMapper auditLogMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<AuditLogEntity>> wrapperCaptor;

    private AuditLogRepositoryImpl repository;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuditLogEntity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new AuditLogRepositoryImpl(auditLogMapper);
    }

    private static AuditLogEntity newAuditLog() {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setId(LOG_ID);
        entry.setTraceId(TRACE_ID);
        entry.setActorId(ACTOR_ID);
        entry.setOnBehalfOf(null);
        entry.setAction("APPLICATION_SUBMIT");
        entry.setObjectType("APPLICATION");
        entry.setObjectId(OBJECT_ID);
        entry.setObjectVersion(3);
        entry.setBeforeDigest(BEFORE_DIGEST);
        entry.setAfterDigest(AFTER_DIGEST);
        entry.setReason("提交申报");
        entry.setIp("10.0.0.8");
        entry.setUserAgent("Mozilla/5.0");
        entry.setOccurredAt(OffsetDateTime.of(2026, 11, 17, 10, 0, 0, 0, ZoneOffset.ofHours(8)));
        entry.setCreatedAt(OffsetDateTime.of(2026, 11, 17, 10, 0, 0, 0, ZoneOffset.ofHours(8)));
        return entry;
    }

    @Test
    void insertDelegatesToMapper() {
        AuditLogEntity entry = newAuditLog();

        repository.insert(entry);

        verify(auditLogMapper).insert(same(entry));
    }

    @Test
    void findByTraceIdFiltersTraceColumnAndReturnsRows() {
        when(auditLogMapper.selectList(ArgumentMatchers.<Wrapper<AuditLogEntity>>any()))
                .thenReturn(List.of(newAuditLog()));

        List<AuditLogEntity> result = repository.findByTraceId(TRACE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("APPLICATION_SUBMIT");
        assertThat(result.get(0).getObjectVersion()).isEqualTo(3);
        assertThat(result.get(0).getBeforeDigest()).isEqualTo(BEFORE_DIGEST);
        assertThat(result.get(0).getAfterDigest()).isEqualTo(AFTER_DIGEST);
        verify(auditLogMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<AuditLogEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("trace_id =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(TRACE_ID);
    }

    @Test
    void findByTraceIdReturnsEmptyListWhenNoRows() {
        when(auditLogMapper.selectList(ArgumentMatchers.<Wrapper<AuditLogEntity>>any()))
                .thenReturn(List.of());

        assertThat(repository.findByTraceId("trace-without-audit")).isEmpty();
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        when(auditLogMapper.selectById("no-such-log")).thenReturn(null);

        assertThat(repository.findById("no-such-log")).isEmpty();
    }

    @Test
    void repositoryExposesNoUpdateOrDeleteOperation() {
        Set<String> methodNames = java.util.Arrays.stream(AuditLogRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertThat(methodNames).doesNotContain("update", "updateById", "delete", "deleteById", "remove");
    }

    @Test
    void entityMapsEveryAuditLogColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(AuditLogEntity.class);
        Set<String> columns = tableInfo.getFieldList().stream()
                .map(TableFieldInfo::getColumn)
                .collect(Collectors.toSet());

        assertThat(tableInfo.getTableName()).isEqualTo("audit_log");
        assertThat(columns).containsExactlyInAnyOrder(
                "trace_id", "actor_id", "on_behalf_of", "action", "object_type", "object_id",
                "object_version", "before_digest", "after_digest", "reason", "ip", "user_agent",
                "occurred_at", "created_at");
    }
}
