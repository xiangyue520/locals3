package com.wanggan.locals3.util;

import java.math.BigDecimal;

public class ConvertOp {

    public static boolean isNull(Object obj) {
        return obj == null;
    }

    public static String convert2String(Object obj) {
        if (isNull(obj)) {
            return "";
        }
        try {
            return String.valueOf(obj);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static Integer convert2Int(Object obj) {
        if (isNull(obj)) {
            return 0;
        }
        if (obj instanceof BigDecimal) {
            BigDecimal bigDecimal = (BigDecimal) obj;
            return bigDecimal.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}
