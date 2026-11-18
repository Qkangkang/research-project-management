package cn.edu.rpm.workflow.infrastructure.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FlowableConfig} 纯单元测试（T-WF-001 先行测试）。
 *
 * <p>验收锚点：引擎指向独立 {@code flowable} schema；关闭启动自动建表
 * （databaseSchemaUpdate=false）；测试流程定义资源存在且为合法的两节点 BPMN。
 * 引擎真实启动/部署验证依赖数据库，归下游业务测试阶段。</p>
 */
class FlowableConfigTest {

    @Test
    void configurePointsEngineToFlowableSchemaAndDisablesAutoDdl() {
        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();

        new FlowableConfig().configure(configuration);

        assertThat(configuration.getDatabaseSchema()).isEqualTo("flowable");
        assertThat(configuration.getDatabaseSchemaUpdate())
                .isEqualTo(SpringProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
        assertThat(configuration.getDatabaseSchemaUpdate()).isEqualTo("false");
    }

    @Test
    void testProcessDefinitionIsPackagedAndContainsTwoUserTasks() throws Exception {
        ClassPathResource resource = new ClassPathResource(FlowableConfig.TEST_PROCESS_RESOURCE);
        assertThat(resource.exists()).isTrue();

        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(resource.getInputStream());
        NodeList processes = document.getElementsByTagName("process");
        assertThat(processes.getLength()).isEqualTo(1);
        assertThat(processes.item(0).getAttributes().getNamedItem("id").getNodeValue())
                .isEqualTo("rpmTestApproval");
        assertThat(document.getElementsByTagName("userTask").getLength()).isEqualTo(2);
        assertThat(document.getElementsByTagName("userTask").item(0)
                .getAttributes().getNamedItem("name").getNodeValue()).isEqualTo("形式审查");
        assertThat(document.getElementsByTagName("userTask").item(1)
                .getAttributes().getNamedItem("name").getNodeValue()).isEqualTo("决策");
    }

    @Test
    void expenseProcessDefinitionIsPackagedAndExecutable() throws Exception {
        ClassPathResource resource = new ClassPathResource(FlowableConfig.EXPENSE_PROCESS_RESOURCE);
        assertThat(resource.exists()).isTrue();

        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(resource.getInputStream());
        NodeList processes = document.getElementsByTagName("process");
        assertThat(processes.getLength()).isEqualTo(1);
        assertThat(processes.item(0).getAttributes().getNamedItem("id").getNodeValue())
                .isEqualTo("expenseApproval");
        assertThat(processes.item(0).getAttributes().getNamedItem("isExecutable").getNodeValue())
                .isEqualTo("true");
        assertThat(document.getElementsByTagName("userTask").getLength()).isEqualTo(1);
        assertThat(document.getElementsByTagName("userTask").item(0)
                .getAttributes().getNamedItem("name").getNodeValue()).isEqualTo("经费审批");
    }
}
