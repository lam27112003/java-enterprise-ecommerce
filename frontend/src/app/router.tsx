import { Route, Routes } from 'react-router-dom';
import { AdminCategoriesPage } from '../features/admin/pages/AdminCategoriesPage';
import { AdminCategoryFormPage } from '../features/admin/pages/AdminCategoryFormPage';
import { AdminInventoryPage } from '../features/admin/pages/AdminInventoryPage';
import { AdminOrderDetailPage } from '../features/admin/pages/AdminOrderDetailPage';
import { AdminOrdersPage } from '../features/admin/pages/AdminOrdersPage';
import { AdminProductFormPage } from '../features/admin/pages/AdminProductFormPage';
import { AdminProductsPage } from '../features/admin/pages/AdminProductsPage';
import { AdminPromotionFormPage } from '../features/admin/pages/AdminPromotionFormPage';
import { AdminPromotionsPage } from '../features/admin/pages/AdminPromotionsPage';
import { AdminRefundDetailPage } from '../features/admin/pages/AdminRefundDetailPage';
import { AdminRefundsPage } from '../features/admin/pages/AdminRefundsPage';
import { AdminUsersPage } from '../features/admin/pages/AdminUsersPage';
import { DashboardPage } from '../features/admin/pages/DashboardPage';
import { AdminRoute } from '../features/auth/components/AdminRoute';
import { CustomerRoute } from '../features/auth/components/CustomerRoute';
import { RequireAuth } from '../features/auth/components/RequireAuth';
import { AccountPage } from '../features/auth/pages/AccountPage';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { CartPage } from '../features/cart/pages/CartPage';
import { CategoryPage } from '../features/catalog/pages/CategoryPage';
import { ProductDetailPage } from '../features/catalog/pages/ProductDetailPage';
import { ProductListPage } from '../features/catalog/pages/ProductListPage';
import { CheckoutPage } from '../features/checkout/pages/CheckoutPage';
import { PayOrderPage } from '../features/checkout/pages/PayOrderPage';
import { OrderDetailPage } from '../features/orders/pages/OrderDetailPage';
import { OrdersPage } from '../features/orders/pages/OrdersPage';
import { HomePage } from '../pages/HomePage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { UnauthorizedPage } from '../pages/UnauthorizedPage';
import { AdminLayout } from './layouts/AdminLayout';
import { AuthLayout } from './layouts/AuthLayout';
import { CustomerLayout } from './layouts/CustomerLayout';

export function AppRouter() {
  return (
    <Routes>
      <Route element={<CustomerLayout />}>
        <Route index element={<HomePage />} />
        <Route path="products" element={<ProductListPage />} />
        <Route path="products/:idOrSlug" element={<ProductDetailPage />} />
        <Route path="categories/:slug" element={<CategoryPage />} />
        <Route path="unauthorized" element={<UnauthorizedPage />} />

        <Route element={<CustomerRoute />}>
          <Route path="cart" element={<CartPage />} />
          <Route path="checkout" element={<CheckoutPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:id" element={<OrderDetailPage />} />
          <Route path="orders/:id/pay" element={<PayOrderPage />} />
        </Route>

        <Route element={<RequireAuth />}>
          <Route path="account" element={<AccountPage />} />
        </Route>
      </Route>

      <Route element={<AuthLayout />}>
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
      </Route>

      <Route path="admin" element={<AdminRoute />}>
        <Route element={<AdminLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="products" element={<AdminProductsPage />} />
          <Route path="products/new" element={<AdminProductFormPage />} />
          <Route path="products/:id/edit" element={<AdminProductFormPage />} />
          <Route path="categories" element={<AdminCategoriesPage />} />
          <Route path="categories/new" element={<AdminCategoryFormPage />} />
          <Route path="categories/:id/edit" element={<AdminCategoryFormPage />} />
          <Route path="inventory" element={<AdminInventoryPage />} />
          <Route path="promotions" element={<AdminPromotionsPage />} />
          <Route path="promotions/new" element={<AdminPromotionFormPage />} />
          <Route path="promotions/:id/edit" element={<AdminPromotionFormPage />} />
          <Route path="orders" element={<AdminOrdersPage />} />
          <Route path="orders/:id" element={<AdminOrderDetailPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="refunds" element={<AdminRefundsPage />} />
          <Route path="refunds/:id" element={<AdminRefundDetailPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
