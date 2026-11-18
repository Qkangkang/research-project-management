package cn.edu.rpm.boot.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 审计字段自动填充器（T-ENV-005）。
 *
 * <p>对标注了 MyBatis-Plus 填充注解的字段自动写入
 * {@code created_at} / {@code updated_at} / {@code created_by} / {@code updated_by}
 * （对应《02-数据模型与数据库设计.md》§1 原则 4；实体基类 BaseEntity 于 Batch-2 引入）。</p>
 *
 * <p>时间统一取 {@code Asia/Shanghai} 时区（展示时区约定）；
 * 操作人取 {@link AuditUserContext}，上下文缺失时不写操作人。</p>
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final ZoneId ZONE_ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");

    /**
     * INSERT 时填充四个审计字段；仅填充注解声明且值为 null 的字段，不覆盖业务已赋值。
     *
     * @param metaObject MyBatis 元对象（当前插入实体）
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now(ZONE_ASIA_SHANGHAI);
        String actor = AuditUserContext.currentActor();
        this.strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
        if (actor != null) {
            this.strictInsertFill(metaObject, "createdBy", String.class, actor);
            this.strictInsertFill(metaObject, "updatedBy", String.class, actor);
        }
    }

    /**
     * UPDATE 时刷新 {@code updated_at} 与 {@code updated_by}。
     *
     * @param metaObject MyBatis 元对象（当前更新实体）
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class,
                OffsetDateTime.now(ZONE_ASIA_SHANGHAI));
        String actor = AuditUserContext.currentActor();
        if (actor != null) {
            this.strictUpdateFill(metaObject, "updatedBy", String.class, actor);
        }
    }
}
