package com.wanggan.locals3.service;

import com.wanggan.locals3.config.SystemConfig;
import com.wanggan.locals3.constant.S3Constant;
import com.wanggan.locals3.model.Bucket;
import com.wanggan.locals3.model.CompleteMultipartUpload;
import com.wanggan.locals3.model.CompleteMultipartUploadResult;
import com.wanggan.locals3.model.InitiateMultipartUploadResult;
import com.wanggan.locals3.model.ObjectMetadata;
import com.wanggan.locals3.model.PartETag;
import com.wanggan.locals3.model.S3Object;
import com.wanggan.locals3.model.S3ObjectInputStream;
import com.wanggan.locals3.util.CommonUtil;
import com.wanggan.locals3.util.DateUtil;
import com.wanggan.locals3.util.EncryptUtil;
import com.wanggan.locals3.util.FileUtil;
import com.wanggan.locals3.util.StringUtil;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Service
public class S3Service {
    @Resource
    SystemConfig systemConfig;
    @Resource
    Tika tika;

    public Bucket createBucket(String bucketName) {
        String dirPath = getBucketPath(bucketName);
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return Bucket.of(bucketName, DateUtil.getDateFormatToSecond(LocalDateTime.now()));
    }

    private String getBucketPath(String bucketName) {
        return systemConfig.getDataPath() + bucketName;
    }

    private String getTempPath(String uploadId, int partNumber) {
        return systemConfig.getTempPath() + "/" + uploadId + "/" + partNumber + ".temp";
    }

    private String getObjPath(String bucketName, String objectKey) {
        return getBucketPath(bucketName) + "/" + objectKey;
    }


    public void deleteBucket(String bucketName) {
        String dirPath = getBucketPath(bucketName);
        FileUtil.delete(dirPath);
    }


    public List<Bucket> listBuckets() {
        List<Bucket> bucketList = new ArrayList<>();
        String dirPath = systemConfig.getDataPath();
        File dir = new File(dirPath);
        if (!dir.exists()) {
            return bucketList;
        }
        File[] fileList = dir.listFiles();
        if (null == fileList) {
            return bucketList;
        }
        for (File file : fileList) {
            if (file.isDirectory()) {
                bucketList.add(Bucket.of(file.getName(), FileUtil.getCreationTime(file.getAbsoluteFile())));
            }
        }
        return bucketList;
    }


    public boolean headBucket(String bucketName) {
        String dirPath = getBucketPath(bucketName);
        File dir = new File(dirPath);
        return dir.exists();
    }


    public List<S3Object> listObjects(String bucketName, String prefix) {
        if (StringUtil.isEmpty(prefix)) {
            prefix = "/";
        } else {
            if (!prefix.startsWith("/")) {
                prefix = "/" + prefix;
            }
        }
        List<S3Object> s3ObjectList = new ArrayList<>();
        String dirPath = systemConfig.getDataPath() + bucketName + prefix;
        File dir = new File(dirPath);
        File[] fileList = dir.listFiles();
        if (null == fileList) {
            return s3ObjectList;
        }
        for (File file : fileList) {
            S3Object s3Object = new S3Object();
            s3Object.setBucketName(bucketName);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setFileName(file.getName());
            if (!file.isDirectory()) {
                s3Object.setKey(file.getName());
                objectMetadata.setContentLength(FileUtil.getFileSize(file));
                objectMetadata.setContentType(getContentType(file));
                //objectMetadata.setContentType(FileUtil.getContentType(file.getName()));
                objectMetadata.setLastModified(FileUtil.getLastModifyTime(file));
            } else {
                s3Object.setKey(file.getName() + "/");
            }
            s3Object.setMetadata(objectMetadata);
            s3ObjectList.add(s3Object);
        }
        return s3ObjectList;
    }


