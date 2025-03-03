package com.wanggan.locals3.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

public class CommonUtil {
    public static String getNewGuid() {
        String dateStr = DateUtil.getDateTagToSecond();
        String randomStr = UUID.randomUUID().toString();
        try {
            randomStr = EncryptUtil.encryptByMD5(randomStr).toLowerCase();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dateStr + randomStr;
    }

    public static String getApiPath() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        assert requestAttributes != null;
        HttpServletRequest request = requestAttributes.getRequest();
        String requestURL = request.getRequestURL().toString();
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();
        String rootPageURL = scheme + ":" + "//" + serverName + ":" + port;
        return rootPageURL;
    }
}
