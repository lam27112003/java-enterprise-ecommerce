package com.ecommerce.platform.catalog.repository;

import com.ecommerce.platform.catalog.domain.Product;
import com.ecommerce.platform.catalog.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySkuIgnoreCaseAndDeletedAtIsNull(String sku);

    boolean existsBySlugIgnoreCaseAndDeletedAtIsNull(String slug);

    boolean existsBySkuIgnoreCaseAndIdNotAndDeletedAtIsNull(String sku, UUID id);

    boolean existsBySlugIgnoreCaseAndIdNotAndDeletedAtIsNull(String slug, UUID id);

    @Query("""
            select distinct p from Product p
             join fetch p.category c
             left join fetch p.images
             where p.id = :id and p.deletedAt is null
            """)
    Optional<Product> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            select distinct p from Product p
             join fetch p.category c
             left join fetch p.images
             where lower(p.slug) = lower(:slug) and p.deletedAt is null
            """)
    Optional<Product> findBySlugWithDetails(@Param("slug") String slug);

    @Query(
            value = """
                    select p from Product p
                     join p.category c
                     where p.deletedAt is null
                       and (:status is null or p.status = :status)
                       and (:categoryId is null or c.id = :categoryId)
                       and (:q is null or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
                            or lower(p.sku) like lower(concat('%', cast(:q as string), '%')))
                       and (:minPrice is null or p.priceCents >= :minPrice)
                       and (:maxPrice is null or p.priceCents <= :maxPrice)
                    """,
            countQuery = """
                    select count(p) from Product p
                     join p.category c
                     where p.deletedAt is null
                       and (:status is null or p.status = :status)
                       and (:categoryId is null or c.id = :categoryId)
                       and (:q is null or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
                            or lower(p.sku) like lower(concat('%', cast(:q as string), '%')))
                       and (:minPrice is null or p.priceCents >= :minPrice)
                       and (:maxPrice is null or p.priceCents <= :maxPrice)
                    """
    )
    Page<Product> search(
            @Param("q") String q,
            @Param("categoryId") UUID categoryId,
            @Param("status") ProductStatus status,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable);

    long countByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    long countByStatus(ProductStatus status);
}
