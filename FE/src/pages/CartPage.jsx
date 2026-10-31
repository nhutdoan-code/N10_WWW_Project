import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import useAuth from '../hooks/useAuth';
import useCart from '../hooks/useCart';
import useToast from '../hooks/useToast';
import ProductImage from '../components/ProductImage';
import { wishlistService } from '../service/wishlistService';
import { errorMessage, formatPrice } from '../utils/format';

function CartQuantity({ item, soLuong, max, onChange }) {
  const [value, setValue] = useState(String(soLuong));

  useEffect(() => {
    setValue(String(soLuong));
  }, [soLuong]);

  const save = () => {
    const sl = Number(value);

    if (!Number.isInteger(sl) || sl < 1) {
      setValue(String(soLuong));
      return;
    }

    const next = max > 0 ? Math.min(sl, max) : soLuong;
    setValue(String(next));

    if (next !== soLuong) onChange(next);
  };

  return (
      <div className="qty-box">
        <button
            type="button"
            aria-label="Giảm"
            disabled={soLuong <= 1}
            onClick={() => onChange(soLuong - 1)}
        >
          −
        </button>

        <input
            type="number"
            min="1"
            max={max}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onBlur={save}
            onKeyDown={(e) => {
              if (e.key === 'Enter') e.currentTarget.blur();
            }}
            aria-label="Số lượng"
        />

        <button
            type="button"
            aria-label="Tăng"
            disabled={max <= 0 || soLuong >= max}
            onClick={() => onChange(soLuong + 1)}
        >
          +
        </button>
      </div>
  );
}

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

  const toWishlist = (item) => {
    const sp = item.sanPham;

    return run(async () => {
      await wishlistService.add(sp.maSanPham);
      await remove(item);
    }, 'Đã chuyển sang danh sách yêu thích');
  };

  if (items.length === 0) {
    return (
        <p className="muted">
          Giỏ hàng trống. <Link to="/">Tiếp tục mua sắm</Link>
        </p>
    );
  }

  return (
      <>
        <h2>
          Giỏ hàng <span className="muted">({count} sản phẩm)</span>
        </h2>

        <div className="cart-layout">
          <div className="section">
            {items.map((item) => {
              const { sanPham: sp, phienBan: pb, soLuong } = item;
              const stock = Number(pb.soLuongTon ?? 0);
              const price = Number(pb.giaBan ?? 0);
              const subtotal = price * soLuong;

              return (
                  <div key={pb.maPhienBan} className="cart-item">
                    <Link to={`/products/${sp.maSanPham}`}>
                      <ProductImage src={sp.anhChinh} alt={sp.tenMay} />
                    </Link>

                    <div>
                      <h4>
                        <Link to={`/products/${sp.maSanPham}`}>
                          {sp.tenMay}
                        </Link>
                      </h4>

                      <span className="price">{formatPrice(price)}</span>

                      <div className="muted variant-line">
                        {pb.ram}GB RAM · {pb.boNho}GB · {pb.mauSac || 'Mặc định'}
                      </div>

                      {soLuong > stock && (
                          <small className="error">
                            Số lượng trong giỏ vượt tồn kho hiện tại ({stock}).
                          </small>
                      )}

                      {soLuong === stock && stock > 0 && (
                          <small className="muted">
                            Đã đạt tối đa tồn kho ({stock}).
                          </small>
                      )}

                      <div className="tools">
                        <CartQuantity
                            item={item}
                            soLuong={soLuong}
                            max={stock}
                            onChange={(next) =>
                                run(() => setQuantity(item, next))
                            }
                        />

                        {isCustomer && (
                            <button
                                type="button"
                                className="link-btn"
                                onClick={() => toWishlist(item)}
                            >
                              Để sau
                            </button>
                        )}

                        <button
                            type="button"
                            className="link-btn"
                            onClick={() => run(() => remove(item))}
                        >
                          Xóa
                        </button>
                      </div>
                    </div>

                    <b>{formatPrice(subtotal)}</b>
                  </div>
              );
            })}
          </div>

          <aside className="side">
            <div className="summary">
              <div className="row between">
                <span>Tạm tính ({count} sản phẩm)</span>
                <span>{formatPrice(total)}</span>
              </div>

              <div className="row between total">
                <b>Tổng cộng</b>
                <b className="price">{formatPrice(total)}</b>
              </div>

              <p className="muted">
                Nhập mã khuyến mãi và chọn địa chỉ ở bước thanh toán.
              </p>

              <Link to="/checkout" className="btn btn-primary btn-lg">
                {isCustomer ? 'Thanh toán' : 'Đăng nhập để thanh toán'}
              </Link>
            </div>

            {!isCustomer && (
                <p className="muted">
                  Giỏ hàng được lưu tạm trên trình duyệt và sẽ được giữ lại
                  sau khi bạn đăng nhập.
                </p>
            )}

            <button
                type="button"
                className="btn btn-danger"
                onClick={() =>
                    window.confirm('Xóa toàn bộ giỏ hàng?') &&
                    run(clear, 'Đã xóa toàn bộ giỏ hàng')
                }
            >
              Xóa tất cả
            </button>
          </aside>
        </div>
      </>
  );
}