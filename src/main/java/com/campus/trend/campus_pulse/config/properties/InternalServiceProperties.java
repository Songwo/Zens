package com.campus.trend.campus_pulse.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内部 s2s 服务白名单(供 InternalServiceFilter 使用)。
 * 新增子站只需在 application.yml 的 internal.service.clients 加一项,无需改 Java 代码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "internal.service")
public class InternalServiceProperties {

    private List<Client> clients = new ArrayList<>();

    @Data
    public static class Client {
        private boolean enabled = true;
        /** 服务标识,子站请求头 X-Service-Id 必须与其一致 */
        private String id;
        /** HMAC-SHA256 共享密钥 */
        private String secret;
    }

    /** 启动即校验:id 重复或 secret 空白直接 fail-fast,避免带病上线。 */
    @PostConstruct
    void validate() {
        Set<String> seen = new HashSet<>();
        for (Client c : clients) {
            if (c != null && !c.isEnabled()) continue;
            if (c == null || !StringUtils.hasText(c.getId())) {
                throw new IllegalStateException("internal.service.clients 存在缺少 id 的条目");
            }
            if (!StringUtils.hasText(c.getSecret())) {
                throw new IllegalStateException("internal.service.clients[" + c.getId() + "] 缺少 secret");
            }
            if ("zens-ops".equals(c.getId()) && c.getSecret().length() < 32) {
                throw new IllegalStateException("internal.service.clients[zens-ops] secret 长度必须至少 32 字符");
            }
            if (!seen.add(c.getId())) {
                throw new IllegalStateException("internal.service.clients 存在重复 id: " + c.getId());
            }
        }
    }

    /** serviceId -> secret 只读视图,白名单即 keySet。 */
    public Map<String, String> asSecretMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Client c : clients) {
            if (c == null || !c.isEnabled()) continue;
            map.put(c.getId(), c.getSecret());
        }
        return Map.copyOf(map);
    }
}
