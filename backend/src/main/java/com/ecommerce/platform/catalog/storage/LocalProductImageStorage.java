package com.ecommerce.platform.catalog.storage;

import com.ecommerce.platform.catalog.exception.CatalogExceptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.catalog.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalProductImageStorage implements ProductImageStorage {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final CatalogStorageProperties properties;

    public LocalProductImageStorage(CatalogStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredObject store(UUID productId, MultipartFile file) {
        validate(file);
        String extension = extensionFor(file.getContentType());
        String storageKey = productId + "/" + UUID.randomUUID() + extension;
        Path destination = Path.of(properties.getLocalBasePath()).resolve(storageKey).normalize();
        try {
            Files.createDirectories(destination.getParent());
            file.transferTo(destination);
        } catch (IOException ex) {
            throw CatalogExceptions.invalidImage();
        }
        String url = trimSlash(properties.getPublicBaseUrl()) + "/" + storageKey.replace('\\', '/');
        return new StoredObject(storageKey, url, file.getContentType(), file.getSize());
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        Path path = Path.of(properties.getLocalBasePath()).resolve(storageKey).normalize();
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw CatalogExceptions.invalidImage();
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
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
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
