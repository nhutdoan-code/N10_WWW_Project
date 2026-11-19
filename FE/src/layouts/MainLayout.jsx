import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import ChatWidget from '../components/ChatWidget';
import AccountMenu from '../components/AccountMenu';
import Icon from '../components/Icon';
import Logo from '../components/Logo';
import ThemeToggle from '../components/ThemeToggle';
import SearchBox from '../components/SearchBox';
import useAuth from '../hooks/useAuth';
import useCart from '../hooks/useCart';
import useCompare from '../hooks/useCompare';
import useFetch from '../hooks/useFetch';
import usePageTransition from '../hooks/usePageTransition';
import useScrollReveal from '../hooks/useScrollReveal';
import { customerService } from '../service/customerService';
import { productService } from '../service/productService';
import { bestDeals, brandsOnSale, popularLines } from '../utils/catalogInsights';
import { formatPrice } from '../utils/format';

export default function MainLayout() {
  const [categoriesOpen, setCategoriesOpen] = useState(false);
  const [hoverDismissed, setHoverDismissed] = useState(false);
  const categoriesRef = useRef(null);
  const mainRef = useRef(null);
  const [scrolled, setScrolled] = useState(false);
  const { user, logout, isAdmin, isCustomer } = useAuth();
  const { count, reset: resetCart } = useCart();
  const { ids } = useCompare();
  const location = useLocation();
  const navigate = useNavigate();
  const { data: notices } = useFetch(
    () => (isCustomer ? customerService.notifications() : Promise.resolve([])),
    [isCustomer, location.pathname]
  );
  // Dữ liệu menu: thương hiệu và giá tốt tính từ sản phẩm đang mở bán, dòng tìm nhiều từ dữ liệu hành vi.
  const { data: allProducts } = useFetch(() => productService.list({}));
  const { data: popular } = useFetch(() => productService.popular(12).catch(() => null));
  const unread = notices?.filter((n) => !n.daDoc).length || 0;
  const menuBrands = brandsOnSale(allProducts || []);
  const menuPopular = popularLines(popular, allProducts || [], 6);
  const menuDeals = bestDeals(allProducts || [], 6);
  const closeCategoriesByClick = () => {
    setCategoriesOpen(false);
    setHoverDismissed(true);
  };

  useEffect(() => {
    const closeCategories = (event) => {
      if (!categoriesRef.current?.contains(event.target)) setCategoriesOpen(false);
    };
    document.addEventListener('mousedown', closeCategories);
    return () => document.removeEventListener('mousedown', closeCategories);
  }, []);

  usePageTransition(mainRef, location.pathname);
  useScrollReveal(mainRef);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const handleLogout = () => {
    resetCart();
    logout();
    navigate('/');
  };

  return (
    <>
      <div className="topbar no-print">
        <div className="container">
          <ul className="topbar-perks">
            <li>Hàng chính hãng 100%</li>
            <li>Giao nhanh toàn quốc</li>
            <li>Đổi trả khi máy lỗi</li>
          </ul>
          <span className="topbar-hotline"><Icon name="phone" size={14} /> Hotline <b>1800 6868</b></span>
        </div>
      </div>
      <header className={`header no-print ${scrolled ? 'scrolled' : ''}`}>
        <div className="container header-inner">
          <Logo intro />
          <div
            className={`catnav-item ${categoriesOpen ? 'open' : ''} ${hoverDismissed ? 'hover-dismissed' : ''}`}
            ref={categoriesRef}
            onMouseEnter={() => {
              if (!hoverDismissed) setCategoriesOpen(true);
            }}
            onMouseLeave={() => {
              setCategoriesOpen(false);
              setHoverDismissed(false);
            }}
          >
            <button
              type="button"
              className={`catnav-trigger ${location.pathname === '/' && !location.search ? 'on' : ''}`}
              aria-expanded={categoriesOpen}
              onClick={() => {
                setCategoriesOpen(false);
                setHoverDismissed(true);
              }}
            >
              <span className="catnav-burger" aria-hidden="true"><i /><i /><i /></span>
              Tất cả điện thoại
            </button>
            {categoriesOpen && (
              <div className="category-mega" role="menu">
                <div className="category-mega-column category-mega-brand">
                  <h3>Thương hiệu đang bán</h3>
                  {menuBrands.map((b) => (
                    <Link
                      key={b.maThuongHieu}
                      className="brand-chip"
                      to={`/?hang=${b.maThuongHieu}`}
                      role="menuitem"
                      onClick={closeCategoriesByClick}
                      title={`${b.soSanPham} sản phẩm`}
                    >
                      {b.tenThuongHieu}
                    </Link>
                  ))}
                  {!allProducts && <p className="muted">Đang tải...</p>}
                  {allProducts && menuBrands.length === 0 && <p className="muted">Chưa có thương hiệu đang bán.</p>}
                </div>
                <div className="category-mega-column">
                  <h3>Được tìm nhiều trong 30 ngày</h3>
                  {menuPopular.map((item) => (
                    <Link key={item.key} className="category-chip" to={item.to} role="menuitem" onClick={closeCategoriesByClick}>
                      <span>{item.label}</span>
                      <small>{item.loai === 'search' ? `${item.luot} lượt tìm` : `${item.luot} lượt xem`}</small>
                    </Link>
                  ))}
                  {allProducts && menuPopular.length === 0 && <p className="muted">Chưa đủ dữ liệu tìm kiếm.</p>}
                </div>
                <div className="category-mega-column">
                  <h3>Giảm nhiều so với giá niêm yết</h3>
                  {menuDeals.map(({ product: p, deal }) => (
                    <Link key={p.maSanPham} className="category-chip deal-chip" to={`/products/${p.maSanPham}`} role="menuitem" onClick={closeCategoriesByClick}>
                      <span>{p.tenMay}<small>{formatPrice(deal.giaBan)}</small></span>
                      <b>−{Math.round(deal.phanTram * 100)}%</b>
                    </Link>
                  ))}
                  {allProducts && menuDeals.length === 0 && <p className="muted">Hiện chưa có sản phẩm giảm giá.</p>}
                </div>
              </div>
            )}
          </div>
          <SearchBox />
          <nav className="actions" aria-label="Tài khoản và giỏ hàng">
            {ids.length > 0 && (
              <Link to="/compare" className="pill">
                <Icon name="compare" size={16} /> So sánh <b key={ids.length}>{ids.length}</b>
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
                {unread > 0 && <span className="badge-dot" key={unread}>{unread}</span>}
              </Link>
            )}
            {!isAdmin && (
              <Link to="/cart" className="nav-item cart-link" aria-label="Giỏ hàng">
                <span className="cart-ico"><Icon name="cart" />{count > 0 && <span className="badge-dot" key={count}>{count}</span>}</span>
                <span>Giỏ hàng</span>
              </Link>
            )}
            <ThemeToggle className="icon-btn" />
            {isCustomer && <AccountMenu user={user} onLogout={handleLogout} />}
            {isAdmin && (
              <>
                <Link to="/admin" className="btn btn-primary">Trang quản trị</Link>
                <button className="btn btn-ghost" onClick={handleLogout}>Đăng xuất</button>
              </>
            )}
            {!user && (
              <>
                <Link to="/login" className="btn btn-ghost">Đăng nhập</Link>
                <Link to="/register" className="btn btn-primary">Đăng ký</Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="container main" ref={mainRef}>
        <Outlet />
      </main>
      <footer className="footer no-print">
        <div className="container footer-grid">
          <div>
            <Logo to={null} />
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
            <Link to="/returns">Đổi trả &amp; bảo hành</Link>
            <Link to="/account/addresses">Địa chỉ giao hàng</Link>
          </div>
          <div>
            <h4>Liên hệ</h4>
            <span>Hotline: 1800 6868 (8:00 - 21:00)</span>
            <span>Email: no-reply@phonenest.vn</span>
            <span>Hỏi đáp: dùng khung chat góc phải</span>
          </div>
        </div>
        <div className="container footer-bottom">
          <span>© 2026 PhoneNest. Đồ án Lập trình WWW (Java), Nhóm 10.</span>
          <small>Dữ liệu lượt xem, tìm kiếm và thêm giỏ được lưu tối đa 90 ngày để cá nhân hóa gợi ý.</small>
        </div>
      </footer>
      <ChatWidget />
    </>
  );
}
