package com.zextras.s3browser.application.usecase;

import com.zextras.s3browser.domain.ConnectionSettings;
import com.zextras.s3browser.domain.UploadJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class UploadAsyncProcessor {

    private final S3BrowserUseCase s3BrowserUseCase;
    private final UploadTrackerService uploadTrackerService;

    @Value("${app.upload.multipart-threshold-bytes:16777216}")
    private long multipartThresholdBytes;

    @Value("${app.upload.multipart-part-size-bytes:8388608}")
    private long multipartPartSizeBytes;

    public UploadAsyncProcessor(S3BrowserUseCase s3BrowserUseCase, UploadTrackerService uploadTrackerService) {
        this.s3BrowserUseCase = s3BrowserUseCase;
        this.uploadTrackerService = uploadTrackerService;
    }

    @Async("uploadTaskExecutor")
    public void process(
        String jobId,
        ConnectionSettings settings,
        String bucket,
        String key,
        Path tempFile,
        String contentType,
        String storageClass,
        long size
    ) {
        UploadJob job = uploadTrackerService.getRequired(jobId);
        job.markRunning();

        try {
            if (size > multipartThresholdBytes) {
                s3BrowserUseCase.uploadMultipart(settings, bucket, key, tempFile, contentType, storageClass, size, multipartPartSizeBytes);
            } else {
                s3BrowserUseCase.uploadSinglePart(settings, bucket, key, Files.readAllBytes(tempFile), contentType, storageClass);
            }

            job.markCompleted();
        } catch (Exception ex) {
            job.markFailed(ex.getMessage() == null ? "Upload basarisiz" : ex.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {
            }
        }
    }
}

