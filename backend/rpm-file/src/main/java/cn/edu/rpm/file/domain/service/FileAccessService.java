package cn.edu.rpm.file.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.file.domain.model.FileAccessGrant;
import cn.edu.rpm.file.domain.model.FileDownloadGrant;
import cn.edu.rpm.file.domain.model.FileDownloadTicket;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.FilePermissionCodes;
import cn.edu.rpm.file.domain.model.FileRef;
import cn.edu.rpm.file.domain.port.FileGrantPort;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.file.domain.port.FileRefQueryPort;
import cn.edu.rpm.file.domain.port.ObjectStoragePort;
import cn.edu.rpm.iam.application.dto.Action;
import cn.edu.rpm.iam.application.dto.AuthContext;
import cn.edu.rpm.iam.application.dto.ResourceRef;
import cn.edu.rpm.iam.domain.service.AuthorizationService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件下载鉴权服务（T-FILE-005；技术文档 03 §5、08 §4.2）。
 *
 * <p>授权口径：</p>
 * <ul>
 *   <li>文件必须 {@code AVAILABLE}；不存在/未就绪/无权限统一返回 {@code RPM-COMMON-403}，
 *       不泄露文件是否存在（TC-IAM-016/107）；</li>
 *   <li>已被业务引用的文件：按所属项目执行 {@code AuthorizationService.check(project:view)}，
 *       每次下载复核，权限快照缓存 5 分钟（TC-IAM-018）；</li>
 *   <li>未被业务引用的孤立文件：仅上传人本人可下载；</li>
 *   <li>授权通过后签发 ≤5 分钟短期签名并写入 {@code file_download_grant}；
 *       业务撤销授权时调用 {@link #revokeForFile} 批量撤销，旧签名立即失效。</li>
 * </ul>
 *
 * <p>本类为纯 Java 领域服务，时间基准注入 {@link Clock}；用户身份由 {@link AuthContext}
 * 承载（不从 ThreadLocal 读取，保证可测试与领域纯净）。</p>
 */
public class FileAccessService {

    /** 签名有效期上限（技术文档 08 §4.2：≤5 分钟）。 */
    private static final Duration MAX_SIGNATURE_TTL = Duration.ofSeconds(300);

    private static final Action VIEW_ACTION = Action.of(FilePermissionCodes.PROJECT_VIEW);

    private final FileObjectQueryPort fileObjectQueryPort;

    private final FileRefQueryPort fileRefQueryPort;

    private final FileGrantPort fileGrantPort;

    private final ObjectStoragePort objectStoragePort;

    private final AuthorizationService authorizationService;

    private final Duration signatureTtl;

    private final Clock clock;

    /**
     * 构造下载鉴权服务。
     *
     * @param fileObjectQueryPort  文件对象查询端口
     * @param fileRefQueryPort     文件引用查询端口
     * @param fileGrantPort        下载签名记录端口
     * @param objectStoragePort    对象存储签名端口
     * @param authorizationService 统一授权服务
     * @param signatureTtl         签名有效期（必须为 1～300 秒）
     * @param clock                时间基准
     */
    public FileAccessService(FileObjectQueryPort fileObjectQueryPort,
                            FileRefQueryPort fileRefQueryPort,
                            FileGrantPort fileGrantPort,
                            ObjectStoragePort objectStoragePort,
                            AuthorizationService authorizationService,
                            Duration signatureTtl,
                            Clock clock) {
        this.fileObjectQueryPort = Objects.requireNonNull(fileObjectQueryPort, "fileObjectQueryPort 不能为空");
        this.fileRefQueryPort = Objects.requireNonNull(fileRefQueryPort, "fileRefQueryPort 不能为空");
        this.fileGrantPort = Objects.requireNonNull(fileGrantPort, "fileGrantPort 不能为空");
        this.objectStoragePort = Objects.requireNonNull(objectStoragePort, "objectStoragePort 不能为空");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        Objects.requireNonNull(signatureTtl, "signatureTtl 不能为空");
        if (signatureTtl.isZero() || signatureTtl.isNegative()) {
            throw new IllegalArgumentException("签名有效期必须大于 0");
        }
        if (signatureTtl.compareTo(MAX_SIGNATURE_TTL) > 0) {
            throw new IllegalArgumentException("签名有效期不得超过 300 秒");
        }
        this.signatureTtl = signatureTtl;
    }

    /**
     * 校验文件访问权限，通过后签发短期下载签名。
     *
     * @param fileId      文件 ID
     * @param userId      请求人用户 ID（必须与授权上下文一致）
     * @param resource    文件所属业务资源（孤立文件可为空）
     * @param authContext 请求级授权上下文
     * @return 短期签名、预签名 URL 与失效时刻
     * @throws BizException 无权限/文件不可用 {@code RPM-COMMON-403}
     */
    public FileAccessGrant authorize(UUID fileId, UUID userId, ResourceRef resource, AuthContext authContext) {
        Objects.requireNonNull(fileId, "fileId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(authContext, "authContext 不能为空");
        if (!authContext.userId().equals(userId)) {
            throw deny();
        }
        FileObject file = fileObjectQueryPort.findById(fileId).orElseThrow(FileAccessService::deny);
        if (!file.isAvailable()) {
            throw deny();
        }
        if (!isAllowed(file, resource, authContext)) {
            throw deny();
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(signatureTtl);
        FileDownloadGrant grant = FileDownloadGrant.issue(fileId, userId, now, expiresAt);
        fileGrantPort.save(grant);
        String downloadUrl = objectStoragePort.presignGet(file.getObjectKey(), signatureTtl);
        return new FileAccessGrant(grant.getToken(), downloadUrl, expiresAt);
    }

    /**
     * 校验短期签名并复核业务权限（每次下载调用）。
     *
     * @param grantToken  签名 token
     * @param authContext 请求级授权上下文
     * @param resource    文件所属业务资源（孤立文件可为空）
     * @return 下载凭据
     * @throws BizException 签名不存在/已撤销/已过期/非本用户/无权限 {@code RPM-COMMON-403}
     */
    public FileDownloadTicket validate(String grantToken, AuthContext authContext, ResourceRef resource) {
        Objects.requireNonNull(authContext, "authContext 不能为空");
        if (grantToken == null || grantToken.isBlank()) {
            throw deny();
        }
        FileDownloadGrant grant = fileGrantPort.findByToken(grantToken).orElseThrow(FileAccessService::deny);
        if (!grant.isUsableAt(clock.instant()) || !grant.getGrantedTo().equals(authContext.userId())) {
            throw deny();
        }
        FileObject file = fileObjectQueryPort.findById(grant.getFileId()).orElseThrow(FileAccessService::deny);
        if (!file.isAvailable() || !isAllowed(file, resource, authContext)) {
            throw deny();
        }
        return new FileDownloadTicket(file.getId(), file.getObjectKey(), grant.getExpiresAt());
    }

    /**
     * 撤销某文件的全部下载签名（业务授权撤回、成员移除、支出退回时调用）。
     *
     * @param fileId 文件 ID
     * @return 被撤销的签名单数
     */
    public int revokeForFile(UUID fileId) {
        Objects.requireNonNull(fileId, "fileId 不能为空");
        return fileGrantPort.revokeAllForFile(fileId, clock.instant());
    }

    private boolean isAllowed(FileObject file, ResourceRef resource, AuthContext authContext) {
        List<FileRef> refs = fileRefQueryPort.findByFileId(file.getId());
        if (refs.isEmpty()) {
            // 孤立文件：仅上传人本人可下载
            return Objects.equals(file.getUploadedBy(), authContext.userId());
        }
        if (resource != null) {
            // 已被业务引用且调用方提供资源上下文：按所属项目执行统一授权
            return authorizationService.check(authContext, VIEW_ACTION, resource).allowed();
        }
        // 已被引用但未提供资源上下文：上传人本人仍可见（绑定不剥夺其既有访问，
        // 与绑定前口径一致；其他用户需经带资源上下文的业务入口访问）
        return Objects.equals(file.getUploadedBy(), authContext.userId());
    }

    private static BizException deny() {
        return new BizException(ErrorCode.COMMON_403);
    }
}
