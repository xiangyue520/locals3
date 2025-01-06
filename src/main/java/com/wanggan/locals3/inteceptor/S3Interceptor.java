package com.wanggan.locals3.inteceptor;

import com.wanggan.locals3.config.SystemConfig;
import com.wanggan.locals3.constant.S3Constant;
import com.wanggan.locals3.model.AccessUser;
import com.wanggan.locals3.util.ConvertOp;
import com.wanggan.locals3.util.StringUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Component
public class S3Interceptor implements HandlerInterceptor {
    public static final String SCHEME = "AWS4";
    public static final String ALGORITHM = "HMAC-SHA256";
    public static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    @Resource
    SystemConfig systemConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /**
         * 此处使用aws的v4版本授权进行处理,
         * 具体授权头见,https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-authentication-HTTPPOST.html
         */
        boolean flag = false;
        String authorization = request.getHeader("Authorization");
        if (!StringUtil.isEmpty(authorization)) {
            flag = validAuthorizationHead(request);
        } else {
            //<your-access-key-id>/<date>/<aws-region>/<aws-service>/aws4_request
            //authorization = request.getParameter("X-Amz-Credential");
            //if (!StringUtil.isEmpty(authorization)) {
            //    flag = validAuthorizationUrl(request, systemConfig.getUsername(), systemConfig.getPassword());
            //}
            String url = URLDecoder.decode(request.getRequestURI(), S3Constant.UTF_8);
            //String replace = url.replace(S3Constant.ENDPOINT + "/", "");
            //兼容处理/useasy-oss/1090/common1736134526979/202516/1736134527206/big-sur.mp4和//useasy-oss/1090/common1736134526979/202516/1736134527206/big-sur.mp4
            String[] split = Arrays.stream(url.split("/")).filter(StringUtils::hasText).toArray(String[]::new);
            String bucketName = split[0];
            flag = allow(bucketName);
            if (!flag) {
                System.out.println("not allow access,bucketName:" + bucketName + ",url:" + url);
            }
        }
        if (!flag) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        }
        return flag;
    }

    private boolean allow(String bucketName) {
        if (!StringUtils.hasText(bucketName)) {
            return false;
        }
        List<String> buckets = systemConfig.getExternalBuckets();
        if (CollectionUtils.isEmpty(buckets)) {
            return false;
        }
        return buckets.contains(bucketName);
    }

    public boolean validAuthorizationHead(HttpServletRequest request) throws Exception {
        String authorization = request.getHeader("Authorization");
        String requestDate = request.getHeader("x-amz-date");
        String contentHash = request.getHeader("x-amz-content-sha256");
        String httpMethod = request.getMethod();
        String uri = request.getRequestURI().split("\\?")[0];
        //String requestUrl = request.getRequestURI().split("\\?")[0];
        //String uri = URLDecoder.decode(requestUrl, S3Constant.UTF_8);
        //requestUrl = URLDecoder.decode(requestUrl, S3Constant.UTF_8);
        //String[] urlList = requestUrl.split("\\/");
        //String uri = "";
        //for (int i = 0; i < urlList.length - 1; i++) {
        //    uri += urlList[i] + "/";
        //}
        //uri += URLEncoder.encode(urlList[urlList.length - 1], S3Constant.UTF_8);
        String queryString = ConvertOp.convert2String(request.getQueryString());
        //示例
        //AWS4-HMAC-SHA256 Credential=admin/20230530/us-east-1/s3/aws4_request, SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;host;x-amz-content-sha256;x-amz-date, Signature=6f50628a101b46264c7783937be0366762683e0d319830b1844643e40b3b0ed

        ///region authorization拆分
        String[] parts = authorization.trim().split("\\,");
        //第一部分-凭证范围
        String credential = parts[0].split("\\=")[1];
        String[] credentials = credential.split("\\/");
        String accessKey = credentials[0];
        AccessUser accessUser = matchUser(accessKey);
        if (null == accessUser) {
            return false;
        }
        String date = credentials[1];
        String region = credentials[2];
        String service = credentials[3];
        String aws4Request = credentials[4];
        //第二部分-签名头中包含哪些字段
        String signedHeader = parts[1].split("\\=")[1];
        String[] signedHeaders = signedHeader.split("\\;");
        //第三部分-获取生成的签名
        String signature = parts[2].split("\\=")[1];

        String canonicalizedHeaders = "";
        for (String name : signedHeaders) {
            canonicalizedHeaders += name + ":" + request.getHeader(name) + "\n";
        }

        String canonicalizedQueryParameters = "";
        if (!StringUtil.isEmpty(queryString)) {
            Map<String, String> queryStringMap = parseQueryParams(queryString);
            canonicalizedQueryParameters = getCanonicalizedQueryString(queryStringMap);
        }

        String canonicalRequest = getCanonicalRequest(uri, httpMethod,
                canonicalizedQueryParameters, canonicalizedHeaders, signedHeader, contentHash);

        String scope = date + "/" + region + "/" + service + "/" + aws4Request;
        //待签名字符串,签名由4部分组成
        //1-Algorithm – 用于创建规范请求的哈希的算法。对于 SHA-256，算法是 AWS4-HMAC-SHA256。
        //2-RequestDateTime – 在凭证范围内使用的日期和时间。
        //3-CredentialScope – 凭证范围。这会将生成的签名限制在指定的区域和服务范围内。该字符串采用以下格式：YYYYMMDD/region/service/aws4_request
        //4-HashedCanonicalRequest – 规范请求的哈希。
        String stringToSign = getStringToSign(SCHEME, ALGORITHM, requestDate, scope, canonicalRequest);

        ///region 重新生成签名
        //计算签名的key
        byte[] kSecret = (SCHEME + accessUser.getAccessSecret()).getBytes();
        byte[] kDate = doHmacSHA256(kSecret, date);
        byte[] kRegion = doHmacSHA256(kDate, region);
        byte[] kService = doHmacSHA256(kRegion, service);
        byte[] signatureKey = doHmacSHA256(kService, aws4Request);
        //计算签名
        byte[] authSignature = doHmacSHA256(signatureKey, stringToSign);
        //对签名编码处理
        String strHexSignature = toHex(authSignature);
        ///endregion

        if (signature.equals(strHexSignature)) {
            return true;
        }
        return false;
    }

    private AccessUser matchUser(String accessKey) {
        List<AccessUser> authList = systemConfig.getAuthList();
        if (CollectionUtils.isEmpty(authList)) {
            return null;
        }
        return authList.stream().filter(v -> v.getAccessKey().equals(accessKey)).findFirst().orElse(null);
    }

    public static String getCanonicalizedQueryString(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        SortedMap<String, String> sorted = new TreeMap<String, String>();

        Iterator<Map.Entry<String, String>> pairs = parameters.entrySet().iterator();
        while (pairs.hasNext()) {
            Map.Entry<String, String> pair = pairs.next();
            String key = pair.getKey();
            String value = pair.getValue();
            sorted.put(urlEncode(key, false), urlEncode(value, false));
        }

        StringBuilder builder = new StringBuilder();
        pairs = sorted.entrySet().iterator();
        while (pairs.hasNext()) {
            Map.Entry<String, String> pair = pairs.next();
            builder.append(pair.getKey());
            builder.append("=");
            builder.append(pair.getValue());
            if (pairs.hasNext()) {
                builder.append("&");
            }
        }

        return builder.toString();
    }

    public static String urlEncode(String url, boolean keepPathSlash) {
        String encoded;
        try {
            encoded = URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding is not supported.", e);
        }
        if (keepPathSlash) {
            encoded = encoded.replace("%2F", "/");
        }
        return encoded;
    }

    /**
     * Computes the canonical headers with values for the request. For AWS4, all
     * headers must be included in the signing process.
     */
    protected static String getCanonicalizedHeaderString(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }

        // step1: sort the headers by case-insensitive order
        List<String> sortedHeaders = new ArrayList<String>(headers.keySet());
        Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

        // step2: form the canonical header:value entries in sorted order.
        // Multiple white spaces in the values should be compressed to a single
        // space.
        StringBuilder buffer = new StringBuilder();
        for (String key : sortedHeaders) {
            buffer.append(key.toLowerCase()).append(":").append(headers.get(key));
            buffer.append("\n");
        }

        return buffer.toString();
    }

    protected static String getCanonicalizeHeaderNames(Map<String, String> headers) {
        List<String> sortedHeaders = new ArrayList<String>(headers.keySet());
        Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

        StringBuilder buffer = new StringBuilder();
        for (String header : sortedHeaders) {
            if (buffer.length() > 0) buffer.append(";");
            buffer.append(header.toLowerCase());
        }

        return buffer.toString();
    }

    protected static String getCanonicalRequest(String uri,
                                                String httpMethod,
                                                String queryParameters,
                                                String canonicalizedHeaders,
                                                String canonicalizedHeaderNames,
                                                String bodyHash) {
        String canonicalRequest =
                httpMethod + "\n" +
                        uri + "\n" +
                        queryParameters + "\n" +
                        canonicalizedHeaders + "\n" +
                        canonicalizedHeaderNames + "\n" +
                        bodyHash;
        return canonicalRequest;
    }

    protected static String getStringToSign(String scheme, String algorithm, String dateTime, String scope, String canonicalRequest) {
        String stringToSign =
                scheme + "-" + algorithm + "\n" +
                        dateTime + "\n" +
                        scope + "\n" +
                        toHex(hash(canonicalRequest));
        return stringToSign;
    }

    public static byte[] hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(text.getBytes("UTF-8"));
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException("Unable to compute hash while signing request: " + e.getMessage(), e);
        }
    }

    public boolean validAuthorizationUrl(HttpServletRequest request, String accessKeyId, String secretAccessKey) throws Exception {
        String requestDate = request.getParameter("X-Amz-Date");
        String httpMethod = request.getMethod();
        //String uri = request.getRequestURI().split("\\?")[0];
        String requestUrl = request.getRequestURI().split("\\?")[0];
        //String uri = URLDecoder.decode(requestUrl, S3Constant.UTF_8);
        requestUrl = URLDecoder.decode(requestUrl, S3Constant.UTF_8);
        String[] urlList = requestUrl.split("\\/");
        String uri = "";
        for (int i = 0; i < urlList.length - 1; i++) {
            uri += urlList[i] + "/";
        }
        uri += URLEncoder.encode(urlList[urlList.length - 1], S3Constant.UTF_8);
        String queryString = ConvertOp.convert2String(request.getQueryString());
        //示例
        //"http://localhost:8001/s3/kkk/%E6%B1%9F%E5%AE%81%E8%B4%A2%E6%94%BF%E5%B1%80%E9%A1%B9%E7%9B%AE%E5%AF%B9%E6%8E%A5%E6%96%87%E6%A1%A3.docx?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20230531T024715Z&X-Amz-SignedHeaders=host&X-Amz-Expires=300&X-Amz-Credential=admin%2F20230531%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Signature=038e2ea71073761aa0370215621599649e9228177c332a0a79f784b1a6d9ee39

        ///region 参数准备
        //第一部分-凭证范围
        String credential = request.getParameter("X-Amz-Credential");
        String[] credentials = credential.split("\\/");
        String accessKey = credentials[0];
        if (!accessKeyId.equals(accessKey)) {
            return false;
        }
        String date = credentials[1];
        String region = credentials[2];
        String service = credentials[3];
        String aws4Request = credentials[4];
        //第二部分-签名头中包含哪些字段
        String signedHeader = request.getParameter("X-Amz-SignedHeaders");
        String[] signedHeaders = signedHeader.split("\\;");
        //第三部分-生成的签名
        String signature = request.getParameter("X-Amz-Signature");
        ///endregion

        ///region 验证expire
        String expires = request.getParameter("X-Amz-Expires");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        LocalDateTime startDate = LocalDateTime.parse(requestDate, formatter);
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime localDateTime = startDate.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId);
        startDate = localDateTime.toLocalDateTime();
        LocalDateTime endDate = startDate.plusSeconds(ConvertOp.convert2Int(expires));
        if (endDate.isBefore(LocalDateTime.now())) {
            return false;
        }
        ///endregion

        ///region 待签名字符串
        String stringToSign = "";
        //签名由4部分组成
        //1-Algorithm – 用于创建规范请求的哈希的算法。对于 SHA-256，算法是 AWS4-HMAC-SHA256。
        stringToSign += "AWS4-HMAC-SHA256" + "\n";
        //2-RequestDateTime – 在凭证范围内使用的日期和时间。
        stringToSign += requestDate + "\n";
        //3-CredentialScope – 凭证范围。这会将生成的签名限制在指定的区域和服务范围内。该字符串采用以下格式：YYYYMMDD/region/service/aws4_request
        stringToSign += date + "/" + region + "/" + service + "/" + aws4Request + "\n";
        //4-HashedCanonicalRequest – 规范请求的哈希。
        //<HTTPMethod>\n
        //<CanonicalURI>\n
        //<CanonicalQueryString>\n
        //<CanonicalHeaders>\n
        //<SignedHeaders>\n
        //<HashedPayload>
        String hashedCanonicalRequest = "";
        //4.1-HTTP Method
        hashedCanonicalRequest += httpMethod + "\n";
        //4.2-Canonical URI
        hashedCanonicalRequest += uri + "\n";
        //4.3-Canonical Query String
        if (!StringUtil.isEmpty(queryString)) {
            Map<String, String> queryStringMap = parseQueryParams(queryString);
            List<String> keyList = new ArrayList<>(queryStringMap.keySet());
            Collections.sort(keyList);
            StringBuilder queryStringBuilder = new StringBuilder("");
            for (String key : keyList) {
                if (!key.equals("X-Amz-Signature")) {
                    queryStringBuilder.append(key).append("=").append(queryStringMap.get(key)).append("&");
                }
            }
            queryStringBuilder.deleteCharAt(queryStringBuilder.lastIndexOf("&"));

            hashedCanonicalRequest += queryStringBuilder.toString() + "\n";
        } else {
            hashedCanonicalRequest += queryString + "\n";
        }
        //4.4-Canonical Headers
        for (String name : signedHeaders) {
            hashedCanonicalRequest += name + ":" + request.getHeader(name) + "\n";
        }
        hashedCanonicalRequest += "\n";
        //4.5-Signed Headers
        hashedCanonicalRequest += signedHeader + "\n";
        //4.6-Hashed Payload
        hashedCanonicalRequest += UNSIGNED_PAYLOAD;
        stringToSign += doHex(hashedCanonicalRequest);
        ///endregion

        ///region 重新生成签名
        //计算签名的key
        byte[] kSecret = (SCHEME + secretAccessKey).getBytes("UTF8");
        byte[] kDate = doHmacSHA256(kSecret, date);
        byte[] kRegion = doHmacSHA256(kDate, region);
        byte[] kService = doHmacSHA256(kRegion, service);
        byte[] signatureKey = doHmacSHA256(kService, aws4Request);
        //计算签名
        byte[] authSignature = doHmacSHA256(signatureKey, stringToSign);
        //对签名编码处理
        String strHexSignature = doBytesToHex(authSignature);
        ///endregion

        if (signature.equals(strHexSignature)) {
            return true;
        }
        return false;
    }

    private String doHex(String data) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(data.getBytes(S3Constant.UTF_8));
            byte[] digest = messageDigest.digest();
            return String.format("%064x", new java.math.BigInteger(1, digest));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] doHmacSHA256(byte[] key, String data) throws Exception {
        String algorithm = "HmacSHA256";
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key, algorithm));
        return mac.doFinal(data.getBytes("UTF-8"));
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private String doBytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars).toLowerCase();
    }

    public static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (int i = 0; i < data.length; i++) {
            String hex = Integer.toHexString(data[i]);
            if (hex.length() == 1) {
                // Append leading zero.
                sb.append("0");
            } else if (hex.length() == 8) {
                // Remove ff prefix from negative numbers.
                hex = hex.substring(6);
            }
            sb.append(hex);
        }
        return sb.toString().toLowerCase(Locale.getDefault());
    }

    public static Map<String, String> parseQueryParams(String queryString) {
        Map<String, String> queryParams = new HashMap<>();
        try {
            if (queryString != null && !queryString.isEmpty()) {
                String[] queryParamsArray = queryString.split("\\&");

                for (String param : queryParamsArray) {
                    String[] keyValue = param.split("\\=");
                    if (keyValue.length == 1) {
                        String key = keyValue[0];
                        String value = "";
                        queryParams.put(key, value);
                    } else if (keyValue.length == 2) {
                        String key = keyValue[0];
                        String value = keyValue[1];
                        queryParams.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return queryParams;
    }

}
