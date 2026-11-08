package com.ecommerce.platform.catalog.api;

import com.ecommerce.platform.catalog.dto.ProductDtos.ProductResponse;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductSummaryResponse;
import com.ecommerce.platform.catalog.service.ProductService;
import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class PublicProductController {

    private final ProductService productService;

    public PublicProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.ok(productService.searchPublic(q, categoryId, minPrice, maxPrice, page, size, sort, direction));
    }

    @GetMapping("/{idOrSlug}")
    public ApiResponse<ProductResponse> detail(@PathVariable String idOrSlug) {
        return ApiResponse.ok(productService.getPublicByIdOrSlug(idOrSlug));
    }
}
