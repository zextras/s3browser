package com.zextras.s3browser.domain;

import java.time.Instant;

public record BucketItem(String name, Instant createdAt) {
}

