package cn.edu.rpm.file.infrastructure.mapper;

import cn.edu.rpm.file.infrastructure.entity.FileObjectEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code file_object} 表 Mapper（T-FILE-001）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，不声明注解 SQL；
 * 条件查询由 Repository 以 Wrapper 构造。</p>
 */
@Mapper
public interface FileObjectMapper extends BaseMapper<FileObjectEntity> {
}
