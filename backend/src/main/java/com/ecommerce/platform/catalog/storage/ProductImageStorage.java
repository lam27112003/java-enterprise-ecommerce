package com.ecommerce.platform.catalog.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ProductImageStorage {

    StoredObject store(UUID productId, MultipartFile file);

    void delete(String storageKey);
}
