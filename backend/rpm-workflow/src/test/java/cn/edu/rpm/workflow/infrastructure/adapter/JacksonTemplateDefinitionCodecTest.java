package cn.edu.rpm.workflow.infrastructure.adapter;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.workflow.domain.model.TemplateDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JacksonTemplateDefinitionCodec} 纯单元测试（T-WF-013 先行测试）。
 */
class JacksonTemplateDefinitionCodecTest {

    private JacksonTemplateDefinitionCodec codec;

    @BeforeEach
    void setUp() {
        codec = new JacksonTemplateDefinitionCodec(new ObjectMapper());
    }

    @Test
    void parsesTwoNodeDefinition() {
        TemplateDefinition definition = codec.parse("""
                {"nodes":[
                  {"key":"formalReview","name":"形式审查","assignee":"role:research_admin","returnTo":"start"},
                  {"key":"decision","name":"决策","assignee":"role:leader","returnTo":"formalReview"}
                ]}
                """);

        assertThat(definition.nodes()).hasSize(2);
        assertThat(definition.nodes().get(0).key()).isEqualTo("formalReview");
        assertThat(definition.nodes().get(0).assignee()).isEqualTo("role:research_admin");
        assertThat(definition.nodes().get(1).returnTo()).isEqualTo("formalReview");
    }

    @Test
    void keepsBlankAssigneeForPublishServiceToValidate() {
        TemplateDefinition definition = codec.parse(
                "{\"nodes\":[{\"key\":\"first\",\"name\":\"首节点\",\"assignee\":\"\",\"returnTo\":\"start\"}]}");
        assertThat(definition.nodes().get(0).assignee()).isEmpty();
    }

    @Test
    void rejectsDuplicateNodeKey() {
        assertThatThrownBy(() -> codec.parse("""
                {"nodes":[
                  {"key":"same","name":"A","assignee":"x","returnTo":"start"},
                  {"key":"same","name":"B","assignee":"y","returnTo":"same"}
                ]}
                """))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SYS_001));
    }

    @Test
    void rejectsEmptyNodesAndBlankKey() {
        assertThatThrownBy(() -> codec.parse("{\"nodes\":[]}"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> codec.parse(
                "{\"nodes\":[{\"key\":\" \",\"name\":\"A\",\"assignee\":\"x\",\"returnTo\":\"start\"}]}"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> codec.parse("{oops"))
                .isInstanceOf(BizException.class);
    }
}
