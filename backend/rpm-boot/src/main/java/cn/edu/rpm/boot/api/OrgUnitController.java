package cn.edu.rpm.boot.api;

import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.organization.api.dto.OrgUnitOptionView;
import cn.edu.rpm.organization.application.service.OrgUnitQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织/部门只读接口。
 *
 * <p>组织模块本身不依赖 IAM，认证上下文检查放在启动模块装配，
 * 避免 rpm-organization 与 rpm-iam 形成循环依赖。</p>
 */
@RestController
@RequestMapping("/api/v1/org-units")
public class OrgUnitController {

    private final OrgUnitQueryService orgUnitQueryService;

    public OrgUnitController(OrgUnitQueryService orgUnitQueryService) {
        this.orgUnitQueryService = orgUnitQueryService;
    }

    /**
     * 生效组织下拉选项。
     *
     * @return 组织选项列表
     */
    @GetMapping("/options")
    public Result<List<OrgUnitOptionView>> options() {
        AuthContextHolder.require();
        return Result.ok(orgUnitQueryService.listActiveOptions());
    }
}
