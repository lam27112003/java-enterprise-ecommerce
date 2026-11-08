import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductListPage } from './ProductListPage';

vi.mock('../api/catalogApi', async () => {
  const actual = await vi.importActual<typeof import('../api/catalogApi')>('../api/catalogApi');
  return {
    ...actual,
    fetchCategories: vi.fn(),
    fetchProducts: vi.fn(),
  };
});

import { fetchCategories, fetchProducts } from '../api/catalogApi';

const fetchCategoriesMock = vi.mocked(fetchCategories);
const fetchProductsMock = vi.mocked(fetchProducts);

function renderList(initial = '/products') {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/products" element={<ProductListPage />} />
        <Route path="/products/:idOrSlug" element={<div>Detail</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProductListPage', () => {
  beforeEach(() => {
    fetchCategoriesMock.mockResolvedValue([]);
    fetchProductsMock.mockResolvedValue({
      content: [
        {
          id: 'p1',
          categoryId: 'c1',
          sku: 'SKU-1',
          name: 'Ceramic Mug',
          slug: 'ceramic-mug',
          priceCents: 1299,
          currency: 'USD',
          status: 'ACTIVE',
          primaryImageUrl: null,
        },
      ],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1,
    });
  });

  it('renders products from the catalog API', async () => {
    renderList();
    expect(await screen.findByText('Ceramic Mug')).toBeInTheDocument();
    expect(screen.getByText('SKU-1')).toBeInTheDocument();
  });

  it('applies search query to the products API', async () => {
    renderList();
    await screen.findByText('Ceramic Mug');
    const input = screen.getByPlaceholderText('Name or SKU');
    fireEvent.change(input, { target: { value: 'mug' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => {
      expect(fetchProductsMock).toHaveBeenCalledWith(
        expect.objectContaining({ q: 'mug', page: 0 }),
      );
    });
  });

  it('shows empty state when API returns no products', async () => {
    fetchProductsMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    });
    renderList();
    expect(await screen.findByText('No products found')).toBeInTheDocument();
  });

  it('shows error state when catalog API fails', async () => {
    fetchProductsMock.mockRejectedValue(new Error('boom'));
    renderList();
    expect(await screen.findByText('Failed to load products')).toBeInTheDocument();
  });
});
