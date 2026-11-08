package com.ecommerce.platform.catalog.service;

import com.ecommerce.platform.catalog.cache.CatalogCacheService;
import com.ecommerce.platform.catalog.domain.Category;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryRequest;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryResponse;
import com.ecommerce.platform.catalog.exception.CatalogExceptions;
import com.ecommerce.platform.catalog.mapper.CatalogMapper;
import com.ecommerce.platform.catalog.repository.CategoryRepository;
import com.ecommerce.platform.catalog.repository.ProductRepository;
import com.ecommerce.platform.catalog.util.SlugUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CatalogCacheService catalogCacheService;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            CatalogCacheService catalogCacheService) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.catalogCacheService = catalogCacheService;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> publicTree() {
        return catalogCacheService.categoryTree(() ->
                CatalogMapper.toTree(categoryRepository.findPublicTreeSource()));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> adminList() {
        return categoryRepository.findAllActiveNotDeleted().stream()
                .map(CatalogMapper::toCategory)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getByIdOrSlug(String idOrSlug) {
        Category category = resolve(idOrSlug);
        if (!category.isActive() && isUuid(idOrSlug)) {
            // admin path uses dedicated method; public detail requires active
        }
        return CatalogMapper.toCategory(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getPublicByIdOrSlug(String idOrSlug) {
        Category category = resolve(idOrSlug);
        if (!category.isActive()) {
            throw CatalogExceptions.categoryNotFound();
        }
        return CatalogMapper.toCategory(category);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String slug = resolveSlug(request.name(), request.slug(), null);
        Category category = new Category(UUID.randomUUID(), request.name().trim(), slug);
        category.setDescription(trimToNull(request.description()));
        category.setActive(request.active() == null || request.active());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setParent(resolveParent(request.parentId(), null));
        Category saved = categoryRepository.save(category);
        catalogCacheService.evictCategories();
        return CatalogMapper.toCategory(saved);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findByIdNotDeleted(id)
                .orElseThrow(CatalogExceptions::categoryNotFound);
        String slug = resolveSlug(request.name(), request.slug(), id);
        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setDescription(trimToNull(request.description()));
        if (request.active() != null) {
            category.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        category.setParent(resolveParent(request.parentId(), id));
        Category saved = categoryRepository.save(category);
        catalogCacheService.evictCategories();
        return CatalogMapper.toCategory(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Category category = categoryRepository.findByIdNotDeleted(id)
                .orElseThrow(CatalogExceptions::categoryNotFound);
        if (categoryRepository.countByParentIdAndDeletedAtIsNull(id) > 0) {
            throw CatalogExceptions.categoryHasChildren();
        }
        if (productRepository.countByCategoryIdAndDeletedAtIsNull(id) > 0) {
            throw CatalogExceptions.categoryHasProducts();
        }
        category.softDelete();
        categoryRepository.save(category);
        catalogCacheService.evictCategories();
    }

    private Category resolve(String idOrSlug) {
        if (isUuid(idOrSlug)) {
            return categoryRepository.findByIdNotDeleted(UUID.fromString(idOrSlug))
                    .orElseThrow(CatalogExceptions::categoryNotFound);
        }
        return categoryRepository.findBySlugNotDeleted(idOrSlug)
                .orElseThrow(CatalogExceptions::categoryNotFound);
    }

    private Category resolveParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return null;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw CatalogExceptions.invalidParent();
        }
        Category parent = categoryRepository.findByIdNotDeleted(parentId)
                .orElseThrow(CatalogExceptions::invalidParent);
        // prevent simple cycles: parent cannot already be under self
        Category cursor = parent.getParent();
        while (cursor != null) {
            if (selfId != null && selfId.equals(cursor.getId())) {
                throw CatalogExceptions.invalidParent();
            }
            cursor = cursor.getParent();
        }
        return parent;
    }

    private String resolveSlug(String name, String requested, UUID excludeId) {
        String slug = requested == null || requested.isBlank()
                ? SlugUtils.slugify(name)
                : SlugUtils.slugify(requested);
        if (slug.isBlank()) {
            throw CatalogExceptions.invalidCategory();
        }
        boolean exists = excludeId == null
                ? categoryRepository.existsBySlugIgnoreCaseAndDeletedAtIsNull(slug)
                : categoryRepository.existsBySlugIgnoreCaseAndIdNotAndDeletedAtIsNull(slug, excludeId);
        if (exists) {
            throw CatalogExceptions.duplicateCategorySlug();
        }
        return slug;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
