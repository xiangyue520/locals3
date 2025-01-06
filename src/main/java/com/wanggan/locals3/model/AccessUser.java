package com.wanggan.locals3.model;

import java.util.Objects;

/**
 * @author wanggan
 * @desc:
 * @date 2025/1/6
 */
public class AccessUser {
    private String accessKey;
    private String accessSecret;

    public static AccessUser of(String accessKey, String accessSecret) {
        AccessUser accessUser = new AccessUser();
        accessUser.setAccessKey(accessKey);
        accessUser.setAccessSecret(accessSecret);
        return accessUser;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getAccessSecret() {
        return accessSecret;
    }

    public void setAccessSecret(String accessSecret) {
        this.accessSecret = accessSecret;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        AccessUser that = (AccessUser) object;
        return Objects.equals(accessKey, that.accessKey);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(accessKey);
    }
}
