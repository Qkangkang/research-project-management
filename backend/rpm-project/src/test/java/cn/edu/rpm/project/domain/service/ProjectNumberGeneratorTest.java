package cn.edu.rpm.project.domain.service;

import cn.edu.rpm.common.domain.service.NumberGenerator;
import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.project.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectNumberGenerator} 纯单元测试（T-PRJ-010；TC-APP-040 / TC-APP-041）。
 */
@ExtendWith(MockitoExtension.class)
class ProjectNumberGeneratorTest {

    @Mock
    private NumberGenerator numberGenerator;

    @Mock
    private ProjectRepository projectRepository;

    private ProjectNumberGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ProjectNumberGenerator(numberGenerator, projectRepository);
    }

    @Test
    void generatesUniqueProjectNumberOnFirstAttempt() {
        when(numberGenerator.next(ProjectNumberGenerator.RULE_PROJECT_NO))
                .thenReturn("KY-2026-000456");
        when(projectRepository.existsByProjectNo("KY-2026-000456")).thenReturn(false);

        assertThat(generator.next()).isEqualTo("KY-2026-000456");
        verify(numberGenerator, times(1)).next(ProjectNumberGenerator.RULE_PROJECT_NO);
    }

    @Test
    void retriesThreeTimesAndSucceedsOnFourthAttempt() {
        when(numberGenerator.next(ProjectNumberGenerator.RULE_PROJECT_NO))
                .thenReturn("KY-2026-000001", "KY-2026-000002", "KY-2026-000003", "KY-2026-000004");
        when(projectRepository.existsByProjectNo("KY-2026-000001")).thenReturn(true);
        when(projectRepository.existsByProjectNo("KY-2026-000002")).thenReturn(true);
        when(projectRepository.existsByProjectNo("KY-2026-000003")).thenReturn(true);
        when(projectRepository.existsByProjectNo("KY-2026-000004")).thenReturn(false);

        assertThat(generator.next()).isEqualTo("KY-2026-000004");
        verify(numberGenerator, times(4)).next(ProjectNumberGenerator.RULE_PROJECT_NO);
    }

    @Test
    void throwsProj003WhenAllAttemptsConflict() {
        when(numberGenerator.next(ProjectNumberGenerator.RULE_PROJECT_NO))
                .thenReturn("KY-2026-000001", "KY-2026-000002", "KY-2026-000003", "KY-2026-000004");
        when(projectRepository.existsByProjectNo(
                org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        assertThatThrownBy(() -> generator.next())
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROJ_003));
    }
}
