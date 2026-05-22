package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.MediaFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MediaFileService extends IService<MediaFile> {

    MediaFile findActiveBySha256(String sha256);

    MediaFile findActiveByFileKey(String fileKey);

    MediaFile saveCompletedFile(MediaFile mediaFile);

    Page<MediaFile> pageFiles(int pageNum, int pageSize, String keyword, String mediaType, String bizType, Integer status);

    boolean softDelete(String id);

    int softDeleteBatch(Collection<String> ids);

    Map<String, Object> buildStats();

    List<MediaFile> listRecent(int limit);

    long rebuildActiveAccessUrls(String publicBaseUrl);
}
