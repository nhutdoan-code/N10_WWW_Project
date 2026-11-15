import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../components/Icon';
import ProductImage from '../components/ProductImage';
import StatusBadge from '../components/StatusBadge';
import useAuth from '../hooks/useAuth';
import useCart from '../hooks/useCart';
import useToast from '../hooks/useToast';
import { wishlistService } from '../service/wishlistService';
import { trackingService } from '../service/trackingService';
import { errorMessage, formatDungLuong, formatPrice } from '../utils/format';
import { anhTheoPhienBan } from '../utils/image';
import {
  isItemDiscontinued,
  isItemPendingReview,
  isItemBlocked,
  isItemUnapproved,
  hasDiscontinuedItems,
  hasUnapprovedItems,
  DISCONTINUED_MESSAGES,
} from '../utils/discontinuedProductRules';

function CartQuantity({ soLuong, max, disabled, onChange }) {
  const [value, setValue] = useState(String(soLuong));

  useEffect(() => {
    setValue(String(soLuong));
  }, [soLuong]);

  const save = () => {
    if (disabled) return;
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
        disabled={disabled || soLuong <= 1}
        onClick={() => onChange(soLuong - 1)}
      >
        −
      </button>

      <input
        type="number"
        min="1"
        max={max}
        value={value}
        disabled={disabled}
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
        disabled={disabled || max <= 0 || soLuong >= max}
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

  const hasDiscontinued = hasDiscontinuedItems(items);
  const hasUnapproved = hasUnapprovedItems(items);
  const hasBlockedOrDiscontinued = hasDiscontinued || hasUnapproved;
  // Dòng còn mua được nhưng số lượng trong giỏ đã vượt tồn kho hiện tại (tồn giảm sau khi thêm vào giỏ).
  const overStockItems = items.filter((i) => !isItemDiscontinued(i) && !isItemUnapproved(i)
    && i.soLuong > Number(i.phienBan?.soLuongTon ?? 0));
  const hasOverStock = overStockItems.length > 0;
  // "Tạm tính (N sản phẩm)" chỉ đếm các dòng được cộng vào tổng tiền.
  const payableCount = items
    .filter((i) => !isItemDiscontinued(i) && !isItemUnapproved(i))
    .reduce((sum, i) => sum + i.soLuong, 0);

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

      {hasUnapproved && (
        <div className="alert alert-warning unapproved-cart-banner" style={{ margin: '0 0 16px 0', padding: '12px 16px', background: '#fff3cd', color: '#856404', border: '1px solid #ffeeba', borderRadius: '8px', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Icon name="alert-triangle" size={20} />
          <span><strong>{DISCONTINUED_MESSAGES.APPROVAL_CART_BANNER}</strong></span>
        </div>
      )}

      {!hasUnapproved && hasDiscontinued && (
        <div className="alert alert-warning discontinued-cart-banner" style={{ margin: '0 0 16px 0', padding: '12px 16px', background: '#fff3cd', color: '#856404', border: '1px solid #ffeeba', borderRadius: '8px', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Icon name="alert-triangle" size={20} />
          <span><strong>{DISCONTINUED_MESSAGES.CART_BANNER}</strong></span>
        </div>
      )}

      <div className="cart-layout">
        <div className="section">
          {items.map((item) => {
            const { sanPham: sp, phienBan: pb, soLuong } = item;
            const isDiscontinued = isItemDiscontinued(item);
            const isPending = isItemPendingReview(item);
            const isBlocked = isItemBlocked(item);
            const isLocked = isDiscontinued || isPending || isBlocked;
            const stock = Number(pb?.soLuongTon ?? 0);
            const price = Number(pb?.giaBan ?? 0);
            const subtotal = price * soLuong;

            return (
              <div key={pb?.maPhienBan || sp?.maSanPham} className="cart-item">
                <Link to={`/products/${sp.maSanPham}`}>
                  <ProductImage src={anhTheoPhienBan(sp, pb)} alt={sp.tenMay} />
                </Link>

                <div>
                  <h4>
                    <Link to={`/products/${sp.maSanPham}`}>
                      {sp.tenMay}
                    </Link>
                    {isDiscontinued && (
                      <span style={{ marginLeft: '8px' }}>
                        <StatusBadge status="NGUNG_KINH_DOANH" />
                      </span>
                    )}
                    {isPending && (
                      <span style={{ marginLeft: '8px' }}>
                        <StatusBadge status="PENDING_REVIEW" />
                      </span>
                    )}
                    {isBlocked && (
                      <span style={{ marginLeft: '8px' }}>
                        <StatusBadge status="BLOCKED" />
                      </span>
                    )}
                  </h4>

                  {/* 2B.7C: không hiển thị giá seed của biến thể chưa duyệt như giá bán */}
                  {isPending ? (
                    <span className="price price-pending">Giá đang cập nhật</span>
                  ) : isBlocked ? (
                    <span className="price price-pending">Không khả dụng</span>
                  ) : (
                    <span className="price">{formatPrice(price)}</span>
                  )}

                  <div className="muted variant-line">
                    {pb ? `${pb.ram}GB RAM · ${formatDungLuong(pb.boNho)} · ${pb.mauSac || 'Mặc định'}` : 'Bản tiêu chuẩn'}
                  </div>

                  {isDiscontinued && (
                    <small className="error" style={{ display: 'block', marginTop: '4px', fontWeight: 500 }}>
                      {DISCONTINUED_MESSAGES.CART_ITEM_WARNING}
                    </small>
                  )}

                  {isPending && (
                    <small className="error" style={{ display: 'block', marginTop: '4px', fontWeight: 500, color: '#b45309' }}>
                      Sản phẩm này đang tạm ngừng nhận đơn và không thể thanh toán. Vui lòng xóa khỏi giỏ hàng.
                    </small>
                  )}

                  {isBlocked && (
                    <small className="error" style={{ display: 'block', marginTop: '4px', fontWeight: 500 }}>
                      Sản phẩm này đã bị khóa bán và không thể thanh toán. Vui lòng xóa khỏi giỏ hàng.
                    </small>
                  )}

                  {!isLocked && soLuong > stock && (
                    <small className="error">
                      Số lượng trong giỏ vượt tồn kho hiện tại ({stock}).
                    </small>
                  )}

                  {!isLocked && soLuong === stock && stock > 0 && (
                    <small className="muted">
                      Đã đạt tối đa tồn kho ({stock}).
                    </small>
                  )}

                  <div className="tools">
                    <CartQuantity
                      soLuong={soLuong}
                      max={stock}
                      disabled={isLocked}
                      onChange={(next) => run(() => setQuantity(item, next))}
                    />

                    {isCustomer && !isLocked && (
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
                      onClick={() => run(async () => {
                        await remove(item);
                        trackingService.trackRemoveFromCart(sp?.maSanPham, pb?.maPhienBan);
                      })}
                    >
                      Xóa
                    </button>
                  </div>
                </div>

                <b>{isPending || isBlocked ? '—' : formatPrice(subtotal)}</b>
              </div>
            );
          })}
        </div>

        <aside className="side">
          <div className="summary">
            <div className="row between">
              <span>Tạm tính ({payableCount} sản phẩm)</span>
              <span>{formatPrice(total)}</span>
            </div>

            <div className="row between total">
              <b>Tổng cộng</b>
              <b className="price">{formatPrice(total)}</b>
            </div>

            <p className="muted">
              Nhập mã khuyến mãi và chọn địa chỉ ở bước thanh toán.
            </p>

            {hasBlockedOrDiscontinued ? (
              <>
                <button
                  type="button"
                  className="btn btn-primary btn-lg"
                  disabled
                  title={hasUnapproved ? DISCONTINUED_MESSAGES.APPROVAL_CART_BANNER : DISCONTINUED_MESSAGES.CART_BANNER}
                  style={{ opacity: 0.6, cursor: 'not-allowed' }}
                >
                  Thanh toán
                </button>
                <small className="error" style={{ display: 'block', marginTop: '8px', textAlign: 'center' }}>
                  {DISCONTINUED_MESSAGES.CHECKOUT_ACTION_DISABLED}
                </small>
              </>
            ) : hasOverStock ? (
              <>
                <button type="button" className="btn btn-primary btn-lg" disabled style={{ opacity: 0.6, cursor: 'not-allowed' }}>
                  Thanh toán
                </button>
                <small className="error" style={{ display: 'block', marginTop: '8px', textAlign: 'center' }}>
                  Có {overStockItems.length} sản phẩm vượt số lượng tồn kho. Vui lòng giảm số lượng trước khi thanh toán.
                </small>
              </>
            ) : (
              <Link to="/checkout" className="btn btn-primary btn-lg">
                {isCustomer ? 'Thanh toán' : 'Đăng nhập để thanh toán'}
              </Link>
            )}
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
