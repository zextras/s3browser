package com.zextras.s3browser.infrastructure.aws;

import com.zextras.s3browser.application.port.S3BrowserPort;
import com.zextras.s3browser.application.usecase.PrefixUtils;
import com.zextras.s3browser.domain.BrowseResult;
import com.zextras.s3browser.domain.BucketItem;
import com.zextras.s3browser.domain.ConnectionSettings;
import com.zextras.s3browser.domain.DownloadedObject;
import com.zextras.s3browser.domain.FolderItem;
import com.zextras.s3browser.domain.ObjectItem;
import com.zextras.s3browser.domain.ObjectMetadataDetails;
import com.zextras.s3browser.domain.MultipartUploadItem;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.StorageClass;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

@Component
public class AwsS3BrowserAdapter implements S3BrowserPort {

    private final S3ClientFactory s3ClientFactory;

    public AwsS3BrowserAdapter(S3ClientFactory s3ClientFactory) {
        this.s3ClientFactory = s3ClientFactory;
    }

    @Override
    public void verifyConnection(ConnectionSettings settings) {
        try (var s3 = s3ClientFactory.create(settings)) {
            if (settings.defaultBucket() == null || settings.defaultBucket().isBlank()) {
                s3.listBuckets();
            } else {
                // Bucket-scoped check avoids requiring ListBuckets permission.
                s3.listObjectsV2(builder -> builder
                    .bucket(settings.defaultBucket())
                    .maxKeys(1));
            }
        }
    }

    @Override
    public void createBucket(ConnectionSettings settings, String bucketName) {
        try (var s3 = s3ClientFactory.create(settings)) {
            try {
                s3.createBucket(createBucketRequest(bucketName, settings.region(), true));
            } catch (S3Exception ex) {
                if (!isLocationConstraintError(ex)) {
                    throw ex;
                }

                // Many S3-compatible endpoints reject explicit location-constraint; retry without it.
                try {
                    s3.createBucket(createBucketRequest(bucketName, settings.region(), false));
                } catch (S3Exception secondEx) {
                    if (!isLocationConstraintError(secondEx) || settings.endpointOverride() == null || settings.endpointOverride().isBlank()) {
                        throw secondEx;
                    }

                    // Last fallback for some S3-compatible services: us-east-1 signer + no location constraint.
                    ConnectionSettings fallbackSettings = new ConnectionSettings(
                        "us-east-1",
                        settings.accessKeyId(),
                        settings.secretAccessKey(),
                        settings.sessionToken(),
                        settings.endpointOverride(),
                        settings.forcePathStyle(),
                        settings.defaultBucket()
                    );

                    try (var fallbackClient = s3ClientFactory.create(fallbackSettings)) {
                        fallbackClient.createBucket(createBucketRequest(bucketName, "us-east-1", false));
                    }
                }
            }
        }
    }

    @Override
    public void deleteBucket(ConnectionSettings settings, String bucketName) {
        try (var s3 = s3ClientFactory.create(settings)) {
            s3.deleteBucket(builder -> builder.bucket(bucketName));
        }
    }

    @Override
    public List<BucketItem> listBuckets(ConnectionSettings settings) {
        try (var s3 = s3ClientFactory.create(settings)) {
            return s3.listBuckets().buckets().stream()
                .map(bucket -> new BucketItem(bucket.name(), bucket.creationDate()))
                .sorted(Comparator.comparing(BucketItem::name))
                .toList();
        }
    }

