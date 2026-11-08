package com.ecommerce.platform.catalog.api;

import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryResponse;
import com.ecommerce.platform.catalog.service.CategoryService;
import com.ecommerce.platform.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class PublicCategoryController {

    private final CategoryService categoryService;

    public PublicCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> tree() {
        return ApiResponse.ok(categoryService.publicTree());
    }

    @GetMapping("/{idOrSlug}")
    public ApiResponse<CategoryResponse> detail(@PathVariable String idOrSlug) {
        return ApiResponse.ok(categoryService.getPublicByIdOrSlug(idOrSlug));
    }
}
