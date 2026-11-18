package cn.edu.rpm.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BaseEntity 注解装配与审计字段填充契约测试（T-DB-008）。
 *
 * <p>纯单元测试：不依赖 Spring / 数据库。用 MyBatis-Plus {@link TableInfoHelper}
 * 完成与真实启动路径一致的注解解析，再用与 rpm-boot {@code AuditMetaObjectHandler}
 * 完全相同的 strictFill 契约（镜像实现，boot 模块类对 common 测试不可见）驱动填充，
 * 验证基类注解装配能让填充器真实生效。</p>
 */
class BaseEntityTest {

    /** 测试桩：业务 PO 继承 BaseEntity 后的最小形态。 */
    public static class TestPo extends BaseEntity {
    }

    private static final ZoneId ZONE_ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String OFFSET_PLUS_8 = "+08:00";
    private static final String ACTOR = "u-test-0001";

    private MybatisConfiguration configuration;

    @BeforeEach
    void initTableInfo() {
        configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, TestPo.class);
        assertNotNull(tableInfo, "TableInfo 解析失败：BaseEntity 注解装配不成立");
    }

    /** 与 AuditMetaObjectHandler 完全相同的填充契约镜像（Asia/Shanghai + strictFill + 操作人上下文）。 */
    private MetaObjectHandler auditHandlerMirror(String actor) {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                OffsetDateTime now = OffsetDateTime.now(ZONE_ASIA_SHANGHAI);
                this.strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
                this.strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
                this.strictInsertFill(metaObject, "createdBy", String.class, actor);
                this.strictInsertFill(metaObject, "updatedBy", String.class, actor);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class,
                        OffsetDateTime.now(ZONE_ASIA_SHANGHAI));
                this.strictUpdateFill(metaObject, "updatedBy", String.class, actor);
            }
        };
    }

    @Test
    void primaryKeyMapsToIdColumnWithAssignUuid() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(TestPo.class);
        assertEquals("id", tableInfo.getKeyColumn(), "主键列名应为 id");
        assertEquals("id", tableInfo.getKeyProperty(), "主键属性名应为 id");
        assertEquals(IdType.ASSIGN_UUID, tableInfo.getIdType(),
                "主键应为 ASSIGN_UUID（插入时生成 uuid，对应 PostgreSQL uuid 列默认 gen_random_uuid 的替代方案）");
    }

    @Test
    void auditFieldsMapToSnakeCaseColumnsWithExactHandlerTypes() throws Exception {
        Field createdAt = BaseEntity.class.getDeclaredField("createdAt");
        Field updatedAt = BaseEntity.class.getDeclaredField("updatedAt");
        Field createdBy = BaseEntity.class.getDeclaredField("createdBy");
        Field updatedBy = BaseEntity.class.getDeclaredField("updatedBy");

        TableField createdAtFill = createdAt.getAnnotation(TableField.class);
        assertEquals("created_at", createdAtFill.value(), "created_at 列映射");
        assertEquals(FieldFill.INSERT, createdAtFill.fill(), "created_at 应为 INSERT 填充");
        assertEquals(OffsetDateTime.class, createdAt.getType(),
                "created_at Java 类型应为 OffsetDateTime（与填充器声明一致，否则 strictFill 静默跳过）");

        TableField updatedAtFill = updatedAt.getAnnotation(TableField.class);
        assertEquals("updated_at", updatedAtFill.value(), "updated_at 列映射");
        assertEquals(FieldFill.INSERT_UPDATE, updatedAtFill.fill(), "updated_at 应为 INSERT_UPDATE 填充");
        assertEquals(OffsetDateTime.class, updatedAt.getType(), "updated_at Java 类型应为 OffsetDateTime");

        TableField createdByFill = createdBy.getAnnotation(TableField.class);
        assertEquals("created_by", createdByFill.value(), "created_by 列映射");
        assertEquals(FieldFill.INSERT, createdByFill.fill(), "created_by 应为 INSERT 填充");
        assertEquals(String.class, createdBy.getType(), "created_by Java 类型应为 String（与填充器声明一致）");

        TableField updatedByFill = updatedBy.getAnnotation(TableField.class);
        assertEquals("updated_by", updatedByFill.value(), "updated_by 列映射");
        assertEquals(FieldFill.INSERT_UPDATE, updatedByFill.fill(), "updated_by 应为 INSERT_UPDATE 填充");
        assertEquals(String.class, updatedBy.getType(), "updated_by Java 类型应为 String");
    }

    @Test
    void versionFieldCarriesOptimisticLockAnnotationAndDefaultsToZero() throws Exception {
        Field version = BaseEntity.class.getDeclaredField("version");
        assertNotNull(version.getAnnotation(Version.class), "version 字段必须标注 @Version 供乐观锁拦截器识别");
        assertEquals(Integer.class, version.getType(), "version 类型应为 Integer");

        TestPo po = new TestPo();
        assertEquals(0, po.getVersion(), "新实体 version 应默认 0（与 DDL DEFAULT 0 一致，插入即带显式版本）");
    }

    @Test
    void insertFillPopulatesAllNullAuditFieldsWithActorAndNow() {
        TestPo po = new TestPo();
        MetaObject metaObject = SystemMetaObject.forObject(po);
        auditHandlerMirror(ACTOR).insertFill(metaObject);

        assertNotNull(po.getCreatedAt(), "createdAt 未被填充：INSERT 填充注解装配失效");
        assertNotNull(po.getUpdatedAt(), "updatedAt 未被填充：INSERT_UPDATE 填充注解装配失效");
        assertEquals(ACTOR, po.getCreatedBy(), "createdBy 应填充为当前操作人");
        assertEquals(ACTOR, po.getUpdatedBy(), "updatedBy 应填充为当前操作人");
        assertEquals(OFFSET_PLUS_8, String.valueOf(po.getCreatedAt().getOffset()),
                "填充时间应为 Asia/Shanghai（+08:00）时区");
    }

    @Test
    void insertFillDoesNotOverridePresetBusinessValues() {
        TestPo po = new TestPo();
        OffsetDateTime presetTime = OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0,
                ZoneOffset.of(OFFSET_PLUS_8));
        po.setCreatedAt(presetTime);
        po.setCreatedBy("u-preset-0002");

        auditHandlerMirror(ACTOR).insertFill(SystemMetaObject.forObject(po));

        assertSame(presetTime, po.getCreatedAt(), "业务已赋值的 createdAt 不得被覆盖（strictFill 仅填 null）");
        assertEquals("u-preset-0002", po.getCreatedBy(), "业务已赋值的 createdBy 不得被覆盖");
        assertNotNull(po.getUpdatedAt(), "null 的 updatedAt 仍应被填充");
    }

    @Test
    void updateFillTouchesOnlyUpdateFields() {
        TestPo po = new TestPo();
        assertNull(po.getCreatedAt());
        assertNull(po.getCreatedBy());

        auditHandlerMirror(ACTOR).updateFill(SystemMetaObject.forObject(po));

        assertNotNull(po.getUpdatedAt(), "updatedAt 未被更新填充");
        assertEquals(ACTOR, po.getUpdatedBy(), "updatedBy 未被更新填充");
        assertNull(po.getCreatedAt(), "createdAt 不得参与 UPDATE 填充（FieldFill.INSERT 而非 INSERT_UPDATE）");
        assertNull(po.getCreatedBy(), "createdBy 不得参与 UPDATE 填充");
    }

    @Test
    void concreteSubclassInheritsTableInfoWithoutShadowing() {
        // 派生 PO 不重写任何字段时，填充与主键装配同样生效（Batch-3 起所有业务 PO 的继承前提）
        TestPo po = new TestPo();
        po.setId("0197f16a2c3d7891b4a2c5d6e7f80910");
        assertEquals("0197f16a2c3d7891b4a2c5d6e7f80910", po.getId(), "派生 PO 的主键读写应直接继承");

        TableInfo tableInfo = TableInfoHelper.getTableInfo(TestPo.class);
        assertTrue(tableInfo.getFieldList().size() >= 5, "基类 5 个公共字段（含主键外 4 审计 + version）应全部注册");
        assertFalse(tableInfo.isAutoInitResultMap(), "无需自动 resultMap（列名已蛇形映射）");
    }
}
