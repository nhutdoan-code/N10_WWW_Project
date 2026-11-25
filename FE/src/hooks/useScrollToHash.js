import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Cuộn tới phần tử có id = location.hash khi dữ liệu đã sẵn sàng
 * (dùng khi mở trang từ thông báo, vd /products/SP01#hoi-dap, /returns#YC123).
 */
export default function useScrollToHash(ready) {
  const { hash } = useLocation();
  useEffect(() => {
    if (!ready || !hash) return;
    let id;
    try {
      id = decodeURIComponent(hash.slice(1));
    } catch {
      return;
    }
    const el = document.getElementById(id);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    el.classList.add('hash-target');
    const t = setTimeout(() => el.classList.remove('hash-target'), 2400);
    return () => clearTimeout(t);
  }, [ready, hash]);
}
