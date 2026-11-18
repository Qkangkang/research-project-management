package cn.edu.rpm.boot.config;

import cn.edu.rpm.iam.domain.port.CoiDeclarationQueryPort;
import cn.edu.rpm.iam.domain.port.OrgScopePort;
import cn.edu.rpm.iam.domain.port.PermissionCache;
import cn.edu.rpm.iam.domain.port.RolePermissionQueryPort;
import cn.edu.rpm.iam.domain.port.RoleQueryPort;
import cn.edu.rpm.iam.domain.port.TempGrantQueryPort;
import cn.edu.rpm.iam.domain.port.UserRoleQueryPort;
import cn.edu.rpm.iam.domain.service.AuthorizationService;
import cn.edu.rpm.iam.domain.service.DataScopeResolver;
import cn.edu.rpm.iam.domain.service.DefaultAuthorizationService;
import cn.edu.rpm.iam.domain.service.FieldMaskService;
import cn.edu.rpm.iam.domain.service.FunctionPermissionResolver;
import cn.edu.rpm.iam.domain.service.GrantValidityGuard;
import cn.edu.rpm.iam.domain.service.PermissionCacheEvictor;
import cn.edu.rpm.iam.domain.service.SeparationOfDutyGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 授权核心服务装配（Batch-4 / T-IAM-013）。
 *
 * <p>领域服务为纯 Java 类（不引 Spring 注解，DEP-2），统一在本配置中装配；
 * 其数据依赖（Repository / 端口适配器 / Redis 缓存）由组件扫描注入。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationConfig {

    /**
     * 功能权限解析器（角色 → 权限点，短期缓存）。
     *
     * @param userRoleQueryPort       用户-角色关联查询端口
     * @param rolePermissionQueryPort 角色-权限码查询端口
     * @param permissionCache         权限快照缓存端口（Redis 适配器）
     * @return 解析器
     */
    @Bean
    public FunctionPermissionResolver functionPermissionResolver(UserRoleQueryPort userRoleQueryPort,
                                                                 RolePermissionQueryPort rolePermissionQueryPort,
                                                                 PermissionCache permissionCache) {
        return new FunctionPermissionResolver(userRoleQueryPort, rolePermissionQueryPort, permissionCache);
    }

    /**
     * 权限快照失效器（角色/授权变更后清理）。
     *
     * @param permissionCache 权限快照缓存端口
     * @return 失效器
     */
    @Bean
    public PermissionCacheEvictor permissionCacheEvictor(PermissionCache permissionCache) {
        return new PermissionCacheEvictor(permissionCache);
    }

    /**
     * 临时授权有效期守护。
     *
     * @return 守护实例
     */
    @Bean
    public GrantValidityGuard grantValidityGuard() {
        return new GrantValidityGuard();
    }

    /**
     * 项目数据范围解析器。
     *
     * @param userRoleQueryPort  用户-角色关联查询端口
     * @param roleQueryPort      角色查询端口
     * @param tempGrantQueryPort 临时授权查询端口
     * @param orgScopePort       组织范围端口适配器
     * @param grantValidityGuard 临时授权有效期守护
     * @return 解析器
     */
    @Bean
    public DataScopeResolver dataScopeResolver(UserRoleQueryPort userRoleQueryPort,
                                               RoleQueryPort roleQueryPort,
                                               TempGrantQueryPort tempGrantQueryPort,
                                               OrgScopePort orgScopePort,
                                               GrantValidityGuard grantValidityGuard) {
        return new DataScopeResolver(userRoleQueryPort, roleQueryPort, tempGrantQueryPort,
                orgScopePort, grantValidityGuard);
    }

    /**
     * 字段脱敏服务。
     *
     * @return 脱敏服务
     */
    @Bean
    public FieldMaskService fieldMaskService() {
        return new FieldMaskService();
    }

    /**
     * 职责分离与利益冲突守护。
     *
     * @param coiDeclarationQueryPort 利益冲突声明查询端口适配器
     * @return 守护实例
     */
    @Bean
    public SeparationOfDutyGuard separationOfDutyGuard(CoiDeclarationQueryPort coiDeclarationQueryPort) {
        return new SeparationOfDutyGuard(coiDeclarationQueryPort);
    }

    /**
     * 统一授权服务（对外以接口注入，应用服务不得依赖实现类）。
     *
     * @param dataScopeResolver     数据范围解析器
     * @param fieldMaskService      字段脱敏服务
     * @param separationOfDutyGuard 职责分离守护
     * @return 授权服务
     */
    @Bean
    public AuthorizationService authorizationService(DataScopeResolver dataScopeResolver,
                                                     FieldMaskService fieldMaskService,
                                                     SeparationOfDutyGuard separationOfDutyGuard) {
        return new DefaultAuthorizationService(dataScopeResolver, fieldMaskService, separationOfDutyGuard);
    }
}
