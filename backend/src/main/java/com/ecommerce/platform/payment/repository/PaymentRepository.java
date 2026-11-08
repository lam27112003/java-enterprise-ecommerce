package com.ecommerce.platform.payment.repository;

import com.ecommerce.platform.payment.domain.Payment;
import com.ecommerce.platform.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderRef(String providerRef);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerRef = :providerRef")
    Optional<Payment> lockByProviderRef(@Param("providerRef") String providerRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderId = :orderId")
    List<Payment> lockByOrderId(@Param("orderId") UUID orderId);

    @Query("""
            select p.currency as currency, coalesce(sum(p.amountCents), 0) as totalCents
              from Payment p
             where p.status in :statuses
               and p.succeededAt >= :from
               and p.succeededAt < :to
             group by p.currency
            """)
    List<CurrencySum> sumAmountCentsByCurrencyForSucceededAtWindow(
            @Param("statuses") Collection<PaymentStatus> statuses,
            @Param("from") Instant from,
            @Param("to") Instant to);

    interface CurrencySum {
        String getCurrency();

        Long getTotalCents();
    }
}
