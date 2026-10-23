import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import logo from '../assets/logo.svg';
import ChatWidget from '../components/ChatWidget';
import AccountMenu from '../components/AccountMenu';
import Icon from '../components/Icon';
import SearchBox from '../components/SearchBox';
import useAuth from '../hooks/useAuth';
import useCart from '../hooks/useCart';
import useCompare from '../hooks/useCompare';
import useFetch from '../hooks/useFetch';
import { customerService } from '../service/customerService';
import { productService } from '../service/productService';

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
  const { data: categories } = useFetch(productService.categories);
  const unread = notices?.filter((n) => !n.daDoc).length || 0;

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <>
      <div className="topbar no-print">
        <div className="container">
          <span>Chính hãng 100% · Giao nhanh toàn quốc · Đổi trả khi máy lỗi</span>
          <span><Icon name="phone" size={14} /> Hotline 1800 6868</span>
        </div>
      </div>
      <header className="header no-print">
        <div className="container header-inner">
          <Link to="/" className="logo">
            <img src={logo} alt="" />
            <span>PhoneNest</span>
          </Link>
          <SearchBox />
          <nav className="actions">
            {ids.length > 0 && (
              <Link to="/compare" className="pill">
                <Icon name="compare" size={16} /> So sánh <b>{ids.length}</b>
              </Link>
            )}
            {isCustomer && (
              <Link to="/orders" className="nav-item">
                <Icon name="box" /> <span>Đơn hàng</span>
              </Link>
            )}
            {isCustomer && (
              <Link to="/wishlist" className="icon-btn" aria-label="Yêu thích" title="Yêu thích">
                <Icon name="heart" />
              </Link>
            )}
            {isCustomer && (
              <Link to="/notifications" className="icon-btn" aria-label="Thông báo" title="Thông báo">
                <Icon name="bell" />
                {unread > 0 && <span className="badge-dot">{unread}</span>}
              </Link>
            )}
            {!isAdmin && (
              <Link to="/cart" className="nav-item cart-link" aria-label="Giỏ hàng">
                <span className="cart-ico"><Icon name="cart" />{count > 0 && <span className="badge-dot">{count}</span>}</span>
                <span>Giỏ hàng</span>
              </Link>
            )}
            {isCustomer && <AccountMenu user={user} onLogout={handleLogout} />}
            {isAdmin && (
              <>
                <Link to="/admin" className="btn btn-light">Trang quản trị</Link>
                <button className="btn btn-ghost" onClick={handleLogout}>Đăng xuất</button>
              </>
            )}
            {!user && (
              <>
                <Link to="/login" className="btn btn-ghost">Đăng nhập</Link>
                <Link to="/register" className="btn btn-light">Đăng ký</Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <nav className="catnav no-print" aria-label="Danh mục">
        <div className="container">
          <Link to="/" className={location.pathname === '/' && !location.search ? 'on' : ''}>Tất cả điện thoại</Link>
          {categories?.filter((c) => !c.maLoaiCha).map((c) => (
            <Link key={c.maLoai} to={`/?loai=${c.maLoai}`} className={location.search === `?loai=${c.maLoai}` ? 'on' : ''}>{c.tenLoai}</Link>
          ))}
          <Link to="/?gia=1">Dưới 5 triệu</Link>
          <Link to="/?gia=4">Trên 20 triệu</Link>
          <Link to="/compare">So sánh</Link>
        </div>
      </nav>
      <main className="container main">
        <Outlet />
      </main>
      <footer className="footer no-print">
        <div className="container footer-grid">
          <div>
            <div className="logo">
              <img src={logo} alt="" />
              <span>PhoneNest</span>
            </div>
            <p>Điện thoại chính hãng, giá niêm yết rõ ràng. Theo dõi đơn hàng và yêu cầu đổi trả ngay trên website.</p>
          </div>
          <div>
            <h4>Mua sắm</h4>
            <Link to="/">Tất cả sản phẩm</Link>
            <Link to="/compare">So sánh sản phẩm</Link>
            <Link to="/cart">Giỏ hàng</Link>
          </div>
          <div>
            <h4>Tài khoản</h4>
            <Link to="/orders">Đơn hàng của tôi</Link>
            <Link to="/returns">Đổi trả / hoàn tiền</Link>
            <Link to="/account/addresses">Địa chỉ giao hàng</Link>
          </div>
          <div>
            <h4>Liên hệ</h4>
            <span>Hotline: 1800 6868 (8:00 - 21:00)</span>
            <span>Email: no-reply@phonenest.vn</span>
            <span>Hỏi đáp: dùng khung chat góc phải</span>
          </div>
        </div>
        <div className="container footer-bottom">© 2026 PhoneNest · Đồ án Lập trình WWW (Java) · Nhóm 10</div>
      </footer>
      <ChatWidget />
    </>
  );
}
