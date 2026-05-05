package com.zextras.s3browser.application.usecase;

import com.zextras.s3browser.domain.ConnectionSettings;
import com.zextras.s3browser.domain.UploadJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class UploadOrchestratorService {

    private final S3BrowserUseCase s3BrowserUseCase;
    private final UploadTrackerService uploadTrackerService;
    private final UploadAsyncProcessor uploadAsyncProcessor;

    @Value("${app.upload.multipart-threshold-bytes:16777216}")
    private long multipartThresholdBytes;

    public UploadOrchestratorService(
        S3BrowserUseCase s3BrowserUseCase,
        UploadTrackerService uploadTrackerService,
        UploadAsyncProcessor uploadAsyncProcessor
    ) {
        this.s3BrowserUseCase = s3BrowserUseCase;
        this.uploadTrackerService = uploadTrackerService;
        this.uploadAsyncProcessor = uploadAsyncProcessor;
    }

    public UploadJob submit(
        ConnectionSettings settings,
        String bucket,
        String prefix,
        String originalFilename,
        Path tempFile,
        String contentType,
        String storageClass,
        long size
    ) {
        String key = s3BrowserUseCase.resolveUploadKey(prefix, originalFilename);
        String mode = size > multipartThresholdBytes ? "MULTIPART" : "SINGLE";

        UploadJob job = uploadTrackerService.createJob(bucket, key, size, mode, storageClass);
        uploadAsyncProcessor.process(job.getId(), settings, bucket, key, tempFile, contentType, storageClass, size);
        return job;
    }
}

