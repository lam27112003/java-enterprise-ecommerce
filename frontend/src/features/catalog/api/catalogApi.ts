import { apiRequest } from '../../../shared/lib/apiClient';
import type { Category, ProductDetail, ProductSearchParams, ProductSummary } from '../../../shared/types/catalog';
import type { PageResponse } from '../../../shared/types/paging';

function toQuery(params: ProductSearchParams): string {
  const search = new URLSearchParams();
  if (params.q?.trim()) {
    search.set('q', params.q.trim());
  }
  if (params.categoryId) {
    search.set('categoryId', params.categoryId);
  }
  if (params.minPrice != null) {
    search.set('minPrice', String(params.minPrice));
  }
  if (params.maxPrice != null) {
    search.set('maxPrice', String(params.maxPrice));
  }
  search.set('page', String(params.page ?? 0));
  search.set('size', String(params.size ?? 12));
  search.set('sort', params.sort ?? 'createdAt');
  search.set('direction', params.direction ?? 'desc');
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

export function fetchProducts(params: ProductSearchParams = {}): Promise<PageResponse<ProductSummary>> {
  return apiRequest<PageResponse<ProductSummary>>(`/products${toQuery(params)}`, {
    skipAuth: true,
    skipRefresh: true,
  });
}

export function fetchProduct(idOrSlug: string): Promise<ProductDetail> {
  return apiRequest<ProductDetail>(`/products/${encodeURIComponent(idOrSlug)}`, {
    skipAuth: true,
    skipRefresh: true,
  });
}

export function fetchCategories(): Promise<Category[]> {
  return apiRequest<Category[]>('/categories', {
    skipAuth: true,
    skipRefresh: true,
  });
}

export function fetchCategory(idOrSlug: string): Promise<Category> {
  return apiRequest<Category>(`/categories/${encodeURIComponent(idOrSlug)}`, {
    skipAuth: true,
    skipRefresh: true,
  });
}

/** Flatten nested category tree for filter dropdowns. */
export function flattenCategories(nodes: Category[], depth = 0): Array<Category & { depth: number }> {
  const out: Array<Category & { depth: number }> = [];
  for (const node of nodes) {
    out.push({ ...node, depth });
    if (node.children?.length) {
      out.push(...flattenCategories(node.children, depth + 1));
    }
  }
  return out;
}
