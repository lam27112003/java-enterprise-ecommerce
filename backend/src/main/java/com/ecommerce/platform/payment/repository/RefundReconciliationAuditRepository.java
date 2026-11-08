package com.ecommerce.platform.payment.repository;

import com.ecommerce.platform.payment.domain.RefundReconciliationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundReconciliationAuditRepository extends JpaRepository<RefundReconciliationAudit, UUID> {

    List<RefundReconciliationAudit> findByRefundIdOrderByCreatedAtAsc(UUID refundId);
}
