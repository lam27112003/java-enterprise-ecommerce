package com.ecommerce.platform.catalog.repository;

import com.ecommerce.platform.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsBySlugIgnoreCaseAndDeletedAtIsNull(String slug);

    boolean existsBySlugIgnoreCaseAndIdNotAndDeletedAtIsNull(String slug, UUID id);

    @Query("""
            select c from Category c
             left join fetch c.parent
             where c.deletedAt is null
             order by c.sortOrder asc, c.name asc
            """)
    List<Category> findAllActiveNotDeleted();

    @Query("""
            select c from Category c
             left join fetch c.parent
             where c.deletedAt is null and c.active = true
             order by c.sortOrder asc, c.name asc
            """)
    List<Category> findPublicTreeSource();

    @Query("""
            select c from Category c
             left join fetch c.parent
             where c.id = :id and c.deletedAt is null
            """)
    Optional<Category> findByIdNotDeleted(@Param("id") UUID id);

    @Query("""
            select c from Category c
             left join fetch c.parent
             where lower(c.slug) = lower(:slug) and c.deletedAt is null
            """)
    Optional<Category> findBySlugNotDeleted(@Param("slug") String slug);

    long countByParentIdAndDeletedAtIsNull(UUID parentId);
}
