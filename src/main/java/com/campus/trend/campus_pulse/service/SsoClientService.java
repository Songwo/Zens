package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SsoClient;

import java.util.List;

public interface SsoClientService extends IService<SsoClient> {

    /** 查询所有 SSO 应用 */
    List<SsoClient> listClients();

    /** 创建 SSO 应用，自动生成 clientSecret */
    SsoClient createClient(String clientId, String clientName, String redirectUri, String description, String logoUrl);

    /** 创建或修复预置 SSO 应用，保留既有密钥并合并回调地址 */
    SsoClient upsertPresetClient(String clientId, String clientName, String redirectUri, String description, String logoUrl);

    /** 更新 SSO 应用 */
    void updateClient(String id, String clientName, String redirectUri, String description, String logoUrl);

    /** 删除 SSO 应用 */
    void deleteClient(String id);

    /** 启用/禁用 */
    void toggleClient(String id, boolean enabled);

    /** 重置密钥 */
    String resetSecret(String id);

    /** 验证 clientId 和 redirectUri 是否匹配且已启用 */
    SsoClient validateClient(String clientId, String redirectUri);
}
