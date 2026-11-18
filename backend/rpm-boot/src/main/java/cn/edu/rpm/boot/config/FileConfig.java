package cn.edu.rpm.boot.config;

import cn.edu.rpm.file.domain.port.FileGrantPort;
import cn.edu.rpm.file.domain.port.FileObjectQueryPort;
import cn.edu.rpm.file.domain.port.FileRefQueryPort;
import cn.edu.rpm.file.domain.port.ObjectStoragePort;
import cn.edu.rpm.file.domain.service.FileAccessService;
import cn.edu.rpm.file.domain.service.FileUploadGuard;
import cn.edu.rpm.file.infrastructure.config.FileProperties;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.LinkedHashSet;

/**
 * 文件领域服务装配（Batch-5 / T-FILE-004、T-FILE-005）。
 *
 * <p>{@code FileUploadGuard} / {@code FileAccessService} 为纯 Java 领域服务（不引 Spring 注解），
 * 在此按 {@code rpm.file.*} 外置配置装配。查询端口、签名端口与对象存储端口由
 * {@code rpm-file} 基础设施实现提供（组件扫描注入）。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileProperties.class)
public class FileConfig {

    /**
     * 上传校验服务。
     *
     * @param properties 文件模块配置
     * @return 上传校验服务（大小上限与扩展名白名单来自配置）
     */
    @Bean
    public FileUploadGuard fileUploadGuard(FileProperties properties) {
        return new FileUploadGuard(properties.getMaxSizeBytes(),
                new LinkedHashSet<>(properties.getAllowedExtensions()));
    }

    /**
     * 下载鉴权服务。
     *
     * @param fileObjectQueryPort  文件查询端口
     * @param fileRefQueryPort     文件引用查询端口
     * @param fileGrantPort        下载签名端口
     * @param objectStoragePort    对象存储签名端口
     * @param authorizationService 统一授权服务
     * @param properties           文件模块配置（签名有效期）
     * @param clock                时间基准
     * @return 下载鉴权服务
     */
    @Bean
    public FileAccessService fileAccessService(FileObjectQueryPort fileObjectQueryPort,
                                               FileRefQueryPort fileRefQueryPort,
                                               FileGrantPort fileGrantPort,
                                               ObjectStoragePort objectStoragePort,
                                               AuthorizationService authorizationService,
                                               FileProperties properties,
                                               Clock clock) {
        return new FileAccessService(fileObjectQueryPort, fileRefQueryPort, fileGrantPort,
                objectStoragePort, authorizationService, properties.getSignatureTtl(), clock);
    }
}
