package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.iam.domain.model.TempGrantView;
import cn.edu.rpm.iam.infrastructure.entity.IamTempGrantEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamTempGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link TempGrantQueryAdapter} 纯单元测试（Batch-4 / T-IAM-009）。
 *
 * <p>锚点：字段逐一投影（资源类型/资源 ID/权限码/有效期），有效期不在适配器内裁剪。</p>
 */
@ExtendWith(MockitoExtension.class)
class TempGrantQueryAdapterTest {

    private static final String GRANTEE_ID = "0123456789abcdef0123456789abcdef";
    private static final String PROJECT_ID = "aa111111111111111111111111111111";
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-17T10:00:00+08:00");
    private static final OffsetDateTime VALID_FROM = OffsetDateTime.parse("2026-11-17T09:00:00+08:00");
    private static final OffsetDateTime VALID_TO = OffsetDateTime.parse("2026-11-17T18:00:00+08:00");

    @Mock
    private IamTempGrantRepository tempGrantRepository;

    private TempGrantQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TempGrantQueryAdapter(tempGrantRepository);
    }

    @Test
    void mapsGrantToDomainView() {
        IamTempGrantEntity entity = new IamTempGrantEntity();
        entity.setGranteeId(GRANTEE_ID);
        entity.setResourceType("PROJECT");
        entity.setResourceId(PROJECT_ID);
        entity.setPermissionCode("project:view");
        entity.setValidFrom(VALID_FROM);
        entity.setValidTo(VALID_TO);
        entity.setStatus(IamTempGrantEntity.STATUS_ACTIVE);
        when(tempGrantRepository.findValidByGrantee(GRANTEE_ID, AT)).thenReturn(List.of(entity));

        List<TempGrantView> grants = adapter.findValidByGrantee(GRANTEE_ID, AT);

        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).resourceType()).isEqualTo("PROJECT");
        assertThat(grants.get(0).resourceId()).isEqualTo(PROJECT_ID);
        assertThat(grants.get(0).permissionCode()).isEqualTo("project:view");
        assertThat(grants.get(0).validFrom()).isEqualTo(VALID_FROM);
        assertThat(grants.get(0).validTo()).isEqualTo(VALID_TO);
    }

    @Test
    void emptyResultMapsToEmptyList() {
        when(tempGrantRepository.findValidByGrantee(GRANTEE_ID, AT)).thenReturn(List.of());

        assertThat(adapter.findValidByGrantee(GRANTEE_ID, AT)).isEmpty();
    }
}
