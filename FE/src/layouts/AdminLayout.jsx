import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import useAuth from '../hooks/useAuth';

const MENU = [
  { to: 'dashboard', label: 'Thống kê & tìm kiếm' },
  { to: 'products', label: 'Sản phẩm' },
  { to: 'categories', label: 'Loại sản phẩm' },
  { to: 'inventory', label: 'Tồn kho' },
  { to: 'orders', label: 'Đơn hàng' },
  { to: 'returns', label: 'Đổi trả / hoàn tiền' },
  { to: 'promotions', label: 'Khuyến mãi' },
  { to: 'feedback', label: 'Đánh giá & hỏi đáp' },
  { to: 'accounts', label: 'Tài khoản' },
];

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="admin">
      <aside className="sidebar">
        <h2>PhoneNest Admin</h2>
        <p className="muted">{user.hoTen}</p>
        {MENU.map((m) => (
          <NavLink key={m.to} to={m.to}>{m.label}</NavLink>
        ))}
        <Link to="/">← Về trang bán hàng</Link>
        <button
          className="btn"
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
