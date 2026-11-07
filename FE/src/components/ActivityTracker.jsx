import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { trackingService } from '../service/trackingService';

function pageType(pathname, search) {
  if (pathname === '/') return search ? 'PRODUCT_LIST' : 'HOME';
  if (/^\/products\/[^/]+$/.test(pathname)) return 'PRODUCT_DETAIL';
  if (pathname === '/cart') return 'CART';
  if (pathname === '/compare') return 'COMPARE';
  if (pathname === '/login') return 'LOGIN';
  if (pathname === '/register') return 'REGISTER';
  return null;
}

export default function ActivityTracker() {
  const { pathname, search } = useLocation();

  useEffect(() => {
    const page = pageType(pathname, search);
    // Chỉ track các trang chung (HOME, CART, LOGIN, v.v.), tránh trùng với trackView tại ProductDetailPage
    if (page && page !== 'PRODUCT_DETAIL') {
      trackingService.track({
        loaiHanhVi: 'PAGE_VIEW',
        nguonSuKien: page,
      });
    }
  }, [pathname]);

  return null;
}