    public HashMap<String, String> headObject(String bucketName, String objectKey) {
        HashMap<String, String> headInfo = new HashMap();
        String filePath = getObjPath(bucketName, objectKey);
        File file = new File(filePath);
        if (file.exists()) {
            try {
                headInfo.put("Content-Disposition", "filename=" + URLEncoder.encode(file.getName(), S3Constant.UTF_8));
                headInfo.put("Content-Length", FileUtil.getFileSize(file) + "");
                headInfo.put("Content-Type", getContentType(file));
                headInfo.put("Last-Modified", FileUtil.getLastModifyTimeGMT(file));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            headInfo.put("NoExist", "1");
        }
        return headInfo;
    }


    public void putObject(String bucketName, String objectKey, InputStream inputStream) {
        createBucket(bucketName);
        String filePath = getObjPath(bucketName, objectKey);
        if (filePath.endsWith("/")) {
            File fileDir = new File(filePath);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }
        } else {
            String[] filePathList = filePath.split("\\/");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < filePathList.length - 1; i++) {
                result.append(filePathList[i]).append("/");
            }
            String fileDirPath = result.toString();
            File fileDir = new File(fileDirPath);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }
            FileUtil.saveFile(filePath, inputStream);
        }
    }


    public void copyObject(String sourceBucketName, String sourceObjectKey, String targetBuckName, String targetObjectKey) {
        String sourceFilePath = systemConfig.getDataPath() + sourceBucketName + "/" + sourceObjectKey;
        createBucket(targetBuckName);
        String targetFilePath = systemConfig.getDataPath() + targetBuckName + "/" + targetObjectKey;
        String[] filePathList = targetFilePath.split("\\/");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < filePathList.length - 1; i++) {
            result.append(filePathList[i]).append("/");
        }
        String fileDirPath = result.toString();
        File fileDir = new File(fileDirPath);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        try {
            FileUtil.copyFile(sourceFilePath, targetFilePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void deleteObject(String bucketName, String objectKey) {
        String filePath = getObjPath(bucketName, objectKey);
        FileUtil.delete(filePath);
    }


    public S3ObjectInputStream getObject(String bucketName, String objectKey) {
        String filePath = getObjPath(bucketName, objectKey);
        File file = new File(filePath);
        if (file.exists()) {
            byte[] fileByte = FileUtil.getFile(filePath);
            InputStream inputStream = new ByteArrayInputStream(fileByte);
            ;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(fileByte.length);
            metadata.setContentType(getContentType(file));
            metadata.setFileName(file.getName());
            metadata.setLastModified(FileUtil.getLastModifyTime(file));
            return new S3ObjectInputStream(metadata, inputStream);
        }
        return null;
    }


    public InitiateMultipartUploadResult initiateMultipartUpload(String bucketName, String objectKey) {
        createBucket(bucketName);
        InitiateMultipartUploadResult multipartUploadResult = new InitiateMultipartUploadResult();
        multipartUploadResult.setBucket(bucketName);
        multipartUploadResult.setObjectKey(objectKey);
        String filePath = getObjPath(bucketName, objectKey);
        String[] filePathList = filePath.split("\\/");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < filePathList.length - 1; i++) {
            result.append(filePathList[i]).append("/");
        }
        String fileDirPath = result.toString();
        File fileDir = new File(fileDirPath);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        String uploadID = CommonUtil.getNewGuid();
        String tempPath = systemConfig.getTempPath() + "/" + uploadID + "/";
        File tempDir = new File(tempPath);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        multipartUploadResult.setUploadId(uploadID);
        return multipartUploadResult;
    }


    public PartETag uploadPart(String bucketName, String objectKey, int partNumber, String uploadId, InputStream inputStream) {
        String tempPartFilePath = getTempPath(uploadId, partNumber);
        File file = new File(tempPartFilePath);
        if (!file.exists()) {
            String filePath = getObjPath(bucketName, objectKey);
            String[] filePathList = filePath.split("\\/");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < filePathList.length - 1; i++) {
                result.append(filePathList[i]).append("/");
            }
            String fileDirPath = result.toString();
            String partFilePath = fileDirPath + partNumber + ".part";
            FileUtil.saveFile(partFilePath, inputStream);
            try {
                FileUtil.writeFile(tempPartFilePath, EncryptUtil.encryptByDES(partFilePath));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String eTag = FileUtil.readFileContent(tempPartFilePath);
        PartETag partETag = new PartETag(partNumber, eTag);
        return partETag;
    }


    public CompleteMultipartUploadResult completeMultipartUpload(String bucketName, String objectKey, String uploadId, CompleteMultipartUpload compMPU) {
        String mergeFilePath = getObjPath(bucketName, objectKey);
        File merageFile = new File(mergeFilePath);
        if (!merageFile.exists()) {
            List<PartETag> partETagList = compMPU.getPartETags();
            boolean check = true;
            for (PartETag partETag : partETagList) {
                String tempPartFilePath = getTempPath(uploadId, partETag.getPartNumber());
                String eTag = FileUtil.readFileContent(tempPartFilePath);
                if (!partETag.geteTag().equals(eTag)) {
                    check = false;
                    break;
                }
            }
            if (check) {
                partETagList.sort(new Comparator<PartETag>() {
                    @Override
                    public int compare(PartETag o1, PartETag o2) {
                        return o1.getPartNumber() - o2.getPartNumber();
                    }
                });
                try {
                    BufferedOutputStream destOutputStream = new BufferedOutputStream(new FileOutputStream(mergeFilePath));
                    for (PartETag partETag : partETagList) {
                        File file = new File(EncryptUtil.decryptByDES(partETag.geteTag()));
                        byte[] fileBuffer = new byte[1024 * 1024 * 5];
                        int readBytesLength;
                        BufferedInputStream sourceInputStream = new BufferedInputStream(new FileInputStream(file));
                        while ((readBytesLength = sourceInputStream.read(fileBuffer)) != -1) {
                            destOutputStream.write(fileBuffer, 0, readBytesLength);
                        }
                        sourceInputStream.close();
                    }
                    destOutputStream.flush();
                    destOutputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        String eTag = "";
        try {
            eTag = EncryptUtil.encryptByMD5(bucketName + "/" + objectKey);
            ;
        } catch (Exception ignored) {

        }
        CompleteMultipartUploadResult completeResult = new CompleteMultipartUploadResult(bucketName, objectKey, eTag);
        return completeResult;
    }

    private String getContentType(File file) {
        try {
            return tika.detect(file);
        } catch (IOException e) {
            return S3Constant.DEFAULT_CONTENT_TYPE;
        }
    }
}
