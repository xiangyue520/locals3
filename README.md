locals3

## LocalS3 介绍

**LocalS3** 是一个基于S3协议的本地文件存储服务，无外部组件依赖

1. 符合aws s3兼容协议接入,https://docs.aws.amazon.com/AmazonS3/latest/API/Welcome.html
2. 支持文件类型探测
3. 本地文件目录存储数据
4. 支持aws的v4认证规则
5. 基于jdk8

### 参数信息

| 参数名称	           | 备注                                                         |
|-----------------|------------------------------------------------------------|
| console         | 	是否打开控制调试接口，测试用，详情见ConsoleController                       |
| tempPath        | 	临时文件路径,默认为/opt/locals3/tmp/                               |
| dataPath        | 	文件存储目录,默认为/opt/locals3/data/                              |
| username        | 	凭据ID，对应accessId,默认为admin                                  |
| password        | 	密钥，对应accessSecret,默认为abcd@1234                            |
| externalBuckets | 允许外部访问的buckets列表,只有开启才可以外网访问,默认为useasy-oss,external,多个以逗号分隔 |
| authList        | 账号授权访问列表,只有添加的才能进行认证,启用多个进行处理,配置见示例                        |

```yaml
system:
  authList:
  - accessKey: 123
    accessSecret: 1234
```


## 使用步骤
1. 修改yaml文件的system.dataPath,调整上传的文件存储地址
2. 修改yaml文件的username和password
3. 启动服务,执行`java -jar locals3-[last-version].jar` 命令运行,默认运行端口9000
4. 客户端使用aws的sdk进行接入即可

## 客户端接入

### 引入maven

```xml

<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.20.45</version>
</dependency>
```

### 客户端链接

```java
private S3Client getClient() {
    S3Client s3 = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(systemConfig.getUsername(), systemConfig.getPassword())))
            .endpointOverride(URI.create(CommonUtil.getApiPath()))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
            .region(Region.US_EAST_1)
            .build();
    return s3;
}
```

### 上传文件
```java
 public void upload(String bucketName, String key, InputStream inputStream) throws Exception {
    S3Client s3Client = getClient();
    PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(key).build();
    RequestBody requestBody = RequestBody.fromBytes(FileUtil.convertStreamToByte(inputStream));
    s3Client.putObject(request, requestBody);
    s3Client.close();
}
```

### 获取对象内容
```java
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
```


### 获取对象下载地址
```java
private S3Presigner getPresigner() {
    S3Presigner s3Presigner = S3Presigner.builder()
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(systemConfig.getUsername(), systemConfig.getPassword())))
            .endpointOverride(URI.create(CommonUtil.getApiPath()))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
            .region(Region.US_EAST_1)
            .build();
    return s3Presigner;
}

public String getDownLoadUrl(String bucketName, String key) {
    S3Presigner s3Presigner = getPresigner();
    GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
    GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(5)).getObjectRequest(getObjectRequest).build();
    PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
    String url = presignedGetObjectRequest.url().toString();
    s3Presigner.close();
    return url;
}
```

### 删除对象

```java
public void delete(String bucketName, String key) {
    S3Client s3Client = getClient();
    DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
    s3Client.deleteObject(request);
    s3Client.close();
}
```