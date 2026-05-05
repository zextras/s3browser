package com.zextras.s3browser.application.usecase;

import com.zextras.s3browser.domain.UploadJob;
import com.zextras.s3browser.domain.UploadStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UploadTrackerService {

    private static final int MAX_JOBS = 300;

    private final Map<String, UploadJob> jobs = new ConcurrentHashMap<>();

    public UploadJob createJob(String bucket, String key, long size, String mode, String storageClass) {
        UploadJob job = new UploadJob(UUID.randomUUID().toString(), bucket, key, size, mode, storageClass);
        jobs.put(job.getId(), job);
        trimIfNeeded();
        return job;
    }

    public UploadJob getRequired(String id) {
        UploadJob job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("Upload job bulunamadi: " + id);
        }
        return job;
    }

    public List<UploadJob> allJobs() {
        return jobs.values().stream()
            .sorted(Comparator.comparing(UploadJob::getCreatedAt).reversed())
            .toList();
    }

    public List<UploadJob> pendingJobs() {
        return jobs.values().stream()
            .filter(job -> job.getStatus() == UploadStatus.PENDING || job.getStatus() == UploadStatus.RUNNING)
            .sorted(Comparator.comparing(UploadJob::getCreatedAt).reversed())
            .toList();
    }

    private void trimIfNeeded() {
        if (jobs.size() <= MAX_JOBS) {
            return;
        }

        jobs.values().stream()
            .sorted(Comparator.comparing(UploadJob::getCreatedAt))
            .limit(jobs.size() - MAX_JOBS)
            .map(UploadJob::getId)
            .forEach(jobs::remove);
    }
}

