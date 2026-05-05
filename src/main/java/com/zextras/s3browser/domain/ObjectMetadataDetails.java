package com.zextras.s3browser.domain;

import java.time.Instant;
import java.util.Map;

public record ObjectMetadataDetails(
    String key,
    long size,
    String eTag,
    String contentType,
    Instant lastModified,
    Map<String, String> userMetadata
) {
}

