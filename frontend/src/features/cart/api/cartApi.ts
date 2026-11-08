import { apiRequest } from '../../../shared/lib/apiClient';
import type { AddCartItemRequest, Cart, UpdateCartItemRequest } from '../../../shared/types/cart';

export function fetchCart(): Promise<Cart> {
  return apiRequest<Cart>('/cart');
}

export function addCartItem(request: AddCartItemRequest): Promise<Cart> {
  return apiRequest<Cart>('/cart/items', {
    method: 'POST',
    body: request,
  });
}

export function updateCartItem(itemId: string, request: UpdateCartItemRequest): Promise<Cart> {
  return apiRequest<Cart>(`/cart/items/${encodeURIComponent(itemId)}`, {
    method: 'PATCH',
    body: request,
  });
}

export function removeCartItem(itemId: string): Promise<Cart> {
  return apiRequest<Cart>(`/cart/items/${encodeURIComponent(itemId)}`, {
    method: 'DELETE',
  });
}

export function clearCart(): Promise<Cart> {
  return apiRequest<Cart>('/cart', {
    method: 'DELETE',
  });
}
