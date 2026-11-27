import { useCallback, useEffect, useRef, useState } from 'react';
import { adminService } from '../service/adminService';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import usePageTransition from '../hooks/usePageTransition';
import Logo from '../components/Logo';
import ThemeToggle from '../components/ThemeToggle';
import useAuth from '../hooks/useAuth';

// Nhóm menu theo nghiệp vụ để dễ tìm; đường dẫn giữ nguyên như trước.
const MENU_GROUPS = [
  {
    title: 'Tổng quan',
    items: [
      { to: 'dashboard', label: 'Thống kê & tìm kiếm' },
      { to: 'analytics', label: 'Phân tích hành vi khách hàng' },
    ],
  },
  {
    title: 'Sản phẩm',
    items: [
      { to: 'products', label: 'Sản phẩm' },
      { to: 'approvals', label: 'Phê duyệt bán hàng' },
      { to: 'categories', label: 'Loại sản phẩm' },
      { to: 'inventory', label: 'Tồn kho' },
    ],
  },
  {
    title: 'Bán hàng',
    items: [
      { to: 'orders', label: 'Đơn hàng' },
      { to: 'returns', label: 'Đổi trả & bảo hành' },
      { to: 'promotions', label: 'Khuyến mãi' },
      { to: 'shipping', label: 'Phí vận chuyển' },
    ],
  },
  {
    title: 'Khách hàng & hệ thống',
    items: [
      { to: 'feedback', label: 'Đánh giá & hỏi đáp' },
      { to: 'accounts', label: 'Tài khoản' },
      { to: 'audit-logs', label: 'Nhật ký hoạt động' },
    ],
  },
];

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [pending, setPending] = useState(0);
  const contentRef = useRef(null);
  usePageTransition(contentRef, useLocation().pathname);
  const refreshPending = useCallback(async () => {
    try {
      const summary = await adminService.afterSalesSummary();
      setPending(Number(summary?.waiting || 0));
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
          <Logo to={null} />
          <ThemeToggle className="icon-btn" />
        </div>
        <div className="who">
          <span className="avatar">{user.hoTen?.trim().split(' ').pop()?.[0]?.toUpperCase() || 'A'}</span>
          <span><b>{user.hoTen}</b><small>Quản trị viên</small></span>
        </div>
        <nav className="sidebar-nav" aria-label="Menu quản trị">
          {MENU_GROUPS.map((group) => (
            <div className="sidebar-group" key={group.title}>
              <p className="sidebar-group-title">{group.title}</p>
              {group.items.map((m) => (
                <NavLink key={m.to} to={m.to}>{m.label}{m.to === 'returns' && pending > 0 && <span className="as-menu-count" aria-label={`${pending} yêu cầu chờ xử lý`}>{pending}</span>}</NavLink>
              ))}
            </div>
          ))}
        </nav>
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
      <section className="admin-content" ref={contentRef}>
        <Outlet />
      </section>
    </div>
  );
}
