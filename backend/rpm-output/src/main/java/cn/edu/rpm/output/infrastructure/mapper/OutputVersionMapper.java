package cn.edu.rpm.output.infrastructure.mapper;

import cn.edu.rpm.output.infrastructure.entity.OutputVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code output_version} 表 Mapper（T-OUT-002）。
 *
 * <p>{@link BaseMapper} 提供通用 CRUD；版本号计算 SQL 维护在
 * {@code resources/mapper/OutputVersionMapper.xml}（禁止注解 SQL）。</p>
 */
@Mapper
public interface OutputVersionMapper extends BaseMapper<OutputVersionEntity> {

    /**
     * 查询成果当前最大版本号（无版本返回 0），供修订生成 {@code max + 1}。
     *
     * @param outputId 成果 ID
     * @return 最大版本号；无版本返回 0
     */
    int selectMaxVersionNo(@Param("outputId") String outputId);
}
