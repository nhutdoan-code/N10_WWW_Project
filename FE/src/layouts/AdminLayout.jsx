import { useCallback, useEffect, useState } from 'react';
import { adminService } from '../service/adminService';
import { countPending, normalizeRequests } from '../utils/afterSalesAdmin';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import logo from '../assets/logo.svg';
import useAuth from '../hooks/useAuth';

const MENU = [
  { to: 'dashboard', label: 'Thống kê & tìm kiếm' },
  { to: 'products', label: 'Sản phẩm' },
  { to: 'categories', label: 'Loại sản phẩm' },
  { to: 'inventory', label: 'Tồn kho' },
  { to: 'orders', label: 'Đơn hàng' },
  { to: 'returns', label: 'Hậu mãi: đổi trả / bảo hành' },
  { to: 'promotions', label: 'Khuyến mãi' },
  { to: 'shipping', label: 'Phí vận chuyển' },
  { to: 'feedback', label: 'Đánh giá & hỏi đáp' },
  { to: 'accounts', label: 'Tài khoản' },
  { to: 'audit-logs', label: 'Nhật ký hoạt động' },
];

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [pending, setPending] = useState(0);
  const refreshPending = useCallback(async () => {
    try {
      const [returns, afterSales] = await Promise.all([adminService.returns(), adminService.afterSales()]);
      setPending(countPending(normalizeRequests({ returns, afterSales })));
    } catch { /* Giữ menu hoạt động khi API lỗi */ }
  }, []);
  useEffect(() => {
    refreshPending();
    const onFocus = () => refreshPending();
    window.addEventListener('focus', onFocus);
    window.addEventListener('phonenest:after-sales-updated', onFocus);
    const poll = window.setInterval(refreshPending, 60000);
    return () => {
      window.removeEventListener('focus', onFocus);
      window.removeEventListener('phonenest:after-sales-updated', onFocus);
      window.clearInterval(poll);
    };
  }, [refreshPending]);

  return (
    <div className="admin">
      <aside className="sidebar">
        <div className="brand">
          <img src={logo} alt="" />
          <span>PhoneNest</span>
        </div>
        <p className="who">Quản trị · {user.hoTen}</p>
        {MENU.map((m) => (
          <NavLink key={m.to} to={m.to}>{m.label}{m.to === 'returns' && pending > 0 && <span className="as-menu-count" aria-label={`${pending} yêu cầu chờ xử lý`}>{pending}</span>}</NavLink>
        ))}
        <div className="spacer" />
        <Link to="/">Về trang bán hàng</Link>
        <button
          onClick={() => {
            logout();
            navigate('/login');
          }}
        >
          Đăng xuất
        </button>
      </aside>
      <section className="admin-content">
        <Outlet />
      </section>
    </div>
  );
}
