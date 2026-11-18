package cn.edu.rpm.file.infrastructure.mapper;

import cn.edu.rpm.file.infrastructure.entity.FileRefEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code file_ref} 表 Mapper（T-FILE-002）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，不声明注解 SQL。</p>
 */
@Mapper
public interface FileRefMapper extends BaseMapper<FileRefEntity> {
}
