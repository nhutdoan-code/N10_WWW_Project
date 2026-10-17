import { AuthProvider } from './context/AuthContext';
import { CartProvider } from './context/CartContext';
import { CompareProvider } from './context/CompareContext';
import { ToastProvider } from './context/ToastContext';
import AppRoutes from './routes/AppRoutes';

export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <CartProvider>
          <CompareProvider>
            <AppRoutes />
          </CompareProvider>
        </CartProvider>
      </AuthProvider>
    </ToastProvider>
  );
}
