package com.zextras.s3browser.web;

import com.zextras.s3browser.domain.ConnectionSettings;
import jakarta.validation.constraints.NotBlank;

public class ConnectionForm {

    @NotBlank(message = "Region zorunlu")
    private String region = "eu-west-1";
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
    private String endpointOverride;
    private boolean forcePathStyle = true;
    private String defaultBucket;

    public ConnectionSettings toDomain() {
        return new ConnectionSettings(
            region,
            accessKeyId,
            secretAccessKey,
            sessionToken,
            endpointOverride,
            forcePathStyle,
            normalizeOptional(defaultBucket)
        );
    }

    public static ConnectionForm fromDomain(ConnectionSettings settings) {
        ConnectionForm form = new ConnectionForm();
        form.setRegion(settings.region());
        form.setAccessKeyId(settings.accessKeyId());
        form.setSecretAccessKey(settings.secretAccessKey());
        form.setSessionToken(settings.sessionToken());
        form.setEndpointOverride(settings.endpointOverride());
        form.setForcePathStyle(settings.forcePathStyle());
        form.setDefaultBucket(settings.defaultBucket());
        return form;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }

    public void setEndpointOverride(String endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    public boolean isForcePathStyle() {
        return forcePathStyle;
    }

    public void setForcePathStyle(boolean forcePathStyle) {
        this.forcePathStyle = forcePathStyle;
    }

    public String getDefaultBucket() {
        return defaultBucket;
    }

    public void setDefaultBucket(String defaultBucket) {
        this.defaultBucket = defaultBucket;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

