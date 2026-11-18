package cn.edu.rpm.application.infrastructure.mapper;

import cn.edu.rpm.application.infrastructure.entity.ApplicationVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code application_version} 表 Mapper（T-APP-004）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD，常规条件查询由 Repository 层以 Wrapper 构造；
 * 「取当前草稿版本」与「取最大版本号」需要 {@code ORDER BY ... LIMIT 1} /
 * {@code MAX()}，SQL 统一维护在 {@code resources/mapper/ApplicationVersionMapper.xml}
 * （禁止注解 SQL）。版本不可变，无自定义更新 SQL。</p>
 */
@Mapper
public interface ApplicationVersionMapper extends BaseMapper<ApplicationVersionEntity> {

    /**
     * 查询申报当前草稿版本（{@code status = DRAFT}，版本号最大一条）。
     *
     * @param applicationId 申报主档 ID
     * @return 草稿版本；无草稿返回 {@code null}
     */
    ApplicationVersionEntity selectDraftByApplicationId(@Param("applicationId") String applicationId);

    /**
     * 查询申报已有最大版本号。
     *
     * @param applicationId 申报主档 ID
     * @return 最大版本号；无版本返回 0
     */
    int selectMaxVersionNo(@Param("applicationId") String applicationId);
}
