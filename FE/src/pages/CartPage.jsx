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
      <div className="row between">
        <h2>Giỏ hàng ({count} sản phẩm)</h2>
        <button className="btn btn-danger" onClick={() => window.confirm('Xóa toàn bộ giỏ hàng?') && run(clear, 'Đã xóa toàn bộ giỏ hàng')}>
          Xóa tất cả
        </button>
      </div>
      <table className="table">
        <thead>
          <tr><th>Sản phẩm</th><th>Đơn giá</th><th>Số lượng</th><th>Thành tiền</th><th></th></tr>
        </thead>
        <tbody>
          {items.map(({ sanPham: sp, soLuong }) => (
            <tr key={sp.maSanPham}>
              <td>
                <Link to={`/products/${sp.maSanPham}`} className="row"><img className="thumb" src={sp.anhChinh} alt="" />{sp.tenMay}</Link>
                {soLuong >= sp.soLuongTon && <small className="error">Đã đạt tối đa tồn kho ({sp.soLuongTon})</small>}
              </td>
              <td>{formatPrice(sp.giaBan)}</td>
              <td>
                <button className="btn" onClick={() => run(() => setQuantity(sp, soLuong - 1))}>−</button>
                <input
                  className="qty"
                  type="number"
                  min="1"
                  max={sp.soLuongTon}
                  value={soLuong}
                  onChange={(e) => run(() => setQuantity(sp, Number(e.target.value)))}
                />
                <button className="btn" disabled={soLuong >= sp.soLuongTon} onClick={() => run(() => setQuantity(sp, soLuong + 1))}>+</button>
              </td>
              <td>{formatPrice(sp.giaBan * soLuong)}</td>
              <td className="row">
                {isCustomer && <button className="btn" onClick={() => toWishlist(sp)}>♡ Để sau</button>}
                <button className="btn btn-danger" onClick={() => run(() => remove(sp))}>Xóa</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!isCustomer && <p className="muted">Giỏ hàng được lưu tạm trên trình duyệt. Đăng nhập để đặt hàng, giỏ hàng sẽ được giữ lại.</p>}
      <div className="row end">
        <h3>Tổng: {formatPrice(total)}</h3>
        <Link to="/checkout" className="btn btn-primary">{isCustomer ? 'Thanh toán' : 'Đăng nhập để thanh toán'}</Link>
      </div>
    </>
  );
}