    @Override
    public BrowseResult browse(ConnectionSettings settings, String bucket, String prefix) {
        try (var s3 = s3ClientFactory.create(settings)) {
            var response = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .delimiter("/")
                .build());

            List<FolderItem> folders = response.commonPrefixes().stream()
                .map(commonPrefix -> new FolderItem(folderName(commonPrefix.prefix()), commonPrefix.prefix()))
                .sorted(Comparator.comparing(FolderItem::name))
                .toList();

            List<ObjectItem> objects = response.contents().stream()
                .filter(item -> !item.key().equals(prefix))
                .filter(item -> !item.key().endsWith("/"))
                .map(item -> new ObjectItem(
                    item.key(),
                    objectName(item.key()),
                    item.size(),
                    item.lastModified(),
                    item.storageClassAsString()
                ))
                .sorted(Comparator.comparing(ObjectItem::name))
                .toList();

            return new BrowseResult(prefix, PrefixUtils.parentPrefix(prefix), folders, objects);
        }
    }

    @Override
    public ObjectMetadataDetails getMetadata(ConnectionSettings settings, String bucket, String key) {
        try (var s3 = s3ClientFactory.create(settings)) {
            try {
                var response = s3.headObject(builder -> builder.bucket(bucket).key(key));
                return new ObjectMetadataDetails(
                    key,
                    response.contentLength(),
                    response.eTag(),
                    response.contentType(),
                    response.lastModified(),
                    response.metadata()
                );
            } catch (S3Exception ex) {
                if (isNotFoundError(ex)) {
                    throw new IllegalArgumentException("Nesne bulunamadi: bucket=" + bucket + ", key=" + key, ex);
                }
                throw ex;
            }
        }
    }

    @Override
    public DownloadedObject download(ConnectionSettings settings, String bucket, String key) {
        try (var s3 = s3ClientFactory.create(settings)) {
            var response = s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());

            String filename = objectName(key);
            if (filename.isBlank()) {
                filename = "download.bin";
            }

            String contentType = response.response().contentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            return new DownloadedObject(filename, contentType, response.response().contentLength(), response.asByteArray());
        }
    }

    @Override
    public void uploadObject(
        ConnectionSettings settings,
        String bucket,
        String key,
        byte[] bytes,
        String contentType,
        String storageClass
    ) {
        try (var s3 = s3ClientFactory.create(settings)) {
            String normalizedType = (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType;
            StorageClass resolvedStorageClass = StorageClass.fromValue(storageClass);

            s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(normalizedType)
                .storageClass(resolvedStorageClass)
                .contentLength((long) bytes.length)
                .build(), RequestBody.fromBytes(bytes));
        }
    }

    @Override
    public void multipartUploadObject(
        ConnectionSettings settings,
        String bucket,
        String key,
        Path file,
        String contentType,
        String storageClass,
        long fileSize,
        long partSize
    ) {
        try (var s3 = s3ClientFactory.create(settings)) {
            String normalizedType = (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType;
            StorageClass resolvedStorageClass = StorageClass.fromValue(storageClass);

            var createResponse = s3.createMultipartUpload(builder -> builder
                .bucket(bucket)
                .key(key)
                .contentType(normalizedType)
                .storageClass(resolvedStorageClass));

            String uploadId = createResponse.uploadId();
            List<CompletedPart> completedParts = new ArrayList<>();

            try (InputStream inputStream = Files.newInputStream(file)) {
                long remaining = fileSize;
                int partNumber = 1;

                while (remaining > 0) {
                    int chunkSize = (int) Math.min(partSize, remaining);
                    byte[] chunk = inputStream.readNBytes(chunkSize);
                    if (chunk.length == 0) {
                        break;
                    }

                    var uploadPartResponse = s3.uploadPart(UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) chunk.length)
                        .build(), RequestBody.fromBytes(chunk));

                    completedParts.add(CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build());

                    remaining -= chunk.length;
                    partNumber++;
                }
            } catch (Exception ex) {
                s3.abortMultipartUpload(builder -> builder
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId));
                throw ex;
            }

            s3.completeMultipartUpload(builder -> builder
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build()));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void createFolder(ConnectionSettings settings, String bucket, String folderPrefix) {
        try (var s3 = s3ClientFactory.create(settings)) {
            s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(folderPrefix)
                .contentType("application/x-directory")
                .contentLength(0L)
                .build(), RequestBody.empty());
        }
    }

    @Override
    public void deleteFolderRecursively(ConnectionSettings settings, String bucket, String folderPrefix) {
        try (var s3 = s3ClientFactory.create(settings)) {
            String continuationToken = null;

            do {
                var listResponse = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(folderPrefix)
                    .continuationToken(continuationToken)
                    .build());

                List<ObjectIdentifier> ids = listResponse.contents().stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();

                if (!ids.isEmpty()) {
                    // S3 deleteObjects accepts up to 1000 keys per request.
                    for (List<ObjectIdentifier> batch : batches(ids, 1000)) {
                        s3.deleteObjects(builder -> builder
                            .bucket(bucket)
                            .delete(Delete.builder().objects(batch).build()));
                    }
                }

                continuationToken = listResponse.nextContinuationToken();
            } while (continuationToken != null);
        }
    }

    @Override
    public void copyObject(ConnectionSettings settings, String bucket, String sourceKey, String destinationKey) {
        try (var s3 = s3ClientFactory.create(settings)) {
            s3.copyObject(CopyObjectRequest.builder()
                .bucket(bucket)
                .key(destinationKey)
                .copySource(encodeCopySource(bucket, sourceKey))
                .build());
        }
    }

    @Override
    public List<MultipartUploadItem> listMultipartUploads(ConnectionSettings settings, String bucket) {
        try (var s3 = s3ClientFactory.create(settings)) {
            return s3.listMultipartUploads(builder -> builder.bucket(bucket))
                .uploads()
                .stream()
                .map(u -> new MultipartUploadItem(
                    u.uploadId(),
                    bucket,
                    u.key(),
                    u.storageClassAsString(),
                    u.initiated()
                ))
                .sorted(Comparator.comparing(MultipartUploadItem::initiated).reversed())
                .toList();
        }
    }

    @Override
    public void abortMultipartUpload(ConnectionSettings settings, String bucket, String key, String uploadId) {
        try (var s3 = s3ClientFactory.create(settings)) {
            s3.abortMultipartUpload(builder -> builder
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId));
        }
    }

    @Override
    public void deleteObject(ConnectionSettings settings, String bucket, String key) {
        try (var s3 = s3ClientFactory.create(settings)) {
            s3.deleteObject(builder -> builder.bucket(bucket).key(key));
        }
    }

    private String folderName(String prefix) {
        String trimmed = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        int lastSlash = trimmed.lastIndexOf('/');
        return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
    }

    private String objectName(String key) {
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
    }

    private List<List<ObjectIdentifier>> batches(List<ObjectIdentifier> items, int size) {
        List<List<ObjectIdentifier>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            int end = Math.min(i + size, items.size());
            chunks.add(items.subList(i, end));
        }
        return chunks;
    }

    private CreateBucketRequest createBucketRequest(String bucketName, String region, boolean withLocationConstraint) {
        CreateBucketRequest.Builder request = CreateBucketRequest.builder().bucket(bucketName);
        if (withLocationConstraint && !"us-east-1".equals(region)) {
            request.createBucketConfiguration(CreateBucketConfiguration.builder()
                .locationConstraint(BucketLocationConstraint.fromValue(region))
                .build());
        }
        return request.build();
    }

    private boolean isLocationConstraintError(S3Exception ex) {
        if (ex.awsErrorDetails() != null && ex.awsErrorDetails().errorCode() != null) {
            String code = ex.awsErrorDetails().errorCode().toLowerCase();
            if (code.contains("location") || code.contains("invalidregion")) {
                return true;
            }
        }

        String message = ex.getMessage();
        if (message == null) {
            return false;
        }

        String lowered = message.toLowerCase();
        return lowered.contains("location-constraint") || lowered.contains("location constraint") || lowered.contains("invalid region");
    }

    private boolean isNotFoundError(S3Exception ex) {
        if (ex.statusCode() == 404) {
            return true;
        }
        if (ex.awsErrorDetails() == null || ex.awsErrorDetails().errorCode() == null) {
            return false;
        }
        String code = ex.awsErrorDetails().errorCode().toLowerCase();
        return code.contains("nosuchkey") || code.contains("notfound");
    }

    private String encodeCopySource(String bucket, String key) {
        String raw = bucket + "/" + key;
        return URLEncoder.encode(raw, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%2F", "/");
    }
}

