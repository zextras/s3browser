package com.zextras.s3browser.infrastructure.aws;

import com.zextras.s3browser.domain.ConnectionSettings;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Component
public class S3ClientFactory {

    public S3Client create(ConnectionSettings settings) {
        var builder = S3Client.builder()
            .region(Region.of(settings.region()))
            .credentialsProvider(resolveCredentials(settings))
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .forcePathStyle(settings.forcePathStyle());

        if (settings.endpointOverride() != null && !settings.endpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(settings.endpointOverride().trim()));
        }

        return builder.build();
    }

    private AwsCredentialsProvider resolveCredentials(ConnectionSettings settings) {
        if (!settings.hasStaticCredentials()) {
            return DefaultCredentialsProvider.create();
        }

        if (settings.sessionToken() != null && !settings.sessionToken().isBlank()) {
            return StaticCredentialsProvider.create(AwsSessionCredentials.create(
                settings.accessKeyId().trim(),
                settings.secretAccessKey().trim(),
                settings.sessionToken().trim()
            ));
        }

        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
            settings.accessKeyId().trim(),
            settings.secretAccessKey().trim()
        ));
    }
}

