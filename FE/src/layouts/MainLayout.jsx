import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import logo from '../assets/logo.svg';
import SearchBox from '../components/SearchBox';
import useAuth from '../hooks/useAuth';
import useCart from '../hooks/useCart';
import useCompare from '../hooks/useCompare';
import useFetch from '../hooks/useFetch';
import { customerService } from '../service/customerService';

export default function MainLayout() {
  const { user, logout, isAdmin, isCustomer } = useAuth();
  const { count } = useCart();
  const { ids } = useCompare();
  const location = useLocation();
  const navigate = useNavigate();
  const { data: notices } = useFetch(
    () => (isCustomer ? customerService.notifications() : Promise.resolve([])),
    [isCustomer, location.pathname]
  );
  const unread = notices?.filter((n) => !n.daDoc).length || 0;

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <>
      <header className="header no-print">
        <div className="container header-inner">
          <Link to="/" className="logo">
            <img src={logo} alt="" /> PhoneNest
          </Link>
          <SearchBox />
          <nav>
            {ids.length > 0 && <Link to="/compare">So sánh ({ids.length})</Link>}
            {!user && (
              <>
                <Link to="/cart">Giỏ hàng ({count})</Link>
                <Link to="/login">Đăng nhập</Link>
                <Link to="/register" className="btn btn-primary">Đăng ký</Link>
              </>
            )}
            {isCustomer && (
              <>
                <Link to="/wishlist">Yêu thích</Link>
                <Link to="/orders">Đơn hàng</Link>
                <Link to="/returns">Đổi trả</Link>
                <Link to="/notifications">🔔{unread > 0 && <b className="dot">{unread}</b>}</Link>
                <Link to="/cart">Giỏ hàng ({count})</Link>
                <Link to="/account">{user.hoTen}</Link>
              </>
            )}
            {isAdmin && <Link to="/admin">Trang quản trị</Link>}
            {user && <button className="btn" onClick={handleLogout}>Đăng xuất</button>}
          </nav>
        </div>
      </header>
      <main className="container main">
        <Outlet />
      </main>
      <footer className="footer no-print">© 2026 PhoneNest - Nhóm 10 - Lập trình WWW (Java)</footer>
    </>
  );
}
