export type PaymentStatus =
  | 'PENDING_CREATE'
  | 'REQUIRES_ACTION'
  | 'PENDING'
  | 'CANCEL_REQUESTED'
  | 'CANCELED'
  | 'FAILED'
  | 'SUCCEEDED'
  | 'SUCCEEDED_NEEDS_REFUND'
  | 'CREATE_FAILED';

/** Returned only by POST /orders/{id}/pay — includes clientSecret. */
export type PayResponse = {
  id: string;
  orderId: string;
  status: string;
  amountCents: number;
  currency: string;
  providerRef: string | null;
  clientSecret: string;
};

/** GET payment views — never includes clientSecret. */
export type Payment = {
  id: string;
  orderId: string;
  userId: string;
  status: PaymentStatus | string;
  amountCents: number;
  currency: string;
  providerRef: string | null;
  createdAt: string;
  updatedAt: string;
};
