package com.ecommerce.platform.user.repository;

import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from User u
             where lower(u.email) like lower(concat('%', :q, '%'))
                or lower(u.fullName) like lower(concat('%', :q, '%'))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);

    @Query("""
            select count(u) from User u join u.roles r
             where r.code = :roleCode and u.status = :status and u.id <> :excludeId
            """)
    long countActiveAdminsExcluding(
            @Param("roleCode") RoleCode roleCode,
            @Param("status") UserStatus status,
            @Param("excludeId") UUID excludeId);
}
