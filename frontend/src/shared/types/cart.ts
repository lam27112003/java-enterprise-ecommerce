export type CartItem = {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  quantity: number;
  unitPriceCents: number;
  currency: string;
  lineTotalCents: number;
  available: boolean;
};

export type Cart = {
  id: string;
  userId: string;
  items: CartItem[];
  subtotalCents: number;
  currency: string;
  updatedAt: string;
};

export type AddCartItemRequest = {
  productId: string;
  quantity: number;
};

export type UpdateCartItemRequest = {
  quantity: number;
};
