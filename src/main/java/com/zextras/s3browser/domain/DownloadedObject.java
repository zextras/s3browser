package com.zextras.s3browser.domain;

public record DownloadedObject(
    String filename,
    String contentType,
    long contentLength,
    byte[] bytes
) {
}

