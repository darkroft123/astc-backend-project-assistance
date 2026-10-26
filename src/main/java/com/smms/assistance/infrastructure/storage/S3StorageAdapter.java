package com.smms.assistance.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
public class S3StorageAdapter {

    private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

    @ConfigProperty(name = "app.storage.endpoint")
    String endpoint;

    @ConfigProperty(name = "app.storage.public-endpoint", defaultValue = "")
    String publicEndpoint;

    @ConfigProperty(name = "app.storage.access-key")
    String accessKey;

    @ConfigProperty(name = "app.storage.secret-key")
    String secretKey;

    @ConfigProperty(name = "app.storage.region")
    String region;

    @ConfigProperty(name = "app.storage.bucket")
    String bucket;

    private S3Client s3Client;
    private S3Presigner presigner;

    @PostConstruct
    void init() {
        String effectivePublicEndpoint = (publicEndpoint != null && !publicEndpoint.isBlank())
                ? publicEndpoint : endpoint;

        log.info(
                "Initializing S3 client... endpoint={}, publicEndpoint={}, effectivePresignEndpoint={}, region={}, bucket={}",
                endpoint,
                publicEndpoint,
                effectivePublicEndpoint,
                region,
                bucket
        );

        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .forcePathStyle(true)
                .build();

        presigner = S3Presigner.builder()
                .endpointOverride(URI.create(effectivePublicEndpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        log.info("S3 client initialized (internal endpoint). Presigner initialized (public endpoint)");

        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Bucket '{}' already exists", bucket);
        } catch (NoSuchBucketException e) {
            log.warn("Bucket '{}' not found. Creating...", bucket);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Bucket '{}' created successfully", bucket);
        } catch (Exception e) {
            log.error("Error checking/creating bucket '{}'", bucket, e);
            throw e;
        }
    }

    public String upload(String folder, String originalFilename, InputStream data, long size, String contentType) {
        String key = folder + "/" + UUID.randomUUID() + "_" + originalFilename;
        log.info("Uploading file to S3 -> bucket={}, key={}, size={}, contentType={}",
                bucket, key, size, contentType);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromInputStream(data, size));

        log.info("File uploaded successfully -> key={}", key);

        return key;
    }

    public String getPublicUrl(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }

        if (key.startsWith("http://") || key.startsWith("https://")) {
            String bucketPath = "/" + bucket + "/";
            int idx = key.indexOf(bucketPath);
            if (idx >= 0) {
                String extractedKey = key.substring(idx + bucketPath.length());
                int queryIdx = extractedKey.indexOf('?');
                if (queryIdx >= 0) {
                    extractedKey = extractedKey.substring(0, queryIdx);
                }
                log.info("getPublicUrl: extracted key from old URL, re-signing -> key={}", extractedKey);
                return generatePresignedUrl(extractedKey, 60);
            }
            log.info("getPublicUrl: key is an external URL, returning as-is -> {}", key);
            return key;
        }

        log.info("getPublicUrl: raw S3 key, generating presigned URL -> key={}", key);
        return generatePresignedUrl(key, 60);
    }

    public String generatePresignedUrl(String key, int expiryMinutes) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expiryMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        String url = presigner.presignGetObject(presignRequest).url().toString();

        url = normalizeMinioUrl(url);

        log.info("Generated presigned URL -> key={}, expiryMinutes={}, url={}", key, expiryMinutes, url);

        return url;
    }

    private String normalizeMinioUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host == null) return url;

            String newPath = uri.getRawPath();
            String actualHost = host;
            String expectedPrefix = bucket + ".";
            if (host.startsWith(expectedPrefix)) {
                actualHost = host.substring(expectedPrefix.length());
                newPath = "/" + bucket + uri.getRawPath();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(uri.getScheme()).append("://").append(actualHost);
            if (uri.getPort() > 0) sb.append(":").append(uri.getPort());
            sb.append(newPath);
            if (uri.getRawQuery() != null) sb.append("?").append(uri.getRawQuery());

            String normalized = sb.toString();

            if (publicEndpoint != null && !publicEndpoint.isBlank() && endpoint != null && !endpoint.isBlank()) {
                String cleanEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
                String cleanPublic = publicEndpoint.endsWith("/") ? publicEndpoint.substring(0, publicEndpoint.length() - 1) : publicEndpoint;
                normalized = normalized.replace(cleanEndpoint, cleanPublic);
            }

            log.info("Normalized MinIO presigned URL: {} -> {}", url, normalized);
            return normalized;
        } catch (Exception e) {
            log.warn("Error normalizing MinIO presigned URL: {}", e.getMessage());
        }
        return url;
    }

    public String getBucket() {
        return bucket;
    }

    public void delete(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            return;
        }

        String rawKey = keyOrUrl;
        if (keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://")) {
            String bucketPath = "/" + bucket + "/";
            int idx = keyOrUrl.indexOf(bucketPath);
            if (idx >= 0) {
                rawKey = keyOrUrl.substring(idx + bucketPath.length());
                int queryIdx = rawKey.indexOf('?');
                if (queryIdx >= 0) {
                    rawKey = rawKey.substring(0, queryIdx);
                }
            }
        }

        log.info("Deleting file from S3 -> bucket={}, key={}", bucket, rawKey);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(rawKey)
                    .build());
            log.info("File deleted successfully from S3 -> key={}", rawKey);
        } catch (Exception e) {
            log.error("Error deleting file from S3 -> key={}", rawKey, e);
        }
    }
}
