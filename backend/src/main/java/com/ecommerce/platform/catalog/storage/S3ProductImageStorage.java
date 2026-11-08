package com.ecommerce.platform.catalog.storage;

import com.ecommerce.platform.catalog.exception.CatalogExceptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * S3-ready adapter stub for AWS profiles. Stores metadata URLs pointing at configured public base.
 * Full AWS SDK upload wiring belongs with Phase 10 packaging; this keeps the abstraction swappable.
 */
@Component
@ConditionalOnProperty(name = "app.catalog.storage.type", havingValue = "s3")
public class S3ProductImageStorage implements ProductImageStorage {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final CatalogStorageProperties properties;

    public S3ProductImageStorage(CatalogStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredObject store(UUID productId, MultipartFile file) {
        validate(file);
        if (properties.getS3Bucket() == null || properties.getS3Bucket().isBlank()) {
            throw new IllegalStateException("app.catalog.storage.s3-bucket must be configured for s3 storage");
        }
        String extension = extensionFor(file.getContentType());
        String storageKey = "products/" + productId + "/" + UUID.randomUUID() + extension;
        String base = properties.getS3PublicBaseUrl() == null || properties.getS3PublicBaseUrl().isBlank()
                ? "https://" + properties.getS3Bucket() + ".s3." + properties.getS3Region() + ".amazonaws.com"
                : trimSlash(properties.getS3PublicBaseUrl());
        // Placeholder object URL — replace with SDK putObject when AWS credentials are approved.
        String url = base + "/" + storageKey;
        return new StoredObject(storageKey, url, file.getContentType(), file.getSize());
    }

    @Override
    public void delete(String storageKey) {
        // No-op until AWS SDK deleteObject is wired in Phase 10.
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > properties.getMaxFileSizeBytes()) {
            throw CatalogExceptions.invalidImage();
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(contentType)) {
            throw CatalogExceptions.invalidImage();
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
