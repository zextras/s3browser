package com.zextras.s3browser.domain;

import java.time.Instant;

public record ObjectItem(
    String key,
    String name,
    long size,
    Instant lastModified,
    String storageClass
) {
}

