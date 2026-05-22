package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.MediaFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaFileMapper extends BaseMapper<MediaFile> {

    @Select("""
            SELECT *
            FROM sys_media_file
            WHERE sha256 = #{sha256}
              AND status = 1
            ORDER BY create_time DESC
            LIMIT 1
            """)
    MediaFile selectActiveBySha256(String sha256);
}
