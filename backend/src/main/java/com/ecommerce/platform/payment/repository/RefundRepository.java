package com.ecommerce.platform.payment.repository;

import com.ecommerce.platform.payment.domain.Refund;
import com.ecommerce.platform.payment.domain.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByPaymentIdAndReasonCode(UUID paymentId, String reasonCode);

    Optional<Refund> findByProviderRefundRef(String providerRefundRef);

    long countByPaymentId(UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> lockById(@Param("id") UUID id);

    Page<Refund> findByStatus(RefundStatus status, Pageable pageable);

    long countByStatus(RefundStatus status);

    @Query("""
            select r.currency as currency, coalesce(sum(r.amountCents), 0) as totalCents
              from Refund r
             where r.status = :status
               and r.refundedAt >= :from
               and r.refundedAt < :to
             group by r.currency
            """)
    List<CurrencySum> sumAmountCentsByCurrencyForRefundedAtWindow(
            @Param("status") RefundStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);

    interface CurrencySum {
        String getCurrency();

        Long getTotalCents();
    }
}
