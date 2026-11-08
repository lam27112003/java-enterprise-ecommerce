package com.ecommerce.platform.catalog.service;

import com.ecommerce.platform.catalog.cache.CatalogCacheService;
import com.ecommerce.platform.catalog.domain.Category;
import com.ecommerce.platform.catalog.domain.Product;
import com.ecommerce.platform.catalog.domain.ProductImage;
import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductImageResponse;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductResponse;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductSummaryResponse;
import com.ecommerce.platform.catalog.exception.CatalogExceptions;
import com.ecommerce.platform.catalog.mapper.CatalogMapper;
import com.ecommerce.platform.catalog.repository.CategoryRepository;
import com.ecommerce.platform.catalog.repository.ProductImageRepository;
import com.ecommerce.platform.catalog.repository.ProductRepository;
import com.ecommerce.platform.catalog.storage.ProductImageStorage;
import com.ecommerce.platform.catalog.storage.StoredObject;
import com.ecommerce.platform.catalog.util.SlugUtils;
import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.inventory.service.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final Set<String> SORT_FIELDS = Set.of("name", "priceCents", "createdAt", "sku");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageStorage productImageStorage;
    private final CatalogCacheService catalogCacheService;
    private final InventoryService inventoryService;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductImageRepository productImageRepository,
            ProductImageStorage productImageStorage,
            CatalogCacheService catalogCacheService,
            InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productImageRepository = productImageRepository;
        this.productImageStorage = productImageStorage;
        this.catalogCacheService = catalogCacheService;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> searchPublic(
            String q,
            UUID categoryId,
            Long minPrice,
            Long maxPrice,
            int page,
            int size,
            String sort,
            String direction) {
        Pageable pageable = pageable(page, size, sort, direction);
        Page<Product> result = productRepository.search(
                blankToNull(q),
                categoryId,
                ProductStatus.ACTIVE,
                minPrice,
                maxPrice,
                pageable);
        return PageResponse.from(result.map(CatalogMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> searchAdmin(
            String q,
            UUID categoryId,
            ProductStatus status,
            Long minPrice,
            Long maxPrice,
            int page,
            int size,
            String sort,
            String direction) {
        Pageable pageable = pageable(page, size, sort, direction);
        Page<Product> result = productRepository.search(
                blankToNull(q),
                categoryId,
                status,
                minPrice,
                maxPrice,
                pageable);
        return PageResponse.from(result.map(CatalogMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public ProductResponse getPublicByIdOrSlug(String idOrSlug) {
        if (isUuid(idOrSlug)) {
            UUID id = UUID.fromString(idOrSlug);
            return catalogCacheService.productById(id, () -> {
                Product product = productRepository.findByIdWithDetails(id)
                        .orElseThrow(CatalogExceptions::productNotFound);
                requireActive(product);
                return CatalogMapper.toProduct(product);
            });
        }
        return catalogCacheService.productBySlug(idOrSlug, () -> {
            Product product = productRepository.findBySlugWithDetails(idOrSlug)
                    .orElseThrow(CatalogExceptions::productNotFound);
            requireActive(product);
            return CatalogMapper.toProduct(product);
        });
    }

    @Transactional(readOnly = true)
    public ProductResponse getAdminById(UUID id) {
        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(CatalogExceptions::productNotFound);
        return CatalogMapper.toProduct(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = categoryRepository.findByIdNotDeleted(request.categoryId())
                .orElseThrow(CatalogExceptions::categoryNotFound);
        String sku = request.sku().trim();
        String slug = resolveSlug(request.name(), request.slug(), null);
        if (productRepository.existsBySkuIgnoreCaseAndDeletedAtIsNull(sku)) {
            throw CatalogExceptions.duplicateProductSku();
        }
        Product product = new Product(
                UUID.randomUUID(),
                category,
                sku,
                request.name().trim(),
                slug,
                request.priceCents(),
                currency(request.currency())
        );
        product.setDescription(trimToNull(request.description()));
        product.setStatus(request.status() == null ? ProductStatus.DRAFT : request.status());
        Product saved = productRepository.save(product);
        inventoryService.ensureInventoryExists(saved.getId());
        catalogCacheService.evictProduct(saved.getId(), saved.getSlug());
        return CatalogMapper.toProduct(productRepository.findByIdWithDetails(saved.getId()).orElse(saved));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(CatalogExceptions::productNotFound);
        String previousSlug = product.getSlug();
        Category category = categoryRepository.findByIdNotDeleted(request.categoryId())
                .orElseThrow(CatalogExceptions::categoryNotFound);
        String sku = request.sku().trim();
        String slug = resolveSlug(request.name(), request.slug(), id);
        if (productRepository.existsBySkuIgnoreCaseAndIdNotAndDeletedAtIsNull(sku, id)) {
            throw CatalogExceptions.duplicateProductSku();
        }
        product.setCategory(category);
        product.setSku(sku);
        product.setName(request.name().trim());
        product.setSlug(slug);
        product.setDescription(trimToNull(request.description()));
        product.setPriceCents(request.priceCents());
        product.setCurrency(currency(request.currency()));
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        productRepository.save(product);
        catalogCacheService.evictProduct(id, previousSlug);
        catalogCacheService.evictProduct(id, slug);
        return CatalogMapper.toProduct(productRepository.findByIdWithDetails(id).orElse(product));
    }

    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(CatalogExceptions::productNotFound);
        String slug = product.getSlug();
        product.softDelete();
        productRepository.save(product);
        catalogCacheService.evictProduct(id, slug);
    }

    @Transactional
    public ProductImageResponse addImage(UUID productId, MultipartFile file, boolean primary) {
        Product product = productRepository.findByIdWithDetails(productId)
                .orElseThrow(CatalogExceptions::productNotFound);
        StoredObject stored = productImageStorage.store(productId, file);
        if (primary || product.getImages().isEmpty()) {
            product.clearPrimaryFlags();
        }
        int sortOrder = product.getImages().size();
        ProductImage image = new ProductImage(
                UUID.randomUUID(),
                product,
                stored.storageKey(),
                stored.publicUrl(),
                stored.contentType(),
                stored.sizeBytes(),
                sortOrder,
                primary || product.getImages().isEmpty()
        );
        product.addImage(image);
        productRepository.save(product);
        catalogCacheService.evictProduct(productId, product.getSlug());
        return CatalogMapper.toImage(image);
    }

    @Transactional
    public void deleteImage(UUID productId, UUID imageId) {
        Product product = productRepository.findByIdWithDetails(productId)
                .orElseThrow(CatalogExceptions::productNotFound);
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(CatalogExceptions::imageNotFound);
        product.getImages().remove(image);
        productImageStorage.delete(image.getStorageKey());
        if (image.isPrimary() && !product.getImages().isEmpty()) {
            product.getImages().getFirst().setPrimary(true);
        }
        productRepository.save(product);
        catalogCacheService.evictProduct(productId, product.getSlug());
    }

    private void requireActive(Product product) {
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw CatalogExceptions.productNotFound();
        }
    }

    private String resolveSlug(String name, String requested, UUID excludeId) {
        String slug = requested == null || requested.isBlank()
                ? SlugUtils.slugify(name)
                : SlugUtils.slugify(requested);
        if (slug.isBlank()) {
            throw CatalogExceptions.invalidProduct();
        }
        boolean exists = excludeId == null
                ? productRepository.existsBySlugIgnoreCaseAndDeletedAtIsNull(slug)
                : productRepository.existsBySlugIgnoreCaseAndIdNotAndDeletedAtIsNull(slug, excludeId);
        if (exists) {
            throw CatalogExceptions.duplicateProductSlug();
        }
        return slug;
    }

    private Pageable pageable(int page, int size, String sort, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String sortField = sort == null || !SORT_FIELDS.contains(sort) ? "createdAt" : sort;
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(dir, sortField));
    }

    private static String currency(String value) {
        String currency = value == null || value.isBlank() ? "USD" : value.trim().toUpperCase(Locale.ROOT);
        if (currency.length() != 3) {
            throw CatalogExceptions.invalidProduct();
        }
        return currency;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
