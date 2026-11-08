export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'PAYMENT_FAILED'
  | 'CANCELLED';

export type OrderItem = {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  unitPriceCents: number;
  quantity: number;
  lineTotalCents: number;
};

export type Order = {
  id: string;
  orderNumber: string;
  userId: string;
  status: OrderStatus;
  subtotalCents: number;
  discountCents: number;
  taxCents: number;
  totalCents: number;
  currency: string;
  shippingAddressJson: string | null;
  promotionId: string | null;
  promotionCode: string | null;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
  trackingNumber?: string | null;
  carrier?: string | null;
  shippedAt?: string | null;
  deliveredAt?: string | null;
};

export type CheckoutRequest = {
  promotionCode?: string | null;
  shippingAddressJson?: string | null;
};
