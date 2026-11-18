package cn.edu.rpm.boot.api;

import cn.edu.rpm.boot.file.FileUploadAppService;
import cn.edu.rpm.common.api.Result;
import cn.edu.rpm.iam.application.context.AuthContextHolder;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

/**
 * 文件上传与短期下载地址接口。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileUploadAppService fileUploadAppService;

    public FileController(FileUploadAppService fileUploadAppService) {
        this.fileUploadAppService = fileUploadAppService;
    }

    /**
     * 上传单个文件。
     */
    @PostMapping("/upload")
    public Result<FileUploadView> upload(@RequestParam("file") MultipartFile file) {
        var uploaded = fileUploadAppService.upload(file, AuthContextHolder.require());
        return Result.ok(new FileUploadView(uploaded.fileId(), uploaded.originalName(),
                uploaded.sizeBytes(), uploaded.sha256()));
    }

    /**
     * 申请短期下载地址。
     */
    @GetMapping("/{fileId}/download-url")
    public Result<FileDownloadView> downloadUrl(@PathVariable("fileId") String fileId) {
        var download = fileUploadAppService.downloadUrl(fileId, AuthContextHolder.require());
        return Result.ok(new FileDownloadView(download.token(), download.downloadUrl(),
                download.expiresAt()));
    }

    /**
     * 上传结果（snake_case 与前端统一响应约定一致）。
     */
    public record FileUploadView(
            @JsonProperty("file_id") String fileId,
            @JsonProperty("original_name") String originalName,
            @JsonProperty("size_bytes") long sizeBytes,
            @JsonProperty("sha256") String sha256
    ) {
    }

    /**
     * 短期下载地址。
     */
    public record FileDownloadView(
            @JsonProperty("token") String token,
            @JsonProperty("download_url") String downloadUrl,
            @JsonProperty("expires_at") Instant expiresAt
    ) {
    }
}
