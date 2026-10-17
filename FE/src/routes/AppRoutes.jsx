import { Navigate, Route, Routes } from 'react-router-dom';
import AddressesPage from '../pages/AddressesPage';
import ComparePage from '../pages/ComparePage';
import ForgotPasswordPage from '../pages/ForgotPasswordPage';
import InvoicePage from '../pages/InvoicePage';
import NotificationsPage from '../pages/NotificationsPage';
import OrderDetailPage from '../pages/OrderDetailPage';
import ProfilePage from '../pages/ProfilePage';
import ResetPasswordPage from '../pages/ResetPasswordPage';
import ReturnsPage from '../pages/ReturnsPage';
import AdminDashboardPage from '../pages/admin/AdminDashboardPage';
import AdminFeedbackPage from '../pages/admin/AdminFeedbackPage';
import AdminInventoryPage from '../pages/admin/AdminInventoryPage';
import AdminPromotionsPage from '../pages/admin/AdminPromotionsPage';
import AdminReturnsPage from '../pages/admin/AdminReturnsPage';
import AdminLayout from '../layouts/AdminLayout';
import MainLayout from '../layouts/MainLayout';
import CartPage from '../pages/CartPage';
import CheckoutPage from '../pages/CheckoutPage';
import HomePage from '../pages/HomePage';
import LoginPage from '../pages/LoginPage';
import NotFoundPage from '../pages/NotFoundPage';
import OrdersPage from '../pages/OrdersPage';
import ProductDetailPage from '../pages/ProductDetailPage';
import RegisterPage from '../pages/RegisterPage';
import WishlistPage from '../pages/WishlistPage';
import AdminAccountsPage from '../pages/admin/AdminAccountsPage';
import AdminCategoriesPage from '../pages/admin/AdminCategoriesPage';
import AdminOrdersPage from '../pages/admin/AdminOrdersPage';
import AdminProductsPage from '../pages/admin/AdminProductsPage';
import ProtectedRoute from './ProtectedRoute';

export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route index element={<HomePage />} />
        <Route path="products/:ma" element={<ProductDetailPage />} />
        <Route path="cart" element={<CartPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="forgot-password" element={<ForgotPasswordPage />} />
        <Route path="reset-password" element={<ResetPasswordPage />} />
        <Route path="compare" element={<ComparePage />} />
        <Route element={<ProtectedRoute role="CUSTOMER" />}>
          <Route path="checkout" element={<CheckoutPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:ma" element={<OrderDetailPage />} />
          <Route path="orders/:ma/invoice" element={<InvoicePage />} />
          <Route path="wishlist" element={<WishlistPage />} />
          <Route path="account" element={<ProfilePage />} />
          <Route path="account/addresses" element={<AddressesPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="returns" element={<ReturnsPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      <Route path="admin" element={<ProtectedRoute role="ADMIN" />}>
        <Route element={<AdminLayout />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AdminDashboardPage />} />
          <Route path="products" element={<AdminProductsPage />} />
          <Route path="inventory" element={<AdminInventoryPage />} />
          <Route path="promotions" element={<AdminPromotionsPage />} />
          <Route path="returns" element={<AdminReturnsPage />} />
          <Route path="feedback" element={<AdminFeedbackPage />} />
          <Route path="categories" element={<AdminCategoriesPage />} />
          <Route path="orders" element={<AdminOrdersPage />} />
          <Route path="accounts" element={<AdminAccountsPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
