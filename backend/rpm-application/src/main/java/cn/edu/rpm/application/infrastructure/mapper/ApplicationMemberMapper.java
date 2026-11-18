package cn.edu.rpm.application.infrastructure.mapper;

import cn.edu.rpm.application.infrastructure.entity.ApplicationMemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code application_member} 表 Mapper（T-APP-005）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；成员快照批量插入需要多值 {@code INSERT}
 * （避免逐行往返），SQL 统一维护在 {@code resources/mapper/ApplicationMemberMapper.xml}
 * （禁止注解 SQL）。分批上限由 Repository 层控制（RULE-DB3，每批 ≤ 500）。</p>
 */
@Mapper
public interface ApplicationMemberMapper extends BaseMapper<ApplicationMemberEntity> {

    /**
     * 批量插入成员快照（单条多值 INSERT；调用方保证每批 ≤ 500 条）。
     *
     * @param members 成员快照列表，元素必须已具备主键 {@code id}
     * @return 受影响行数
     */
    int insertBatch(@Param("members") List<ApplicationMemberEntity> members);
}
