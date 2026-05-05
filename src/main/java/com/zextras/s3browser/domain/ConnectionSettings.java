package com.zextras.s3browser.domain;

public record ConnectionSettings(
    String region,
    String accessKeyId,
    String secretAccessKey,
    String sessionToken,
    String endpointOverride,
    boolean forcePathStyle,
    String defaultBucket
) {
    public boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank() &&
            secretAccessKey != null && !secretAccessKey.isBlank();
    }
}

