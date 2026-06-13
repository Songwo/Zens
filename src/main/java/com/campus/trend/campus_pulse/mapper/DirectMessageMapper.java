package com.campus.trend.campus_pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trend.campus_pulse.entity.DirectMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface DirectMessageMapper extends BaseMapper<DirectMessage> {

    @Select(
        "SELECT MAX(id) as max_id FROM direct_messages " +
        "WHERE sender_id = #{userId} OR receiver_id = #{userId} " +
        "GROUP BY conversation_id " +
        "ORDER BY max_id DESC"
    )
    List<Long> selectLatestMessageIds(@Param("userId") String userId);

    @Select("<script>" +
            "SELECT * FROM direct_messages WHERE id IN " +
            "<foreach item='item' index='index' collection='ids' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "ORDER BY id DESC" +
            "</script>")
    List<DirectMessage> selectMessagesByIds(@Param("ids") List<Long> ids);

    @Select("<script>" +
            "SELECT conversation_id as conversationId, COUNT(*) as unreadCount " +
            "FROM direct_messages " +
            "WHERE receiver_id = #{userId} AND is_read = 0 AND conversation_id IN " +
            "<foreach item='item' index='index' collection='convIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "GROUP BY conversation_id" +
            "</script>")
    List<Map<String, Object>> selectUnreadCounts(
            @Param("userId") String userId,
            @Param("convIds") List<String> convIds
    );
}
