package com.zextras.s3browser.application.usecase;

import com.zextras.s3browser.application.port.S3BrowserPort;
import com.zextras.s3browser.domain.BrowseResult;
import com.zextras.s3browser.domain.BucketItem;
import com.zextras.s3browser.domain.ConnectionSettings;
import com.zextras.s3browser.domain.DownloadedObject;
import com.zextras.s3browser.domain.MultipartUploadItem;
import com.zextras.s3browser.domain.ObjectMetadataDetails;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class S3BrowserUseCase {

    private final S3BrowserPort s3BrowserPort;

    public S3BrowserUseCase(S3BrowserPort s3BrowserPort) {
        this.s3BrowserPort = s3BrowserPort;
    }

    public void verifyConnection(ConnectionSettings settings) {
        s3BrowserPort.verifyConnection(settings);
    }

    public void createBucket(ConnectionSettings settings, String bucketName) {
        s3BrowserPort.createBucket(settings, normalizeBucketName(bucketName));
    }

    public void deleteBucket(ConnectionSettings settings, String bucketName) {
        s3BrowserPort.deleteBucket(settings, normalizeBucketName(bucketName));
    }

    public List<BucketItem> listBuckets(ConnectionSettings settings) {
        return s3BrowserPort.listBuckets(settings);
    }

    public BrowseResult browse(ConnectionSettings settings, String bucket, String prefix) {
        return s3BrowserPort.browse(settings, bucket, normalizePrefix(prefix));
    }

    public ObjectMetadataDetails metadata(ConnectionSettings settings, String bucket, String key) {
        return s3BrowserPort.getMetadata(settings, bucket, key);
    }

    public DownloadedObject download(ConnectionSettings settings, String bucket, String key) {
        return s3BrowserPort.download(settings, bucket, key);
    }

    public void upload(
        ConnectionSettings settings,
        String bucket,
        String prefix,
        String originalFilename,
        byte[] bytes,
        String contentType,
        String storageClass
    ) {
        uploadSinglePart(settings, bucket, resolveUploadKey(prefix, originalFilename), bytes, contentType, normalizeStorageClass(storageClass));
    }

    public String resolveUploadKey(String prefix, String originalFilename) {
        return buildUploadKey(normalizePrefix(prefix), originalFilename);
    }

    public void uploadSinglePart(
        ConnectionSettings settings,
        String bucket,
        String key,
        byte[] bytes,
        String contentType,
        String storageClass
    ) {
        s3BrowserPort.uploadObject(settings, bucket, key, bytes, contentType, normalizeStorageClass(storageClass));
    }

    public void uploadMultipart(
        ConnectionSettings settings,
        String bucket,
        String key,
        Path file,
        String contentType,
        String storageClass,
        long fileSize,
        long partSize
    ) {
        long normalizedPartSize = Math.max(partSize, 5L * 1024L * 1024L);
        s3BrowserPort.multipartUploadObject(
            settings,
            bucket,
            key,
            file,
            contentType,
            normalizeStorageClass(storageClass),
            fileSize,
            normalizedPartSize
        );
    }

    public void createFolder(ConnectionSettings settings, String bucket, String prefix, String folderName) {
        String normalizedPrefix = normalizePrefix(prefix);
        String safeFolderName = normalizeFolderName(folderName);
        s3BrowserPort.createFolder(settings, bucket, normalizedPrefix + safeFolderName + "/");
    }

    public void deleteFolder(ConnectionSettings settings, String bucket, String folderPrefix) {
        if (folderPrefix == null || folderPrefix.isBlank()) {
            throw new IllegalArgumentException("Silinecek klasor secilmedi.");
        }

        String normalized = folderPrefix.endsWith("/") ? folderPrefix : folderPrefix + "/";
        s3BrowserPort.deleteFolderRecursively(settings, bucket, normalized);
    }

    public void copyObject(ConnectionSettings settings, String bucket, String sourceKey, String destinationKey) {
        String source = normalizeObjectKey(sourceKey, "Kaynak dosya secilmedi.");
        String target = normalizeObjectKey(destinationKey, "Hedef dosya anahtari bos olamaz.");
        if (source.equals(target)) {
            throw new IllegalArgumentException("Kaynak ve hedef key ayni olamaz.");
        }

        s3BrowserPort.copyObject(settings, bucket, source, target);
    }

    public void delete(ConnectionSettings settings, String bucket, String key) {
        s3BrowserPort.deleteObject(settings, bucket, key);
    }

    public List<MultipartUploadItem> listMultipartUploads(ConnectionSettings settings, String bucket) {
        return s3BrowserPort.listMultipartUploads(settings, bucket);
    }

    public void abortMultipartUpload(ConnectionSettings settings, String bucket, String key, String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            throw new IllegalArgumentException("UploadId bos olamaz.");
        }
        s3BrowserPort.abortMultipartUpload(settings, bucket, key, uploadId);
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix;
    }

    private String buildUploadKey(String prefix, String originalFilename) {
        String safeName = originalFilename == null ? "upload.bin" : originalFilename.strip();
        if (safeName.isBlank()) {
            safeName = "upload.bin";
        }

        // Some browsers send full client path; keep only the final filename segment.
        safeName = safeName.replace('\\', '/');
        int lastSlash = safeName.lastIndexOf('/');
        if (lastSlash >= 0) {
            safeName = safeName.substring(lastSlash + 1);
        }

        if (safeName.isBlank()) {
            safeName = "upload.bin";
        }

        return prefix + safeName;
    }

    private String normalizeFolderName(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("Klasor adi bos olamaz.");
        }

        String normalized = folderName.strip().replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Gecerli bir klasor adi giriniz.");
        }

        if (normalized.contains("/")) {
            throw new IllegalArgumentException("Klasor adinda '/' kullanmayin.");
        }

        return normalized;
    }

    private String normalizeBucketName(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("Bucket adi bos olamaz.");
        }

        String normalized = bucketName.strip().toLowerCase();
        if (!normalized.matches("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")) {
            throw new IllegalArgumentException("Bucket adi gecersiz. Sadece kucuk harf, rakam, '-' ve '.' kullanin.");
        }

        return normalized;
    }

    private String normalizeStorageClass(String storageClass) {
        if (storageClass == null || storageClass.isBlank()) {
            return "STANDARD";
        }

        String normalized = storageClass.strip().toUpperCase();
        return switch (normalized) {
            case "STANDARD", "STANDARD_IA", "ONEZONE_IA", "INTELLIGENT_TIERING", "GLACIER", "DEEP_ARCHIVE", "GLACIER_IR" -> normalized;
            default -> throw new IllegalArgumentException("Gecersiz storage class: " + storageClass);
        };
    }

    private String normalizeObjectKey(String key, String emptyMessage) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }

        String normalized = key.strip();
        if (normalized.endsWith("/")) {
            throw new IllegalArgumentException("Klasor key'i kopyalanamaz, dosya seciniz.");
        }

        return normalized;
    }
}
