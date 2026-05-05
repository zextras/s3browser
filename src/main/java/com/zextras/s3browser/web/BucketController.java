package com.zextras.s3browser.web;

import com.zextras.s3browser.application.usecase.S3BrowserUseCase;
import com.zextras.s3browser.application.usecase.UploadOrchestratorService;
import com.zextras.s3browser.application.usecase.UploadTrackerService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BucketController {

    private final S3BrowserUseCase s3BrowserUseCase;
    private final UploadOrchestratorService uploadOrchestratorService;
    private final UploadTrackerService uploadTrackerService;
    private final ConnectionSessionService connectionSessionService;

    @Value("${app.upload.multipart-threshold-bytes:16777216}")
    private long multipartThresholdBytes;

    @Value("${app.upload.multipart-part-size-bytes:8388608}")
    private long multipartPartSizeBytes;

    public BucketController(
        S3BrowserUseCase s3BrowserUseCase,
        UploadOrchestratorService uploadOrchestratorService,
        UploadTrackerService uploadTrackerService,
        ConnectionSessionService connectionSessionService
    ) {
        this.s3BrowserUseCase = s3BrowserUseCase;
        this.uploadOrchestratorService = uploadOrchestratorService;
        this.uploadTrackerService = uploadTrackerService;
        this.connectionSessionService = connectionSessionService;
    }

    @GetMapping("/buckets")
    public String buckets(HttpSession session, Model model) {
        var settings = connectionSessionService.getRequired(session);
        model.addAttribute("buckets", s3BrowserUseCase.listBuckets(settings));
        return "buckets";
    }

    @PostMapping("/buckets/create")
    public String createBucket(
        @RequestParam String bucketName,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        var settings = connectionSessionService.getRequired(session);
        s3BrowserUseCase.createBucket(settings, bucketName);
        redirectAttributes.addFlashAttribute("success", "Bucket olusturuldu: " + bucketName);
        return "redirect:/buckets";
    }

    @GetMapping("/buckets/delete")
    public String deleteBucketConfirm(@RequestParam String bucketName, Model model) {
        model.addAttribute("bucketName", bucketName);
        return "delete-bucket-confirm";
    }

    @PostMapping("/buckets/delete")
    public String deleteBucket(
        @RequestParam String bucketName,
        @RequestParam(defaultValue = "false") boolean confirm,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        if (!confirm) {
            redirectAttributes.addFlashAttribute("error", "Silme icin onay vermeniz gerekiyor.");
            return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/delete")
                .queryParam("bucketName", bucketName)
                .toUriString();
        }

        var settings = connectionSessionService.getRequired(session);
        s3BrowserUseCase.deleteBucket(settings, bucketName);
        redirectAttributes.addFlashAttribute("success", "Bucket silindi: " + bucketName);
        return "redirect:/buckets";
    }

    @GetMapping("/buckets/{bucket}")
    public String browse(
        @PathVariable String bucket,
        @RequestParam(required = false, defaultValue = "") String prefix,
        HttpSession session,
        Model model
    ) {
        var settings = connectionSessionService.getRequired(session);
        var result = s3BrowserUseCase.browse(settings, bucket, prefix);
        model.addAttribute("bucket", bucket);
        model.addAttribute("result", result);
        return "browser";
    }

    @GetMapping("/buckets/{bucket}/metadata")
    public String metadata(
        @PathVariable String bucket,
        @RequestParam String key,
        @RequestParam(required = false, defaultValue = "") String prefix,
        HttpSession session,
        Model model
    ) {
        var settings = connectionSessionService.getRequired(session);
        model.addAttribute("bucket", bucket);
        model.addAttribute("prefix", prefix);
        model.addAttribute("metadata", s3BrowserUseCase.metadata(settings, bucket, key));
        return "metadata";
    }

    @GetMapping("/buckets/{bucket}/download")
    public ResponseEntity<byte[]> download(
        @PathVariable String bucket,
        @RequestParam String key,
        HttpSession session
    ) {
        var settings = connectionSessionService.getRequired(session);
        var object = s3BrowserUseCase.download(settings, bucket, key);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + object.filename() + "\"")
            .contentType(MediaType.parseMediaType(object.contentType()))
            .contentLength(object.bytes().length)
            .body(object.bytes());
    }

    @PostMapping("/buckets/{bucket}/upload")
    public String upload(
        @PathVariable String bucket,
        @RequestParam(required = false, defaultValue = "") String prefix,
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "STANDARD") String storageClass,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) throws IOException {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Yuklenecek dosya seciniz.");
            return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}")
                .queryParam("prefix", prefix)
                .buildAndExpand(bucket)
                .toUriString();
        }

        var settings = connectionSessionService.getRequired(session);
        String key = s3BrowserUseCase.resolveUploadKey(prefix, file.getOriginalFilename());
        long size = file.getSize();

        if (size > multipartThresholdBytes) {
            Path tempFile = Files.createTempFile("s3browser-upload-", ".bin");
            try {
                file.transferTo(tempFile);
                s3BrowserUseCase.uploadMultipart(settings, bucket, key, tempFile, file.getContentType(), storageClass, size, multipartPartSizeBytes);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } else {
            s3BrowserUseCase.uploadSinglePart(settings, bucket, key, file.getBytes(), file.getContentType(), storageClass);
        }

        redirectAttributes.addFlashAttribute("success", "Dosya yuklendi: " + key);
        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}")
            .queryParam("prefix", prefix)
            .buildAndExpand(bucket)
            .toUriString();
    }

    @GetMapping("/buckets/{bucket}/multipart-uploads")
    public String multipartUploads(@PathVariable String bucket, HttpSession session, Model model) {
        var settings = connectionSessionService.getRequired(session);
        model.addAttribute("bucket", bucket);
        model.addAttribute("uploads", s3BrowserUseCase.listMultipartUploads(settings, bucket));
        return "uploads";
    }

    @PostMapping("/buckets/{bucket}/multipart-uploads/abort")
    public String abortMultipartUpload(
        @PathVariable String bucket,
        @RequestParam String key,
        @RequestParam String uploadId,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        var settings = connectionSessionService.getRequired(session);
        s3BrowserUseCase.abortMultipartUpload(settings, bucket, key, uploadId);
        redirectAttributes.addFlashAttribute("success", "Multipart upload iptal edildi: " + key);
        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}/multipart-uploads")
            .buildAndExpand(bucket)
            .toUriString();
    }

    @PostMapping("/buckets/{bucket}/folders")
    public String createFolder(
        @PathVariable String bucket,
        @RequestParam(required = false, defaultValue = "") String prefix,
        @RequestParam String folderName,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        var settings = connectionSessionService.getRequired(session);
        s3BrowserUseCase.createFolder(settings, bucket, prefix, folderName);
        redirectAttributes.addFlashAttribute("success", "Klasor olusturuldu: " + folderName);

        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}")
            .queryParam("prefix", prefix)
            .buildAndExpand(bucket)
            .toUriString();
    }

    @GetMapping("/buckets/{bucket}/delete-folder")
    public String deleteFolderConfirm(
        @PathVariable String bucket,
        @RequestParam String folderPrefix,
        @RequestParam(required = false, defaultValue = "") String prefix,
        Model model
    ) {
        model.addAttribute("bucket", bucket);
        model.addAttribute("folderPrefix", folderPrefix);
        model.addAttribute("prefix", prefix);
        return "delete-folder-confirm";
    }

    @PostMapping("/buckets/{bucket}/delete-folder")
    public String deleteFolder(
        @PathVariable String bucket,
        @RequestParam String folderPrefix,
        @RequestParam(required = false, defaultValue = "") String prefix,
        @RequestParam(defaultValue = "false") boolean confirm,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        if (!confirm) {
            redirectAttributes.addFlashAttribute("error", "Silme icin onay vermeniz gerekiyor.");
            return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}/delete-folder")
                .queryParam("folderPrefix", folderPrefix)
                .queryParam("prefix", prefix)
                .buildAndExpand(bucket)
                .toUriString();
        }

        var settings = connectionSessionService.getRequired(session);
        s3BrowserUseCase.deleteFolder(settings, bucket, folderPrefix);
        redirectAttributes.addFlashAttribute("success", "Klasor silindi: " + folderPrefix);
        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}")
            .queryParam("prefix", prefix)
            .buildAndExpand(bucket)
            .toUriString();
    }

    @GetMapping("/buckets/{bucket}/delete")
    public String deleteConfirm(
        @PathVariable String bucket,
        @RequestParam String key,
        @RequestParam(required = false, defaultValue = "") String prefix,
        Model model
    ) {
        model.addAttribute("bucket", bucket);
        model.addAttribute("key", key);
        model.addAttribute("prefix", prefix);
        return "delete-confirm";
    }

    @GetMapping("/buckets/{bucket}/copy")
    public String copyConfirm(
        @PathVariable String bucket,
        @RequestParam String sourceKey,
        @RequestParam(required = false, defaultValue = "") String prefix,
        Model model
    ) {
        model.addAttribute("bucket", bucket);
        model.addAttribute("sourceKey", sourceKey);
        model.addAttribute("destinationKey", sourceKey + ".copy");
        model.addAttribute("prefix", prefix);
        return "copy-confirm";
    }

    @PostMapping("/buckets/{bucket}/copy")
    public String copyObject(
        @PathVariable String bucket,
        @RequestParam String sourceKey,
        @RequestParam String destinationKey,
        @RequestParam(required = false, defaultValue = "") String prefix,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        var settings = connectionSessionService.getRequired(session);
        s3BrowserUseCase.copyObject(settings, bucket, sourceKey, destinationKey);
        redirectAttributes.addFlashAttribute("success", "Kopyalandi: " + sourceKey + " -> " + destinationKey);
        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}")
            .queryParam("prefix", prefix)
            .buildAndExpand(bucket)
            .toUriString();
    }

    @PostMapping("/buckets/{bucket}/delete")
    public String deleteObject(
        @PathVariable String bucket,
        @RequestParam String key,
        @RequestParam(required = false, defaultValue = "") String prefix,
        @RequestParam(defaultValue = "false") boolean confirm,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        if (!confirm) {
            redirectAttributes.addFlashAttribute("error", "Silme icin onay vermeniz gerekiyor.");
            return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}/delete")
                .queryParam("key", key)
                .queryParam("prefix", prefix)
                .buildAndExpand(bucket)
                .toUriString();
        }

        var settings = connectionSessionService.getRequired(session);
        s3BrowserUseCase.delete(settings, bucket, key);
        redirectAttributes.addFlashAttribute("success", "Dosya silindi: " + key);
        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}")
            .queryParam("prefix", prefix)
            .buildAndExpand(bucket)
            .toUriString();
    }
}

