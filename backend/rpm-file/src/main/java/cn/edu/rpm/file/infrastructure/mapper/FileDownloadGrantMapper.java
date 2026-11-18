package cn.edu.rpm.file.infrastructure.mapper;

import cn.edu.rpm.file.infrastructure.entity.FileDownloadGrantEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code file_download_grant} 表 Mapper（T-FILE-005）。
 *
 * <p>仅继承 MyBatis-Plus {@link BaseMapper} 通用 CRUD，不声明注解 SQL；
 * 批量撤销使用 Wrapper 生成单条参数化 UPDATE。</p>
 */
@Mapper
public interface FileDownloadGrantMapper extends BaseMapper<FileDownloadGrantEntity> {
}
