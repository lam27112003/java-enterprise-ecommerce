import { apiRequest } from '../../../shared/lib/apiClient';
import type { PayResponse, Payment } from '../../../shared/types/payment';

export function createOrReusePayment(orderId: string): Promise<PayResponse> {
  return apiRequest<PayResponse>(`/orders/${encodeURIComponent(orderId)}/pay`, {
    method: 'POST',
  });
}

export function fetchOrderPayment(orderId: string): Promise<Payment> {
  return apiRequest<Payment>(`/orders/${encodeURIComponent(orderId)}/payment`);
}

export function fetchPayment(paymentId: string): Promise<Payment> {
  return apiRequest<Payment>(`/payments/${encodeURIComponent(paymentId)}`);
}
