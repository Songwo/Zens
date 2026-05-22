package com.campus.trend.campus_pulse.service;

import java.util.Map;

public interface MediaStorageAdminService {

    Map<String, Object> getConfigSnapshot();

    Map<String, Object> saveConfig(Map<String, Object> body);

    Map<String, Object> checkPublicAccess(String overridePublicBaseUrl);

    Map<String, Object> rebuildAccessUrls(String overridePublicBaseUrl);
}
