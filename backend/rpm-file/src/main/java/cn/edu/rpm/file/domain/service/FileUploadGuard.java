package cn.edu.rpm.file.domain.service;

import cn.edu.rpm.common.exception.BizException;
import cn.edu.rpm.common.exception.ErrorCode;
import cn.edu.rpm.file.domain.model.FileObject;
import cn.edu.rpm.file.domain.model.ScanStatus;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 文件上传校验服务（T-FILE-004；技术文档 03 §5、08 §4.2）。
 *
 * <p>纯 Java 领域服务，按扩展名、MIME、大小、魔数四层校验，防止伪造扩展名上传可执行内容：</p>
 * <ul>
 *   <li>校验失败：文件置 {@code REJECTED} 并抛 {@code RPM-FILE-002}，不生成业务引用；</li>
 *   <li>校验通过：文件置 {@code QUARANTINED}，等待扫描结论；</li>
 *   <li>扫描结论：{@code PASSED}/{@code SKIPPED}（一期占位）→ {@code AVAILABLE}；
 *       {@code FAILED} → 保持 {@code QUARANTINED}；</li>
 *   <li>业务引用前必须调用 {@link #requireAvailable}，未就绪抛 {@code RPM-FILE-001}。</li>
 * </ul>
 *
 * <p>扩展名白名单与大小上限由 {@code rpm.file.*} 外置配置注入（禁止硬编码业务常量）。</p>
 */
public class FileUploadGuard {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ZIP_MAGIC = new byte[]{'P', 'K', 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = new byte[]{
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_PNG = "image/png";
    private static final String MIME_JPEG = "image/jpeg";
    private static final String MIME_ZIP = "application/zip";
    private static final String MIME_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_PPTX =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    /** 已知扩展名 → MIME 集合 + 魔数规则；{@code magic} 为空表示纯文本类无固定魔数。 */
    private static final Map<String, TypeRule> RULES = Map.ofEntries(
            Map.entry("pdf", new TypeRule(Set.of(MIME_PDF), PDF_MAGIC)),
            Map.entry("png", new TypeRule(Set.of(MIME_PNG), PNG_MAGIC)),
            Map.entry("jpg", new TypeRule(Set.of(MIME_JPEG), JPEG_MAGIC)),
            Map.entry("jpeg", new TypeRule(Set.of(MIME_JPEG), JPEG_MAGIC)),
            Map.entry("docx", new TypeRule(Set.of(MIME_DOCX), ZIP_MAGIC)),
            Map.entry("xlsx", new TypeRule(Set.of(MIME_XLSX), ZIP_MAGIC)),
            Map.entry("pptx", new TypeRule(Set.of(MIME_PPTX), ZIP_MAGIC)),
            Map.entry("doc", new TypeRule(Set.of("application/msword"), OLE2_MAGIC)),
            Map.entry("xls", new TypeRule(Set.of("application/vnd.ms-excel"), OLE2_MAGIC)),
            Map.entry("ppt", new TypeRule(Set.of("application/vnd.ms-powerpoint"), OLE2_MAGIC)),
            Map.entry("zip", new TypeRule(Set.of(MIME_ZIP, "application/x-zip-compressed"), ZIP_MAGIC)),
            Map.entry("txt", new TypeRule(Set.of("text/plain"), null)),
            Map.entry("csv", new TypeRule(Set.of("text/csv", "text/plain", "application/vnd.ms-excel"), null))
    );

    private final long maxSizeBytes;

    private final Set<String> allowedExtensions;

    /**
     * 构造上传校验服务。
     *
     * @param maxSizeBytes      单文件大小上限（字节）
     * @param allowedExtensions 允许的扩展名白名单（小写、不含点）
     */
    public FileUploadGuard(long maxSizeBytes, Set<String> allowedExtensions) {
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes 必须大于 0");
        }
        this.maxSizeBytes = maxSizeBytes;
        this.allowedExtensions = allowedExtensions == null ? Set.of() : allowedExtensions.stream()
                .filter(Objects::nonNull)
                .map(ext -> ext.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 校验上传文件（扩展名、MIME、大小、魔数）。
     *
     * @param file 待校验文件（状态应为 UPLOADING）
     * @param head 文件头字节（建议至少前 16 字节）
     * @throws BizException 校验不通过 {@code RPM-FILE-002}，文件已置 {@code REJECTED}
     */
    public void validate(FileObject file, byte[] head) {
        Objects.requireNonNull(file, "文件对象不能为空");
        if (file.getSizeBytes() <= 0) {
            reject(file, "文件大小必须大于 0");
        }
        if (file.getSizeBytes() > maxSizeBytes) {
            reject(file, "文件大小超出限制（上限 " + maxSizeBytes + " 字节）");
        }
        String extension = extensionOf(file.getOriginalName());
        if (extension == null) {
            reject(file, "文件名缺少扩展名");
        }
        if (!allowedExtensions.contains(extension)) {
            reject(file, "扩展名不在允许范围：" + extension);
        }
        TypeRule rule = RULES.get(extension);
        if (rule == null) {
            reject(file, "暂不支持的扩展名：" + extension);
        }
        String declaredMime = normalizeMime(file.getMimeType());
        if (declaredMime == null || !rule.mimeTypes().contains(declaredMime)) {
            reject(file, "MIME 与扩展名不匹配：" + file.getMimeType());
        }
        if (head == null || head.length == 0) {
            reject(file, "无法读取文件头");
        }
        if (rule.magic() != null && !startsWith(head, rule.magic())) {
            reject(file, "文件内容与扩展名不匹配：" + extension);
        }
        file.markQuarantined();
    }

    /**
     * 要求文件可用（业务绑定附件前调用）。
     *
     * @param file 文件对象
     * @throws BizException 文件未就绪 {@code RPM-FILE-001}（TC-IAM-027）
     */
    public void requireAvailable(FileObject file) {
        Objects.requireNonNull(file, "文件对象不能为空");
        file.requireAvailable();
    }

    /**
     * 应用病毒扫描结果（一期占位钩子返回 SKIPPED）。
     *
     * @param file   隔离中的文件
     * @param result 扫描结论
     * @throws BizException 结论非法或状态不允许（由 {@link FileObject#applyScanResult} 抛出的运行时异常）
     */
    public void applyScanResult(FileObject file, ScanStatus result) {
        Objects.requireNonNull(file, "文件对象不能为空");
        file.applyScanResult(result);
    }

    private static void reject(FileObject file, String message) {
        file.markRejected();
        throw new BizException(ErrorCode.FILE_002, message);
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }
        int semicolon = mimeType.indexOf(';');
        String base = semicolon < 0 ? mimeType : mimeType.substring(0, semicolon);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        if (head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 扩展名类型规则。
     *
     * @param mimeTypes 允许的 MIME 集合
     * @param magic     魔数前缀；纯文本为 {@code null}
     */
    private record TypeRule(Set<String> mimeTypes, byte[] magic) {
    }
}
