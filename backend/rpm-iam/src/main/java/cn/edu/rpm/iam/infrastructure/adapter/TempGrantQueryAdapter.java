package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.iam.domain.model.TempGrantView;
import cn.edu.rpm.iam.domain.port.TempGrantQueryPort;
import cn.edu.rpm.iam.infrastructure.entity.IamTempGrantEntity;
import cn.edu.rpm.iam.infrastructure.repository.IamTempGrantRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@link TempGrantQueryPort} 的持久化适配器（Batch-4 / T-IAM-009）。
 *
 * <p>查询侧已按 {@code status=ACTIVE} 与有效期过滤；适配器只做字段投影，
 * 有效期最终仍由领域层 {@code GrantValidityGuard} 复核。</p>
 */
@Repository
public class TempGrantQueryAdapter implements TempGrantQueryPort {

    private final IamTempGrantRepository tempGrantRepository;

    public TempGrantQueryAdapter(IamTempGrantRepository tempGrantRepository) {
        this.tempGrantRepository = tempGrantRepository;
    }

    @Override
    public List<TempGrantView> findValidByGrantee(String granteeId, OffsetDateTime at) {
        return tempGrantRepository.findValidByGrantee(granteeId, at).stream()
                .map(TempGrantQueryAdapter::toView)
                .toList();
    }

    private static TempGrantView toView(IamTempGrantEntity entity) {
        return new TempGrantView(entity.getResourceType(), entity.getResourceId(),
                entity.getPermissionCode(), entity.getValidFrom(), entity.getValidTo());
    }
}
