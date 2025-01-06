package com.wanggan.locals3.controller;

import com.wanggan.locals3.constant.S3Constant;
import com.wanggan.locals3.model.Result;
import com.wanggan.locals3.util.ConvertOp;
import com.wanggan.locals3.util.S3Util;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.S3Object;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/console")
@ConditionalOnProperty(name = "system.console", havingValue = "true", matchIfMissing = false)
public class ConsoleController {
    @Resource
    S3Util s3Util;

    @PostMapping("/createBucket")
    public Result createBucket(@RequestBody Map<String, Object> params) {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        s3Util.createBucket(bucketName);
        return Result.okResult();
    }

    @PostMapping("/listBucket")
    public Result listBucket() {
        List<Bucket> bucketList = s3Util.getBucketList();
        List<com.wanggan.locals3.model.Bucket> bucketInfoList = bucketList.stream()
                .map(item -> com.wanggan.locals3.model.Bucket.of(item.name(), item.creationDate().toString()))
                .collect(Collectors.toList());
        return Result.okResult().add(S3Constant.OBJ, bucketInfoList);
    }

    @PostMapping("/headBucket")
    public Result headBucket(@RequestBody Map<String, Object> params) {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        boolean checkExist = s3Util.headBucket(bucketName);
        return Result.okResult().add(S3Constant.OBJ, checkExist);
    }

    @PostMapping("/deleteBucket")
    public Result deleteBucket(@RequestBody Map<String, Object> params) {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        s3Util.deleteBucket(bucketName);
        return Result.okResult();
    }

    @PostMapping("/listObjects")
    public Result listObjects(@RequestBody Map<String, Object> params) {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        String prefix = ConvertOp.convert2String(params.get("prefix"));
        JSONArray objectInfoList = new JSONArray();
        List<S3Object> s3ObjectList = s3Util.getObjectList(bucketName, prefix);
        for (S3Object s3Object : s3ObjectList) {
            JSONObject objectInfo = new JSONObject();
            objectInfo.put("key", s3Object.key());
            if (s3Object.lastModified() != null) {
                objectInfo.put("size", s3Object.size());
                objectInfo.put("lastModified", s3Object.lastModified());
            }
            objectInfoList.add(objectInfo);
        }
        return Result.okResult().add(S3Constant.OBJ, objectInfoList);
    }

    @PostMapping("/headObject")
    @ResponseBody
    public Result headObject(@RequestBody Map<String, Object> params) {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        String key = ConvertOp.convert2String(params.get("key"));
        HashMap headInfo = s3Util.headObject(bucketName, key);
        if (headInfo.containsKey("noExist")) {
            return Result.okResult().add(S3Constant.OBJ, false);
        }
        return Result.okResult().add(S3Constant.OBJ, true).add("head", headInfo);
    }

    @PostMapping("/upload")
    public Result upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        String bucketName = request.getParameter("bucketName");
        String key = request.getParameter("key");
        s3Util.upload(bucketName, key, file.getInputStream());
        return Result.okResult();
    }

    @PostMapping("/createMultipartUpload")
    public Result createMultipartUpload(@RequestBody Map<String, Object> params) throws Exception {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        String key = ConvertOp.convert2String(params.get("key"));
        String uploadID = s3Util.createMultipartUpload(bucketName, key);
        return Result.okResult().add("obj", uploadID);
    }

    @PostMapping("/uploadPart")
    public Result uploadPart(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        String bucketName = request.getParameter("bucketName");
        String key = request.getParameter("key");
        String uploadID = request.getParameter("uploadID");
        int partNumber = ConvertOp.convert2Int(request.getParameter("partNumber"));
        String etag = s3Util.uploadPart(bucketName, key, uploadID, partNumber, file.getInputStream());
        return Result.okResult().add(S3Constant.OBJ, etag);
    }

    @PostMapping("/completeMultipartUpload")
    public Result completeMultipartUpload(@RequestBody Map<String, Object> params, HttpServletRequest request) throws Exception {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        String key = ConvertOp.convert2String(params.get("key"));
        String uploadID = ConvertOp.convert2String(params.get("uploadID"));
        List<Map<String, Object>> partArray = (List<Map<String, Object>>) params.get("partList");
        List<CompletedPart> partList = new ArrayList<>();
        for (int i = 0; i < partArray.size(); i++) {
            Map<String, Object> item = partArray.get(i);
            int partNumber = ConvertOp.convert2Int(item.get("partNumber"));
            String eTag = ConvertOp.convert2String(item.get("eTag"));
            CompletedPart completedPart = CompletedPart.builder().partNumber(partNumber).eTag(eTag).build();
            partList.add(completedPart);
        }
        String fileEtag = s3Util.completeMultipartUpload(bucketName, key, uploadID, partList);
        return Result.okResult().add(S3Constant.OBJ, fileEtag);
    }

    @PostMapping("/getFileBytes")
    public Result getFileBytes(@RequestBody Map<String, Object> params) throws Exception {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        String key = ConvertOp.convert2String(params.get("key"));
        byte[] data = s3Util.getFileByte(bucketName, key);
        return Result.okResult().add(S3Constant.OBJ, data);
    }

    @PostMapping("/download")
    public Result download(@RequestBody Map<String, Object> params) throws Exception {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        String key = ConvertOp.convert2String(params.get("key"));
        String url = s3Util.getDownLoadUrl(bucketName, key);
        return Result.okResult().add("obj", url);
    }

    @PostMapping("/delete")
    public Result delete(@RequestBody Map<String, Object> params) throws Exception {
        String bucketName = ConvertOp.convert2String(params.get("bucketName"));
        String key = ConvertOp.convert2String(params.get("key"));
        s3Util.delete(bucketName, key);
        return Result.okResult();
    }

    @PostMapping("/copy")
    public Result copy(@RequestBody Map<String, Object> params) throws Exception {
        String sourceBucketName = ConvertOp.convert2String(params.get("sourceBucketName"));
        String sourceKey = ConvertOp.convert2String(params.get("sourceKey"));
        String targetBucketName = ConvertOp.convert2String(params.get("targetBucketName"));
        String targetKey = ConvertOp.convert2String(params.get("targetKey"));
        s3Util.copyObject(sourceBucketName, sourceKey, targetBucketName, targetKey);
        return Result.okResult();
    }

}
