package com.zextras.s3browser.domain;

import java.time.Instant;

public class UploadJob {

    private final String id;
    private final String bucket;
    private final String key;
    private final long size;
    private final String mode;
    private final String storageClass;
    private final Instant createdAt;

    private volatile UploadStatus status;
    private volatile String message;
    private volatile Instant startedAt;
    private volatile Instant completedAt;

    public UploadJob(String id, String bucket, String key, long size, String mode, String storageClass) {
        this.id = id;
        this.bucket = bucket;
        this.key = key;
        this.size = size;
        this.mode = mode;
        this.storageClass = storageClass;
        this.createdAt = Instant.now();
        this.status = UploadStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }

    public long getSize() {
        return size;
    }

    public String getMode() {
        return mode;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void markRunning() {
        this.status = UploadStatus.RUNNING;
        this.startedAt = Instant.now();
        this.message = null;
    }

    public void markCompleted() {
        this.status = UploadStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.message = null;
    }

    public void markFailed(String message) {
        this.status = UploadStatus.FAILED;
        this.completedAt = Instant.now();
        this.message = message;
    }
}

