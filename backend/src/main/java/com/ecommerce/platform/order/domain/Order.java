package com.ecommerce.platform.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 32)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "subtotal_cents", nullable = false)
    private long subtotalCents;

    @Column(name = "discount_cents", nullable = false)
    private long discountCents;

    @Column(name = "tax_cents", nullable = false)
    private long taxCents;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "shipping_address_json", columnDefinition = "TEXT")
    private String shippingAddressJson;

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(length = 64)
    private String carrier;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "promotion_id")
    private UUID promotionId;

    @Column(name = "promotion_code", length = 64)
    private String promotionCode;

    @Column(name = "inventory_released", nullable = false)
    private boolean inventoryReleased;

    @Column(name = "promotion_usage_restored", nullable = false)
    private boolean promotionUsageRestored;

    @Column(name = "inventory_committed", nullable = false)
    private boolean inventoryCommitted;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(
            UUID id,
            String orderNumber,
            UUID userId,
            OrderStatus status,
            long subtotalCents,
            long discountCents,
            long taxCents,
            long totalCents,
            String currency) {
        if (id == null || orderNumber == null || orderNumber.isBlank() || userId == null) {
            throw new IllegalArgumentException("id, orderNumber, and userId are required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (subtotalCents < 0 || discountCents < 0 || taxCents < 0 || totalCents < 0) {
            throw new IllegalArgumentException("money amounts must be non-negative integer cents");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }
        this.id = id;
        this.orderNumber = orderNumber.trim();
        this.userId = userId;
        this.status = status;
        this.subtotalCents = subtotalCents;
        this.discountCents = discountCents;
        this.taxCents = taxCents;
        this.totalCents = totalCents;
        this.currency = currency.toUpperCase();
        this.inventoryReleased = false;
        this.promotionUsageRestored = false;
        this.inventoryCommitted = false;
    }

    public boolean markInventoryReleasedOnce() {
        if (inventoryReleased) {
            return false;
        }
        inventoryReleased = true;
        return true;
    }

    public boolean markPromotionUsageRestoredOnce() {
        if (promotionUsageRestored) {
            return false;
        }
        promotionUsageRestored = true;
        return true;
    }

    public boolean markInventoryCommittedOnce() {
        if (inventoryCommitted) {
            return false;
        }
        inventoryCommitted = true;
        return true;
    }

    public void markPaidFromPayment() {
        OrderStatusTransitions.requirePaymentPaidTransition(status);
        this.status = OrderStatus.PAID;
        if (paidAt == null) {
            paidAt = Instant.now();
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item is required");
        }
        items.add(item);
        item.setOrder(this);
    }

    public void applyAdminStatus(OrderStatus target) {
        OrderStatusTransitions.requireAllowedAdminTransition(status, target);
        this.status = target;
    }

    public void applyAdminFulfillmentStatus(OrderStatus target, String trackingNumber, String carrier) {
        OrderStatus previous = this.status;
        OrderStatusTransitions.requireAllowedAdminTransition(previous, target);
        if (OrderStatusTransitions.requiresShippedTrackingFields(previous, target)) {
            OrderStatusTransitions.requireShippedTrackingFields(trackingNumber, carrier);
            this.trackingNumber = trackingNumber.trim();
            this.carrier = carrier.trim();
            if (shippedAt == null) {
                shippedAt = Instant.now();
            }
        }
        if (target == OrderStatus.DELIVERED) {
            deliveredAt = Instant.now();
        }
        this.status = target;
    }

    public void applyCustomerCancel() {
        if (!OrderStatusTransitions.isAllowedCustomerCancel(status)) {
            throw new IllegalStateException("Order cannot be cancelled in status " + status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void setPromotionReference(UUID promotionId, String promotionCode) {
        this.promotionId = promotionId;
        this.promotionCode = promotionCode == null ? null : promotionCode.trim();
    }

    public void setShippingAddressJson(String shippingAddressJson) {
        this.shippingAddressJson = shippingAddressJson;
    }

    public UUID getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getSubtotalCents() {
        return subtotalCents;
    }

    public long getDiscountCents() {
        return discountCents;
    }

    public long getTaxCents() {
        return taxCents;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public String getCurrency() {
        return currency;
    }

    public String getShippingAddressJson() {
        return shippingAddressJson;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getCarrier() {
        return carrier;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public String getPromotionCode() {
        return promotionCode;
    }

    public boolean isInventoryReleased() {
        return inventoryReleased;
    }

    public boolean isPromotionUsageRestored() {
        return promotionUsageRestored;
    }

    public boolean isInventoryCommitted() {
        return inventoryCommitted;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
