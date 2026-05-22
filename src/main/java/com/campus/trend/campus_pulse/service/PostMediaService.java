package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.media.MediaObject;
import com.campus.trend.campus_pulse.entity.PostMedia;

import java.util.Collection;
import java.util.List;

/**
 * 帖子媒体关系服务。
 * 规则：createPost / updatePost 传进来的 mediaList 保存在 sys_post_media，
 * 同时回填 sys_post.images / cover_image，保证老代码与列表接口兼容。
 */
public interface PostMediaService extends IService<PostMedia> {

    /** 覆盖式保存某个帖子的媒体列表；传空列表则清空。 */
    List<PostMedia> saveForPost(String postId, List<MediaObject> mediaList);

    /** 按 postId 查询媒体（按 sortOrder 升序）。 */
    List<PostMedia> listByPostId(String postId);

    /** 批量查询多个帖子的媒体（列表场景）。 */
    List<PostMedia> listByPostIds(Collection<String> postIds);

    /** 删除某帖子的全部媒体关系。 */
    void deleteByPostId(String postId);

    /** 物理删除 fileId 关联的所有帖子引用（管理员删除 Go 文件后用）。 */
    int deleteByFileId(String fileId);
}
