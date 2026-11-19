import { Link } from 'react-router-dom';
import useCart from '../hooks/useCart';
import useCompare from '../hooks/useCompare';
import useToast from '../hooks/useToast';
import { errorMessage, formatDungLuong, formatPrice } from '../utils/format';
import { trackingService } from '../service/trackingService';
import Icon from './Icon';
import ProductImage from './ProductImage';
import StatusBadge from './StatusBadge';
import { productStatus } from '../utils/stock';
import { isProductDiscontinued } from '../utils/discontinuedProductRules';
import { cardPricing } from '../utils/approvalUi';

export default function ProductCard({ sp }) {
  const { ids, toggle } = useCompare();
  const toast = useToast();
  const isNgungKD = isProductDiscontinued(sp);

  // 2B.7C: chỉ biến thể đã duyệt hợp lệ (duocPhepBan) mới được dùng để hiển thị giá/tồn.
  const { allPending, allBlocked, hetHang, tonBan, nhieuGia, gia } = cardPricing(sp);
  const giaHienThi = gia ?? sp.giaBan ?? null;
  const t = sp.thongSo || {};
  const tags = [t.ram && t.boNho && `${t.ram}GB | ${formatDungLuong(t.boNho)}`, t.kichThuocManHinh && `${t.kichThuocManHinh}"`, t.ho5G && '5G'].filter(Boolean);

  const run = async (action, message) => {
    try {
      await action();
      if (message) toast(message);
    } catch (e) {
      toast(e.response ? errorMessage(e) : e.message, 'error');
    }
  };

  return (
    <div className="card product-card">
      <Link to={`/products/${sp.maSanPham}`} onClick={() => trackingService.trackClick(sp.maSanPham)}>
        <span className="thumb-wrap">
          <ProductImage src={sp.anhChinh} alt={sp.tenMay} loading="lazy" />
          {allPending && <span className="sold-out"><StatusBadge status="PENDING_REVIEW" /></span>}
          {allBlocked && <span className="sold-out"><StatusBadge status="BLOCKED" /></span>}
          {hetHang && <span className="sold-out"><StatusBadge status={productStatus({ ...sp, soLuongTon: isNgungKD ? sp.soLuongTon : tonBan })} /></span>}
        </span>
        <h3>{sp.tenMay}</h3>
      </Link>
      {tags.length > 0 && <div className="spec-tags">{tags.map((t) => <span key={t}>{t}</span>)}</div>}
      
      {allPending ? (
        <span className="price price-pending">Giá đang cập nhật</span>
      ) : allBlocked ? (
        <span className="price price-unavailable">Không khả dụng</span>
      ) : (
        giaHienThi == null ? (
          <span className="price price-pending">{isNgungKD ? 'Ngừng kinh doanh' : 'Giá đang cập nhật'}</span>
        ) : (
          <span className="price">{nhieuGia ? 'Từ ' : ''}{formatPrice(giaHienThi)}</span>
        )
      )}

      <div className="row between meta">
        <span className="rating">{sp.diemDanhGiaTb}</span>
        {!allPending && !allBlocked && !hetHang && tonBan > 0 && productStatus({ ...sp, soLuongTon: tonBan }) === 'SAP_HET' && (
          <span className="low-stock">Chỉ còn {tonBan} máy</span>
        )}
      </div>
      <div className="actions-row">
        <Link
          className={`btn ${isNgungKD || allPending || allBlocked ? 'btn-secondary' : 'btn-primary'} ${hetHang && !isNgungKD ? 'disabled' : ''}`}
          to={`/products/${sp.maSanPham}`}
          onClick={(e) => {
            trackingService.trackClick(sp.maSanPham);
            if (hetHang && !isNgungKD) e.preventDefault();
          }}
        >
          {isNgungKD || allPending || allBlocked ? 'Xem chi tiết' : (hetHang ? 'Hết hàng' : 'Chọn phiên bản')}
        </Link>
        <button
          className={ids.includes(sp.maSanPham) ? 'btn icon-sq active' : 'btn icon-sq'}
          title="So sánh"
          aria-label="So sánh"
          onClick={() => run(() => toggle(sp.maSanPham))}
        >
          <Icon name="compare" size={18} />
        </button>
      </div>
    </div>
  );
}
