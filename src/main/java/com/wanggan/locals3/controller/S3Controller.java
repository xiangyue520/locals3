package com.wanggan.locals3.controller;

import com.wanggan.locals3.config.SystemConfig;
import com.wanggan.locals3.constant.S3Constant;
import com.wanggan.locals3.model.Bucket;
import com.wanggan.locals3.model.CompleteMultipartUpload;
import com.wanggan.locals3.model.CompleteMultipartUploadResult;
import com.wanggan.locals3.model.InitiateMultipartUploadResult;
import com.wanggan.locals3.model.ListBucketsResult;
import com.wanggan.locals3.model.PartETag;
import com.wanggan.locals3.model.S3Object;
import com.wanggan.locals3.model.S3ObjectInputStream;
import com.wanggan.locals3.service.S3Service;
import com.wanggan.locals3.util.CommonUtil;
import com.wanggan.locals3.util.ConvertOp;
import com.wanggan.locals3.util.DateUtil;
import com.wanggan.locals3.util.EncryptUtil;
import com.wanggan.locals3.util.StringUtil;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(S3Constant.ENDPOINT)
//api参考地址https://docs.aws.amazon.com/AmazonS3/latest/API/
public class S3Controller {
    @Resource
    S3Service s3Service;
    @Resource
    SystemConfig systemConfig;

