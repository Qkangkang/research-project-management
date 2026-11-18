package cn.edu.rpm.common.api;

/**
 * 测试用表单 Bean：为参数校验异常测试提供目标对象（仅测试可见）。
 */
public class TestForm {

    private String projectName;

    private String budgetAmount;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(String budgetAmount) {
        this.budgetAmount = budgetAmount;
    }
}
