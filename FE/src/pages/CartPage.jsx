import { Link } from 'react-router-dom';
import useAuth from '../hooks/useAuth';
import useCart from '../hooks/useCart';
import useToast from '../hooks/useToast';
import { wishlistService } from '../service/wishlistService';
import { errorMessage, formatPrice } from '../utils/format';

export default function CartPage() {
  const { items, total, count, setQuantity, remove, clear } = useCart();
  const { isCustomer } = useAuth();
  const toast = useToast();

  const run = async (action, message) => {
    try {
      await action();
      if (message) toast(message);
    } catch (e) {
      toast(e.response ? errorMessage(e) : e.message, 'error');
    }
  };

  const toWishlist = (sp) =>
    run(async () => {
      await wishlistService.add(sp.maSanPham);
      await remove(sp);
    }, 'Đã chuyển sang danh sách yêu thích');

  if (items.length === 0) {
    return <p className="muted">Giỏ hàng trống. <Link to="/">Tiếp tục mua sắm</Link></p>;
  }

  return (
    <>
      <h2>Giỏ hàng <span className="muted">({count} sản phẩm)</span></h2>
      <div className="cart-layout">
        <div className="section">
          {items.map(({ sanPham: sp, soLuong }) => (
            <div key={sp.maSanPham} className="cart-item">
              <Link to={`/products/${sp.maSanPham}`}><img src={sp.anhChinh} alt="" /></Link>
              <div>
                <h4><Link to={`/products/${sp.maSanPham}`}>{sp.tenMay}</Link></h4>
                <span className="price">{formatPrice(sp.giaBan)}</span>
                {soLuong >= sp.soLuongTon && <small className="error"> · Đã đạt tối đa tồn kho ({sp.soLuongTon})</small>}
                <div className="tools">
                  <div className="qty-box">
                    <button aria-label="Giảm" onClick={() => run(() => setQuantity(sp, soLuong - 1))}>−</button>
                    <input
                      type="number"
                      min="1"
                      max={sp.soLuongTon}
                      value={soLuong}
                      onChange={(e) => run(() => setQuantity(sp, Number(e.target.value)))}
                    />
                    <button aria-label="Tăng" disabled={soLuong >= sp.soLuongTon} onClick={() => run(() => setQuantity(sp, soLuong + 1))}>+</button>
                  </div>
                  {isCustomer && <button className="link-btn" onClick={() => toWishlist(sp)}>Để sau</button>}
                  <button className="link-btn" onClick={() => run(() => remove(sp))}>Xóa</button>
                </div>
              </div>
              <b>{formatPrice(sp.giaBan * soLuong)}</b>
            </div>
          ))}
        </div>
        <aside className="side">
          <div className="summary">
            <div className="row between"><span>Tạm tính ({count} sản phẩm)</span><span>{formatPrice(total)}</span></div>
            <div className="row between total"><b>Tổng cộng</b><b className="price">{formatPrice(total)}</b></div>
            <p className="muted">Nhập mã khuyến mãi và chọn địa chỉ ở bước thanh toán.</p>
            <Link to="/checkout" className="btn btn-primary btn-lg">{isCustomer ? 'Thanh toán' : 'Đăng nhập để thanh toán'}</Link>
          </div>
          {!isCustomer && <p className="muted">Giỏ hàng được lưu tạm trên trình duyệt và sẽ được giữ lại sau khi bạn đăng nhập.</p>}
          <button className="btn btn-danger" onClick={() => window.confirm('Xóa toàn bộ giỏ hàng?') && run(clear, 'Đã xóa toàn bộ giỏ hàng')}>
            Xóa tất cả
          </button>
        </aside>
      </div>
    </>
  );
}
