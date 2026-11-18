package cn.edu.rpm.iam.infrastructure.adapter;

import cn.edu.rpm.iam.domain.model.CoiDeclarationStatus;
import cn.edu.rpm.iam.domain.model.CoiDeclarationView;
import cn.edu.rpm.iam.infrastructure.entity.CoiDeclarationEntity;
import cn.edu.rpm.iam.infrastructure.repository.CoiDeclarationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CoiDeclarationQueryAdapter} 纯单元测试（T-IAM-011 数据依赖先行测试）。
 *
 * <p>锚点：把 {@code coi_declaration} PO 投影为领域只读视图，uuid 文本解析为 {@link UUID}；
 * 无声明返回空列表。</p>
 */
@ExtendWith(MockitoExtension.class)
class CoiDeclarationQueryAdapterTest {

    private static final String BIZ_ID_TEXT = "99999999999999999999999999999999";
    private static final String EXPERT_ID_TEXT = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String APPLICANT_ID_TEXT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private CoiDeclarationRepository coiDeclarationRepository;

    private CoiDeclarationQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CoiDeclarationQueryAdapter(coiDeclarationRepository);
    }

    @Test
    void mapsEntityToDomainView() {
        CoiDeclarationEntity entity = new CoiDeclarationEntity();
        entity.setBizType("REVIEW");
        entity.setBizId(BIZ_ID_TEXT);
        entity.setExpertId(EXPERT_ID_TEXT);
        entity.setRelatedUserId(APPLICANT_ID_TEXT);
        entity.setRelationType("DEPT");
        entity.setStatus(CoiDeclarationStatus.CONFIRMED);
        when(coiDeclarationRepository.findByBizAndExpert("REVIEW", BIZ_ID_TEXT, EXPERT_ID_TEXT))
                .thenReturn(List.of(entity));

        List<CoiDeclarationView> views = adapter.findByBizAndExpert("REVIEW", BIZ_ID_TEXT, EXPERT_ID_TEXT);

        assertThat(views).containsExactly(new CoiDeclarationView(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), CoiDeclarationStatus.CONFIRMED));
        verify(coiDeclarationRepository).findByBizAndExpert("REVIEW", BIZ_ID_TEXT, EXPERT_ID_TEXT);
    }

    @Test
    void returnsEmptyWhenNoDeclaration() {
        when(coiDeclarationRepository.findByBizAndExpert("REVIEW", BIZ_ID_TEXT, EXPERT_ID_TEXT))
                .thenReturn(List.of());

        assertThat(adapter.findByBizAndExpert("REVIEW", BIZ_ID_TEXT, EXPERT_ID_TEXT)).isEmpty();
    }
}
