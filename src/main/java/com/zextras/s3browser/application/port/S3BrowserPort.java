package com.zextras.s3browser.application.port;

import com.zextras.s3browser.domain.BrowseResult;
import com.zextras.s3browser.domain.BucketItem;
import com.zextras.s3browser.domain.ConnectionSettings;
import com.zextras.s3browser.domain.DownloadedObject;
import com.zextras.s3browser.domain.MultipartUploadItem;
import com.zextras.s3browser.domain.ObjectMetadataDetails;

import java.nio.file.Path;
import java.util.List;

public interface S3BrowserPort {

    void verifyConnection(ConnectionSettings settings);

    void createBucket(ConnectionSettings settings, String bucketName);

    void deleteBucket(ConnectionSettings settings, String bucketName);

    List<BucketItem> listBuckets(ConnectionSettings settings);

    BrowseResult browse(ConnectionSettings settings, String bucket, String prefix);

    ObjectMetadataDetails getMetadata(ConnectionSettings settings, String bucket, String key);

    DownloadedObject download(ConnectionSettings settings, String bucket, String key);

    void uploadObject(ConnectionSettings settings, String bucket, String key, byte[] bytes, String contentType, String storageClass);

    void multipartUploadObject(
        ConnectionSettings settings,
        String bucket,
        String key,
        Path file,
        String contentType,
        String storageClass,
        long fileSize,
        long partSize
    );

    void createFolder(ConnectionSettings settings, String bucket, String folderPrefix);

    void deleteFolderRecursively(ConnectionSettings settings, String bucket, String folderPrefix);

    void copyObject(ConnectionSettings settings, String bucket, String sourceKey, String destinationKey);

    List<MultipartUploadItem> listMultipartUploads(ConnectionSettings settings, String bucket);

    void abortMultipartUpload(ConnectionSettings settings, String bucket, String key, String uploadId);

    void deleteObject(ConnectionSettings settings, String bucket, String key);
}
