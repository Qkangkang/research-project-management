package cn.edu.rpm.iam.api;

import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.iam.api.dto.UserOptionView;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import cn.edu.rpm.iam.application.service.UserDirectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户目录只读接口。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserDirectoryController {

    private final UserDirectoryService userDirectoryService;

    public UserDirectoryController(UserDirectoryService userDirectoryService) {
        this.userDirectoryService = userDirectoryService;
    }

    /**
     * 生效用户下拉选项。
     *
     * @param keyword 工号/姓名关键词；可为空
     * @return 用户选项列表
     */
    @GetMapping("/options")
    public Result<List<UserOptionView>> options(
            @RequestParam(name = "keyword", required = false) String keyword) {
        AuthContextHolder.require();
        return Result.ok(userDirectoryService.listOptions(keyword));
    }
}
