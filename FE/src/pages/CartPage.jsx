import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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
  const { items, count, setQuantity, remove, clear } = useCart();
  const { isCustomer } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  // Chọn sản phẩm để thanh toán: lưu các dòng KHÔNG được chọn, dòng mới thêm mặc định được chọn.
  const [boChon, setBoChon] = useState(() => new Set());

  const run = async (action, message) => {
    try {
      await action();
      if (message) toast(message);
    } catch (e) {
      toast(e.response ? errorMessage(e) : e.message, 'error');
    }
  };

  const hasDiscontinued = hasDiscontinuedItems(items);
  const hasUnapproved = hasUnapprovedItems(items);
  // Dòng còn mua được nhưng số lượng trong giỏ đã vượt tồn kho hiện tại (tồn giảm sau khi thêm vào giỏ).
  const overStockItems = items.filter((i) => !isItemDiscontinued(i) && !isItemUnapproved(i)
    && i.soLuong > Number(i.phienBan?.soLuongTon ?? 0));
  const hasOverStock = overStockItems.length > 0;
  // "Tạm tính (N sản phẩm)" chỉ đếm các dòng được cộng vào tổng tiền.
  const payableCount = items
    .filter((i) => !isItemDiscontinued(i) && !isItemUnapproved(i))
    .reduce((sum, i) => sum + i.soLuong, 0);

  const keyOf = (i) => i.phienBan?.maPhienBan || i.sanPham?.maSanPham;
  const isLockedItem = (i) => isItemDiscontinued(i) || isItemPendingReview(i) || isItemBlocked(i);
  const chonDuoc = items.filter((i) => !isLockedItem(i));
  const daChon = chonDuoc.filter((i) => !boChon.has(keyOf(i)));
  const chuaChon = items.filter((i) => !daChon.includes(i));
  const tongDaChon = daChon.reduce((s, i) => s + Number(i.phienBan?.giaBan ?? 0) * i.soLuong, 0);
  const soLuongDaChon = daChon.reduce((s, i) => s + i.soLuong, 0);
  const chonTatCa = chonDuoc.length > 0 && daChon.length === chonDuoc.length;
  const toanBoGio = daChon.length === items.length;
  const doiChon = (i) => setBoChon((prev) => {
    const next = new Set(prev);
    const k = keyOf(i);
    if (next.has(k)) next.delete(k); else next.add(k);
    return next;
  });
  const doiChonTatCa = () => setBoChon(chonTatCa ? new Set(chonDuoc.map(keyOf)) : new Set());
  const vuotTonDaChon = daChon.filter((i) => i.soLuong > Number(i.phienBan?.soLuongTon ?? 0));
  // Chuyển các sản phẩm chưa chọn sang Yêu thích (thay cho nút "Để sau" trước đây).
  const luuChuaChon = () => run(async () => {
    for (const item of chuaChon.filter((i) => !isLockedItem(i))) {
      await wishlistService.add(item.sanPham.maSanPham);
      await remove(item);
    }
    setBoChon(new Set());
  }, 'Đã chuyển sản phẩm chưa chọn sang Yêu thích');

  if (items.length === 0) {
    return (
      <div className="empty-state">
        <h2>Giỏ hàng trống</h2>
        <p className="muted">Chọn điện thoại bạn thích rồi bấm “Thêm vào giỏ”, sản phẩm sẽ hiện ở đây.</p>
        <Link to="/" className="btn btn-primary">Tiếp tục mua sắm</Link>
      </div>
    );
  }

  return (
    <>
      <h2>
        Giỏ hàng <span className="muted">({count} sản phẩm)</span>
      </h2>

      {hasUnapproved && (
        <div className="alert alert-warning unapproved-cart-banner" style={{ margin: '0 0 16px 0', padding: '12px 16px', background: 'var(--warn-soft)', color: 'var(--warn)', border: '1px solid #ffeeba', borderRadius: '8px', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Icon name="alert-triangle" size={20} />
          <span><strong>{DISCONTINUED_MESSAGES.APPROVAL_CART_BANNER}</strong></span>
        </div>
      )}

      {!hasUnapproved && hasDiscontinued && (
        <div className="alert alert-warning discontinued-cart-banner" style={{ margin: '0 0 16px 0', padding: '12px 16px', background: 'var(--warn-soft)', color: 'var(--warn)', border: '1px solid #ffeeba', borderRadius: '8px', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Icon name="alert-triangle" size={20} />
          <span><strong>{DISCONTINUED_MESSAGES.CART_BANNER}</strong></span>
        </div>
      )}

      <div className="cart-layout">
        <div className="section cart-list">
          <div className="cart-list-head">
            <label className="cart-check">
              <input type="checkbox" checked={chonTatCa} onChange={doiChonTatCa} disabled={chonDuoc.length === 0} />
              <span>Chọn tất cả ({chonDuoc.length})</span>
            </label>
            <button
              type="button"
              className="btn btn-sm btn-danger"
              onClick={() =>
                window.confirm('Xóa toàn bộ giỏ hàng?') &&
                run(clear, 'Đã xóa toàn bộ giỏ hàng')
              }
            >
              <Icon name="trash" size={15} /> Xóa tất cả
            </button>
          </div>
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
              <div key={pb?.maPhienBan || sp?.maSanPham} className={`cart-item ${!isLocked && !boChon.has(keyOf(item)) ? 'is-selected' : ''}`}>
                <label className="cart-check" title={isLocked ? 'Sản phẩm này không thể thanh toán' : 'Chọn để thanh toán'}>
                  <input
                    type="checkbox"
                    checked={!isLocked && !boChon.has(keyOf(item))}
                    disabled={isLocked}
                    onChange={() => doiChon(item)}
                    aria-label={`Chọn ${sp.tenMay} để thanh toán`}
                  />
                </label>
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
                    <small className="error" style={{ display: 'block', marginTop: '4px', fontWeight: 500, color: 'var(--warn)' }}>
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


                    <button
                      type="button"
                      className="btn btn-sm btn-danger cart-remove"
                      onClick={() => run(async () => {
                        await remove(item);
                        trackingService.trackRemoveFromCart(sp?.maSanPham, pb?.maPhienBan);
                      })}
                    >
                      <Icon name="trash" size={15} /> Xóa
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
              <span>Đã chọn {soLuongDaChon} / {payableCount} sản phẩm</span>
              <span>{formatPrice(tongDaChon)}</span>
            </div>

            <div className="row between total">
              <b>Tổng thanh toán</b>
              <b className="price">{formatPrice(tongDaChon)}</b>
            </div>

            <p className="muted">
              Nhập mã khuyến mãi và chọn địa chỉ ở bước thanh toán.
            </p>

            {daChon.length === 0 ? (
              <button type="button" className="btn btn-primary btn-lg" disabled>
                Chọn sản phẩm để thanh toán
              </button>
            ) : toanBoGio ? (
              hasOverStock ? (
                <>
                  <button type="button" className="btn btn-primary btn-lg" disabled>
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
              )
            ) : vuotTonDaChon.length > 0 ? (
              <>
                <button type="button" className="btn btn-primary btn-lg" disabled>Thanh toán {daChon.length} sản phẩm đã chọn</button>
                <small className="error" style={{ display: 'block', marginTop: '8px', textAlign: 'center' }}>
                  Có {vuotTonDaChon.length} sản phẩm đã chọn vượt tồn kho. Vui lòng giảm số lượng.
                </small>
              </>
            ) : (
              <>
                <button
                  type="button"
                  className="btn btn-primary btn-lg"
                  onClick={() => navigate('/checkout', { state: { chon: daChon.map((i) => i.phienBan.maPhienBan) } })}
                >
                  {isCustomer ? `Thanh toán ${daChon.length} sản phẩm đã chọn` : 'Đăng nhập để thanh toán'}
                </button>
                <small className="muted cart-select-note">Sản phẩm chưa chọn vẫn được giữ trong giỏ hàng.</small>
                {isCustomer && chuaChon.some((i) => !isLockedItem(i)) && (
                  <button type="button" className="btn btn-ghost btn-sm cart-later" onClick={luuChuaChon}>
                    <Icon name="heart" size={15} /> Chuyển sản phẩm chưa chọn sang Yêu thích
                  </button>
                )}
              </>
            )}
          </div>

          {!isCustomer && (
            <p className="muted">
              Giỏ hàng được lưu tạm trên trình duyệt và sẽ được giữ lại
              sau khi bạn đăng nhập.
            </p>
          )}
        </aside>
      </div>
    </>
  );
}
