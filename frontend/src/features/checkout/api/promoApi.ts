import { apiRequest } from '../../../shared/lib/apiClient';
import type { PromotionPreview } from '../../../shared/types/promotion';

export function previewCartPromo(code: string): Promise<PromotionPreview> {
  return apiRequest<PromotionPreview>('/cart/promo', {
    method: 'POST',
    body: { code },
  });
}
