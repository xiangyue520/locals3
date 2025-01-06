package com.wanggan.locals3.config;

import com.wanggan.locals3.model.AccessUser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "system")
public class SystemConfig {
    private String tempPath;
    private String dataPath;
    private String username;
    private String password;
    private List<String> externalBuckets;
    private Boolean adminAuth;
    private List<AccessUser> authList;

    public String getTempPath() {
        return tempPath;
    }

    public void setTempPath(String tempPath) {
        this.tempPath = tempPath;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getExternalBuckets() {
        return externalBuckets;
    }

    public void setExternalBuckets(List<String> externalBuckets) {
        this.externalBuckets = externalBuckets;
    }

    public List<AccessUser> getAuthList() {
        if (null == authList) {
            authList = new ArrayList<>(4);
        }
        //如果admin授权开启,且admin不在列表中,那么才进行添加
        if (!authList.contains(username) && getAdminAuth()) {
            authList.add(AccessUser.of(username, password));
        }
        return authList;
    }

    public void setAuthList(List<AccessUser> authList) {
        this.authList = authList;
    }

    public Boolean getAdminAuth() {
        return Optional.ofNullable(adminAuth).orElse(Boolean.FALSE);
    }

    public void setAdminAuth(Boolean adminAuth) {
        this.adminAuth = adminAuth;
    }
}
