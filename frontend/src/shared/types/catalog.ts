export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

export type ProductSummary = {
  id: string;
  categoryId: string;
  sku: string;
  name: string;
  slug: string;
  priceCents: number;
  currency: string;
  status: ProductStatus;
  primaryImageUrl: string | null;
};

export type ProductImage = {
  id: string;
  url: string;
  storageKey: string;
  contentType: string;
  sizeBytes: number;
  sortOrder: number;
  primary: boolean;
  createdAt: string;
};

export type ProductDetail = {
  id: string;
  categoryId: string;
  categoryName: string;
  categorySlug: string;
  sku: string;
  name: string;
  slug: string;
  description: string | null;
  priceCents: number;
  currency: string;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
  images: ProductImage[];
};

export type Category = {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  parentId: string | null;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  children: Category[];
};

export type ProductSortField = 'name' | 'priceCents' | 'createdAt' | 'sku';
export type SortDirection = 'asc' | 'desc';

export type ProductSearchParams = {
  q?: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  size?: number;
  sort?: ProductSortField;
  direction?: SortDirection;
};
