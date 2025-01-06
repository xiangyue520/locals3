package com.wanggan.locals3.util;

import com.wanggan.locals3.config.SystemConfig;
import com.wanggan.locals3.constant.S3Constant;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.annotation.Resource;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;

@Component
public class S3Util {
    @Resource
    SystemConfig systemConfig;

    private S3Client getClient() {
        S3Client s3 = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(systemConfig.getUsername(), systemConfig.getPassword())))
                .endpointOverride(URI.create(CommonUtil.getApiPath()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .region(Region.US_EAST_1)
                .build();
        return s3;
    }

    private S3Presigner getPresigner() {
        S3Presigner s3Presigner = S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(systemConfig.getUsername(), systemConfig.getPassword())))
                .endpointOverride(URI.create(CommonUtil.getApiPath() + S3Constant.ENDPOINT + "/"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .region(Region.US_EAST_1)
                .build();
        return s3Presigner;
    }

    public void createBucket(String bucketName) {
        S3Client s3Client = getClient();
        CreateBucketRequest request = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();
        s3Client.createBucket(request);
        s3Client.close();
    }

    public List<Bucket> getBucketList() {
        S3Client s3Client = null;
        try {
            s3Client = getClient();
            ListBucketsRequest request = ListBucketsRequest.builder().build();
            ListBucketsResponse response = s3Client.listBuckets(request);
            return response.buckets();
        } finally {
            if (null != s3Client) {
                s3Client.close();
            }
        }
    }

    public boolean headBucket(String bucketName) {
        S3Client s3Client = getClient();
        boolean checkExist = true;
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            HeadBucketResponse response = s3Client.headBucket(request);
        } catch (NoSuchBucketException e) {
            checkExist = false;
        }
        s3Client.close();
        return checkExist;
    }

    public void deleteBucket(String bucketName) {
        S3Client s3Client = getClient();
        DeleteBucketRequest request = DeleteBucketRequest.builder()
                .bucket(bucketName)
                .build();
        s3Client.deleteBucket(request);
        s3Client.close();
    }


    public List<S3Object> getObjectList(String bucketName, String prefix) {
        S3Client s3Client = getClient();
        ListObjectsRequest request = ListObjectsRequest.builder().bucket(bucketName).prefix(prefix).delimiter("/").build();
        ListObjectsResponse response = s3Client.listObjects(request);
        List<S3Object> s3ObjectList = response.contents();
        s3Client.close();
        return s3ObjectList;
    }

    public HashMap<String, String> headObject(String bucketName, String key) {
        HashMap<String, String> headInfo = new HashMap<>();
        S3Client s3Client = getClient();
        try {
            HeadObjectRequest objectRequest = HeadObjectRequest.builder()
                    .key(key)
                    .bucket(bucketName)
                    .build();
            HeadObjectResponse objectHead = s3Client.headObject(objectRequest);
            s3Client.close();
            headInfo.put("contentType", objectHead.contentType());
            headInfo.put("contentLength", objectHead.contentLength() + "");
            headInfo.put("contentDisposition", objectHead.contentDisposition());
            headInfo.put("lastModified", objectHead.lastModified().toString());
        } catch (NoSuchKeyException e) {
            headInfo.put("noExist", "1");
        }
        return headInfo;
    }

    public void upload(String bucketName, String key, InputStream inputStream) throws Exception {
        S3Client s3Client = getClient();
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(key).build();
        RequestBody requestBody = RequestBody.fromBytes(FileUtil.convertStreamToByte(inputStream));
        s3Client.putObject(request, requestBody);
        s3Client.close();
    }

    public byte[] getFileByte(String bucketName, String key) {
        S3Client s3Client = getClient();
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(request);
        byte[] data = objectBytes.asByteArray();
        s3Client.close();
        return data;
    }

    public String getDownLoadUrl(String bucketName, String key) {
        S3Presigner s3Presigner = getPresigner();
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
            GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(5)).getObjectRequest(getObjectRequest).build();
            PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
            return presignedGetObjectRequest.url().toString();
        } finally {
            s3Presigner.close();
        }
    }

    public void delete(String bucketName, String key) {
        S3Client s3Client = getClient();
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.deleteObject(request);
        s3Client.close();
    }

    public void copyObject(String sourceBucketName, String sourceKey, String targetBucketName, String targetKey) throws Exception {
        S3Client s3Client = getClient();
        CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(sourceBucketName)
                .sourceKey(sourceKey)
                .destinationBucket(targetBucketName)
                .destinationKey(targetKey).build();
        s3Client.copyObject(request);
        s3Client.close();
    }

    public String createMultipartUpload(String bucketName, String key) {
        S3Client s3Client = getClient();
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key).build();
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
        String uploadID = response.uploadId();
        s3Client.close();
        return uploadID;
    }

    public String uploadPart(String bucketName, String key, String uploadID, int partNumber, InputStream inputStream) {
        String eTag = "";
        S3Client s3Client = getClient();
        try {
            byte[] partData = FileUtil.convertStreamToByte(inputStream);
            UploadPartRequest request = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .uploadId(uploadID)
                    .partNumber(partNumber)
                    .contentLength(Long.parseLong(partData.length + ""))
                    .build();
            UploadPartResponse response = s3Client.uploadPart(request, RequestBody.fromBytes(partData));
            eTag = response.eTag();
        } catch (Exception e) {
            e.printStackTrace();
        }
        s3Client.close();
        return eTag;
    }

    public String completeMultipartUpload(String bucketName, String key, String uploadID, List<CompletedPart> partList) {
        S3Client s3Client = getClient();
        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadID)
                .multipartUpload(CompletedMultipartUpload.builder().parts(partList).build())
                .build();
        CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(request);
        s3Client.close();
        return response.eTag();
    }
}
