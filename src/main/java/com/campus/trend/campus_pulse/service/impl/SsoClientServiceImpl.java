package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SsoClient;
import com.campus.trend.campus_pulse.mapper.SsoClientMapper;
import com.campus.trend.campus_pulse.service.SsoClientService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class SsoClientServiceImpl extends ServiceImpl<SsoClientMapper, SsoClient> implements SsoClientService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public List<SsoClient> listClients() {
        return lambdaQuery().orderByDesc(SsoClient::getCreateTime).list();
    }

    @Override
    public SsoClient createClient(String clientId, String clientName, String redirectUri, String description, String logoUrl) {
        // 检查 clientId 是否已存在
        SsoClient existing = lambdaQuery().eq(SsoClient::getClientId, clientId).one();
        if (existing != null) {
            throw new RuntimeException("应用标识 " + clientId + " 已存在");
        }

        String secret = generateSecret();
        SsoClient client = new SsoClient()
                .setClientId(clientId.trim())
                .setClientName(clientName.trim())
                .setClientSecret(secret)
                .setRedirectUri(redirectUri.trim())
                .setDescription(description)
                .setLogoUrl(logoUrl)
                .setEnabled(1);
        save(client);
        return client;
    }

    @Override
    public void updateClient(String id, String clientName, String redirectUri, String description, String logoUrl) {
        SsoClient client = getById(id);
        if (client == null) {
            throw new RuntimeException("SSO 应用不存在");
        }
        client.setClientName(clientName.trim())
                .setRedirectUri(redirectUri.trim())
                .setDescription(description)
                .setLogoUrl(logoUrl);
        updateById(client);
    }

    @Override
    public void deleteClient(String id) {
        if (!removeById(id)) {
            throw new RuntimeException("删除失败，应用不存在");
        }
    }

    @Override
    public void toggleClient(String id, boolean enabled) {
        SsoClient client = getById(id);
        if (client == null) {
            throw new RuntimeException("SSO 应用不存在");
        }
        client.setEnabled(enabled ? 1 : 0);
        updateById(client);
    }

    @Override
    public String resetSecret(String id) {
        SsoClient client = getById(id);
        if (client == null) {
            throw new RuntimeException("SSO 应用不存在");
        }
        String newSecret = generateSecret();
        client.setClientSecret(newSecret);
        updateById(client);
        return newSecret;
    }

    @Override
    public SsoClient validateClient(String clientId, String redirectUri) {
        SsoClient client = lambdaQuery()
                .eq(SsoClient::getClientId, clientId)
                .eq(SsoClient::getEnabled, 1)
                .one();
        if (client == null) {
            throw new RuntimeException("SSO 应用不存在或已禁用");
        }
        // 验证回调地址匹配（支持多个，逗号分隔）
        String[] allowedUris = client.getRedirectUri().split(",");
        boolean matched = false;
        for (String allowed : allowedUris) {
            if (allowed.trim().equals(redirectUri.trim())) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            throw new RuntimeException("回调地址不匹配: " + redirectUri);
        }
        return client;
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "sso_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
