package cn.edu.rpm.application.infrastructure.repository;

import cn.edu.rpm.application.infrastructure.entity.ApplicationMemberEntity;
import cn.edu.rpm.application.infrastructure.mapper.ApplicationMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code application_member} 申报成员仓储实现（T-APP-005）。
 *
 * <p>基于 MyBatis-Plus {@link ApplicationMemberMapper} 的薄委托层；批量插入按
 * RULE-DB3 每批 ≤ 500 条切分，主键沿用 ASSIGN_UUID 的 32 位十六进制格式
 * （{@link IdWorker#get32UUID()}）。</p>
 */
@Repository
public class ApplicationMemberRepositoryImpl implements ApplicationMemberRepository {

    /** RULE-DB3：批量写入每批上限。 */
    private static final int MAX_BATCH_SIZE = 500;

    private final ApplicationMemberMapper applicationMemberMapper;

    public ApplicationMemberRepositoryImpl(ApplicationMemberMapper applicationMemberMapper) {
        this.applicationMemberMapper = applicationMemberMapper;
    }

    @Override
    public void insertBatch(List<ApplicationMemberEntity> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        for (ApplicationMemberEntity member : members) {
            if (member.getId() == null || member.getId().isBlank()) {
                member.setId(IdWorker.get32UUID());
            }
        }
        for (int from = 0; from < members.size(); from += MAX_BATCH_SIZE) {
            int to = Math.min(from + MAX_BATCH_SIZE, members.size());
            applicationMemberMapper.insertBatch(members.subList(from, to));
        }
    }

    @Override
    public List<ApplicationMemberEntity> findByVersionId(String applicationVersionId) {
        return applicationMemberMapper.selectList(
                new LambdaQueryWrapper<ApplicationMemberEntity>()
                        .eq(ApplicationMemberEntity::getApplicationVersionId, applicationVersionId)
                        .orderByAsc(ApplicationMemberEntity::getSortOrder));
    }
}