    // Bucket相关接口
    @PutMapping("/{bucketName}")
    public ResponseEntity<String> createBucket(@PathVariable String bucketName) throws Exception {
        bucketName = getBucketName(bucketName);
        s3Service.createBucket(bucketName);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/")
    public ResponseEntity<String> listBuckets() throws Exception {
        ListBucketsResult result = new ListBucketsResult(s3Service.listBuckets());
        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement(S3Constant.LIST_ALL_MYBUCKET_SRESULT);
        Element owner = root.addElement(S3Constant.OWNER);
        Element id = owner.addElement(S3Constant.ID);
        id.setText(S3Constant.VERSION);
        Element displayName = owner.addElement(S3Constant.DISPLAY_NAME);
        displayName.setText(systemConfig.getUsername());
        Element buckets = root.addElement(S3Constant.BUCKETS);
        for (Bucket item : result.getBuckets()) {
            Element bucket = buckets.addElement(S3Constant.BUCKET);
            Element name = bucket.addElement(S3Constant.NAME);
            name.setText(item.getName());
            Element creationDate = bucket.addElement(S3Constant.CREATIONDATE);
            creationDate.setText(item.getCreationDate());
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(getXml(doc));
    }


    private String getXml(Document doc) throws Exception {
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding(S3Constant.UTF_8);
        StringWriter out = new StringWriter();
        XMLWriter writer = new XMLWriter(out, format);
        writer.write(doc);
        writer.close();
        String xml = out.toString();
        out.close();
        return xml;
    }

    @RequestMapping(value = "/{bucketName}", method = RequestMethod.HEAD)
    public ResponseEntity<Object> headBucket(@PathVariable("bucketName") String bucketName) throws Exception {
        bucketName = getBucketName(bucketName);
        if (s3Service.headBucket(bucketName)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{bucketName}")
    public ResponseEntity<String> deleteBucket(@PathVariable("bucketName") String bucketName) throws Exception {
        bucketName = getBucketName(bucketName);
        s3Service.deleteBucket(bucketName);
        return ResponseEntity.noContent().build();
    }

    // Object相关接口
    @GetMapping("/{bucketName}")
    public ResponseEntity<String> listObjects(@PathVariable String bucketName, HttpServletRequest request) throws Exception {
        bucketName = getBucketName(bucketName);
        String prefix = ConvertOp.convert2String(request.getParameter(S3Constant.PREFIX.toLowerCase()));
        List<S3Object> s3ObjectList = s3Service.listObjects(bucketName, prefix);
        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement(S3Constant.LIST_OBJECTS_RESULT);
        Element name = root.addElement(S3Constant.NAME);
        name.setText(bucketName);
        Element prefixElement = root.addElement(S3Constant.PREFIX);
        prefixElement.setText(prefix);
        Element isTruncated = root.addElement("IsTruncated");
        isTruncated.setText("false");
        Element maxKeys = root.addElement("MaxKeys");
        maxKeys.setText("100000");
        for (S3Object s3Object : s3ObjectList) {
            Element contents = root.addElement("Contents");
            Element key = contents.addElement(S3Constant.KEY);
            key.setText(s3Object.getKey());
            if (!StringUtil.isEmpty(s3Object.getMetadata().getLastModified())) {
                Element lastModified = contents.addElement(S3Constant.LAST_MODIFIED);
                lastModified.setText(s3Object.getMetadata().getLastModified());
                Element size = contents.addElement(S3Constant.SIZE);
                size.setText(s3Object.getMetadata().getContentLength() + "");
            }
        }

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(getXml(doc));
    }

    @RequestMapping(value = "/{bucketName}/**", method = RequestMethod.HEAD)
    public ResponseEntity<Object> headObject(@PathVariable String bucketName, HttpServletRequest request, HttpServletResponse response) throws Exception {
        bucketName = getBucketName(bucketName);
        String pageUrl = getUrl(request);
        String objectKey = getObjectKey(pageUrl, bucketName, request).replace("/metadata", "");
        HashMap<String, String> headInfo = s3Service.headObject(bucketName, objectKey);
        if (headInfo.containsKey("NoExist")) {
            return ResponseEntity.notFound().build();
        }
        headInfo.entrySet().stream().forEach(v -> response.addHeader(v.getKey(), v.getValue()));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{bucketName}/**")
    public ResponseEntity<String> putObject(@PathVariable String bucketName, HttpServletRequest request) throws Exception {
        bucketName = getBucketName(bucketName);
        String pageUrl = getUrl(request);
        String objectKey = getObjectKey(pageUrl, bucketName, request);
        String copySource = URLDecoder.decode(ConvertOp.convert2String(request.getHeader("x-amz-copy-source")), S3Constant.UTF_8);
        if (StringUtil.isEmpty(copySource)) {
            s3Service.putObject(bucketName, objectKey, request.getInputStream());
            return ResponseEntity.ok().build();
        }
        copySource = getUrl(copySource);
        String[] copyList = copySource.split("\\/");
        String sourceBucketName = "";
        for (String item : copyList) {
            if (!StringUtil.isEmpty(item)) {
                sourceBucketName = item;
                break;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 1; i < copyList.length; i++) {
            result.append(copyList[i]).append("/");
        }
        String sourceObjectKey = result.toString();
        s3Service.copyObject(sourceBucketName, sourceObjectKey, bucketName, objectKey);

        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement(S3Constant.COPY_OBJECT_RESULT);
        Element lastModified = root.addElement(S3Constant.LAST_MODIFIED);
        lastModified.setText(DateUtil.getUTCDateFormat());
        Element eTag = root.addElement(S3Constant.ETAG);
        eTag.setText(EncryptUtil.encryptByMD5(copySource));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(getXml(doc));
    }

    @GetMapping("/{bucketName}/**")
    public void getObject(@PathVariable String bucketName, HttpServletRequest request, HttpServletResponse response) throws Exception {
        bucketName = getBucketName(bucketName);
        String pageUrl = getUrl(request);
        String objectKey = getObjectKey(pageUrl, bucketName, request);
        S3ObjectInputStream objectStream = s3Service.getObject(bucketName, objectKey);
        if (null == objectStream) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(objectStream.getMetadata().getContentType());
        response.setHeader("Content-Disposition", "filename=" + URLEncoder.encode(objectStream.getMetadata().getFileName(), S3Constant.UTF_8));
        response.setCharacterEncoding(S3Constant.UTF_8);
        Integer contentLen = ConvertOp.convert2Int(objectStream.getMetadata().getContentLength());
        response.setContentLength(contentLen);
        byte[] buff = new byte[1024];
        BufferedInputStream bufferedInputStream = null;
        OutputStream outputStream;
        try {
            outputStream = response.getOutputStream();
            bufferedInputStream = new BufferedInputStream(objectStream);
            int i;
            while ((i = bufferedInputStream.read(buff)) != -1) {
                outputStream.write(buff, 0, i);
            }
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bufferedInputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    @DeleteMapping("/{bucketName}/**")
    public ResponseEntity<String> deleteObject(@PathVariable String bucketName, HttpServletRequest request) throws Exception {
        bucketName = getBucketName(bucketName);
        String pageUrl = getUrl(request);
        String objectKey = getObjectKey(pageUrl, bucketName, request);
        s3Service.deleteObject(bucketName, objectKey);
        return ResponseEntity.noContent().build();
    }


    // 开始分片上传
    @PostMapping(value = "/{bucketName}/**", params = "uploads")
    public ResponseEntity<Object> createMultipartUpload(@PathVariable String bucketName, HttpServletRequest request) throws Exception {
        bucketName = getBucketName(bucketName);
        String pageUrl = getUrl(request);
        String objectKey = getObjectKey(pageUrl, bucketName, request);
        InitiateMultipartUploadResult result = s3Service.initiateMultipartUpload(bucketName, objectKey);

        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement(S3Constant.INITIATE_MULTI_PART_UPLOAD_RESULT);
        Element bucket = root.addElement(S3Constant.BUCKET);
        bucket.setText(bucketName);
        Element key = root.addElement(S3Constant.KEY);
        key.setText(objectKey);
        Element uploadId = root.addElement(S3Constant.UPLOAD_ID);
        uploadId.setText(result.getUploadId());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(getXml(doc));
    }

    //分配上传文件
    @PutMapping(value = "/{bucketName}/**", params = {"partNumber", "uploadId"})
    public ResponseEntity<String> uploadPart(@PathVariable String bucketName, HttpServletRequest request, HttpServletResponse response) throws Exception {
        bucketName = getBucketName(bucketName);
        String pageUrl = getUrl(request);
        String objectKey = getObjectKey(pageUrl, bucketName, request);
        int partNumber = ConvertOp.convert2Int(request.getParameter("partNumber"));
        String uploadId = request.getParameter(S3Constant.UPLOAD_ID_PARAM);
        PartETag eTag = s3Service.uploadPart(bucketName, objectKey, partNumber, uploadId, request.getInputStream());
        response.addHeader(S3Constant.ETAG, eTag.geteTag());
        return ResponseEntity.ok().build();
    }

    //结束分片上传
    @PostMapping(value = "/{bucketName}/**", params = "uploadId")
    public ResponseEntity<String> completeMultipartUpload(@PathVariable String bucketName, HttpServletRequest request) throws Exception {
        bucketName = getBucketName(bucketName);
        String pageUrl = getUrl(request);
        String objectKey = getObjectKey(pageUrl, bucketName, request);
        String uploadId = request.getParameter(S3Constant.UPLOAD_ID_PARAM);

        SAXReader reader = new SAXReader();
        Document bodyDoc = reader.read(request.getInputStream());
        Element bodyRoot = bodyDoc.getRootElement();
        List<Element> elementList = bodyRoot.elements("Part");
        List<PartETag> partETags = elementList.stream().map(element -> {
            int partNumber = ConvertOp.convert2Int(element.element("PartNumber").getText());
            String eTag = element.element(S3Constant.ETAG).getText();
            return new PartETag(partNumber, eTag);
        }).collect(Collectors.toList());
        CompleteMultipartUploadResult result = s3Service.completeMultipartUpload(bucketName, objectKey, uploadId, new CompleteMultipartUpload(partETags));

        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement(S3Constant.COMPLETE_MULTI_PART_UPLOAD_RESULT);
        Element location = root.addElement("Location");
        location.setText(CommonUtil.getApiPath() + S3Constant.ENDPOINT + "/" + bucketName + "/" + objectKey);
        Element bucket = root.addElement(S3Constant.BUCKET);
        bucket.setText(bucketName);
        Element key = root.addElement(S3Constant.KEY);
        key.setText(objectKey);
        Element etag = root.addElement(S3Constant.ETAG);
        etag.setText(result.geteTag());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(getXml(doc));
    }

    private String getBucketName(String bucketName) throws UnsupportedEncodingException {
        return URLDecoder.decode(bucketName, S3Constant.UTF_8);
    }

    private String getObjectKey(String url, String bucketName, HttpServletRequest request) {
        return url.replace(request.getContextPath() + S3Constant.ENDPOINT + "/" + bucketName + "/", "");
    }

    private String getUrl(HttpServletRequest request) throws Exception {
        String pageUrl = URLDecoder.decode(request.getRequestURI(), S3Constant.UTF_8);
        return getUrl(pageUrl);
    }

    private String getUrl(String pageUrl) throws Exception {
        if (pageUrl.indexOf("\\?") >= 0) {
            pageUrl = pageUrl.split("\\?")[0];
        }
        return pageUrl;
    }
}
