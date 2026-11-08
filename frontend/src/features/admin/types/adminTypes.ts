import type { OrderStatus } from '../../../shared/types/order';
import type { ProductStatus } from '../../../shared/types/catalog';

export type DashboardSummary = {
  totalProducts: number;
  activeProducts: number;
  lowStockProducts: number;
  openOrders: number;
  ordersAwaitingFulfillment: number;
  refundsRequiringReconciliation: number;
  revenuePeriodFrom: string;
  revenuePeriodTo: string;
  revenuePeriodDescription: string;
  revenueGrossCentsByCurrency: Record<string, number>;
  revenueRefundedCentsByCurrency: Record<string, number>;
  revenueNetCentsByCurrency: Record<string, number>;
  ordersCreatedInWindow: number;
};

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'SUSPENDED';

export type AdminUser = {
  id: string;
  email: string;
  fullName: string;
  status: UserStatus;
  roles: string[];
  createdAt: string;
};

export type ProductRequest = {
  categoryId: string;
  sku: string;
  name: string;
  slug?: string | null;
  description?: string | null;
  priceCents: number;
  currency?: string;
  status?: ProductStatus;
};

export type CategoryRequest = {
  name: string;
  slug?: string | null;
  description?: string | null;
  parentId?: string | null;
  active?: boolean;
  sortOrder?: number;
};

export type InventoryRow = {
  productId: string;
  sku: string;
  productName: string;
  quantityAvailable: number;
  quantityReserved: number;
  version: number;
  updatedAt: string;
};

export type InventoryAdjustRequest = {
  delta: number;
  reason: string;
};

export type InventoryAdjustment = {
  id: string;
  productId: string;
  actorUserId: string;
  delta: number;
  quantityAvailableBefore: number;
  quantityAvailableAfter: number;
  reason: string;
  createdAt: string;
};

export type PromotionType = 'PERCENT' | 'FIXED';

export type Promotion = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  type: PromotionType;
  valueCentsOrPercent: number;
  startsAt: string | null;
  endsAt: string | null;
  usageLimit: number | null;
  usedCount: number;
  minOrderAmountCents: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PromotionRequest = {
  code: string;
  name: string;
  description?: string | null;
  type: PromotionType;
  valueCentsOrPercent: number;
  startsAt?: string | null;
  endsAt?: string | null;
  usageLimit?: number | null;
  minOrderAmountCents?: number;
  active?: boolean;
};

export type AdminStatusUpdateRequest = {
  status: OrderStatus;
  trackingNumber?: string | null;
  carrier?: string | null;
  note?: string | null;
};

export type OrderStatusHistoryEntry = {
  id: string;
  previousStatus: OrderStatus | null;
  newStatus: OrderStatus;
  actorUserId: string | null;
  note: string | null;
  createdAt: string;
};

export type RefundStatus =
  | 'REFUND_PENDING'
  | 'REFUND_SUBMITTED'
  | 'REFUNDED'
  | 'RECONCILIATION_REQUIRED';

export type RefundSummary = {
  id: string;
  paymentId: string;
  orderId: string;
  status: RefundStatus;
  reasonCode: string;
  amountCents: number;
  currency: string;
  providerRefundRef: string | null;
  createdAt: string;
  refundedAt: string | null;
};

export type RefundAudit = {
  id: string;
  actorUserId: string | null;
  action: string;
  previousStatus: string | null;
  newStatus: string | null;
  providerRefundRef: string | null;
  note: string | null;
  createdAt: string;
};

export type RefundDetail = {
  refund: RefundSummary;
  idempotencyKey: string | null;
  paymentOrderId: string;
  paymentAmountCents: number;
  paymentCurrency: string;
  paymentProviderRef: string | null;
  audits: RefundAudit[];
};

export type ConfirmExternalRefundRequest = {
  providerRefundRef: string;
  note?: string | null;
};

export type ReopenAndSubmitRequest = {
  note?: string | null;
};
