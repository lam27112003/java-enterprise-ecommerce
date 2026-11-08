import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { AppProviders } from './app/providers';
import { clearTokens } from './shared/lib/tokenStorage';

describe('App shell', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    clearTokens();
  });

  it('renders foundation home with platform probe', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        text: async () =>
          JSON.stringify({
            success: true,
            message: null,
            data: {
              name: 'ecommerce-platform',
              environment: 'test',
              phase: '1-foundation',
              status: 'UP',
            },
            timestamp: new Date().toISOString(),
          }),
      }),
    );

    render(
      <AppProviders>
        <App />
      </AppProviders>,
    );

    expect(screen.getAllByText('Enterprise E-commerce').length).toBeGreaterThan(0);
    expect(await screen.findByText('ecommerce-platform')).toBeInTheDocument();
    expect(screen.getByText('1-foundation')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Enterprise E-commerce' })).toBeInTheDocument();
  });
});
