import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { fetchProducts, flattenCategories } from './catalogApi';
import type { Category } from '../../../shared/types/catalog';

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  } as Response;
}

describe('catalogApi', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('requests products with search, category, sort, and pagination params', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, {
        success: true,
        message: null,
        data: {
          content: [],
          page: 1,
          size: 12,
          totalElements: 0,
          totalPages: 0,
        },
        timestamp: '',
      }),
    );

    await fetchProducts({
      q: 'mug',
      categoryId: 'cat-1',
      page: 1,
      size: 12,
      sort: 'priceCents',
      direction: 'asc',
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('/products?');
    expect(url).toContain('q=mug');
    expect(url).toContain('categoryId=cat-1');
    expect(url).toContain('page=1');
    expect(url).toContain('sort=priceCents');
    expect(url).toContain('direction=asc');
  });

  it('flattens nested categories for filters', () => {
    const tree: Category[] = [
      {
        id: '1',
        name: 'Root',
        slug: 'root',
        description: null,
        parentId: null,
        active: true,
        sortOrder: 0,
        createdAt: '',
        updatedAt: '',
        children: [
          {
            id: '2',
            name: 'Child',
            slug: 'child',
            description: null,
            parentId: '1',
            active: true,
            sortOrder: 0,
            createdAt: '',
            updatedAt: '',
            children: [],
          },
        ],
      },
    ];
    const flat = flattenCategories(tree);
    expect(flat.map((c) => ({ id: c.id, depth: c.depth }))).toEqual([
      { id: '1', depth: 0 },
      { id: '2', depth: 1 },
    ]);
  });
});
