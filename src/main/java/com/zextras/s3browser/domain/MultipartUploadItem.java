package com.zextras.s3browser.domain;

import java.time.Instant;

public record MultipartUploadItem(
    String uploadId,
    String bucket,
    String key,
    String storageClass,
    Instant initiated
) {}

