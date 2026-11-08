package com.ecommerce.platform.catalog.storage;

public record StoredObject(
        String storageKey,
        String publicUrl,
        String contentType,
        long sizeBytes
) {
}
