package com.wanggan.locals3.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtil {
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE_TAG_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public static final DateTimeFormatter DATE_UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");


    public static String getUTCDateFormat() {
        return LocalDateTime.now().format(DATE_UTC_FORMATTER);
    }

    public static String getDateFormatToSecond(LocalDateTime date) {
        return date.format(DATE_TIME_FORMATTER);
    }

    public static String getDateTagToSecond() {
        return LocalDateTime.now().format(DATE_TAG_FORMATTER);
    }

}
