import { Link } from 'react-router-dom';
import useCart from '../hooks/useCart';
import useCompare from '../hooks/useCompare';
import useToast from '../hooks/useToast';
import { errorMessage, formatPrice } from '../utils/format';
import Icon from './Icon';
import ProductImage from './ProductImage';
import StatusBadge from './StatusBadge';
import { productStatus } from '../utils/stock';

export default function ProductCard({ sp }) {
  const { add } = useCart();
  const { ids, toggle } = useCompare();
  const toast = useToast();
  // soLuongTon/giaBan từ API đã chỉ tính variant DANG_KINH_DOANH (xem SanPhamMapper).
  const hetHang = Number(sp.soLuongTon || 0) <= 0 || sp.trangThai !== 'DANG_KINH_DOANH';
  const t = sp.thongSo || {};
  const tags = [t.ram && t.boNho && `${t.ram}GB | ${t.boNho}GB`, t.kichThuocManHinh && `${t.kichThuocManHinh}"`, t.ho5G && '5G'].filter(Boolean);
  const giaHopLe = (sp.phienBans || []).filter((p) => p.trangThai === 'DANG_KINH_DOANH' && p.giaBan != null).map((p) => Number(p.giaBan));
  const nhieuGia = new Set(giaHopLe).size > 1;

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
      <Link to={`/products/${sp.maSanPham}`}>
        <span className="thumb-wrap">
          <ProductImage src={sp.anhChinh} alt={sp.tenMay} loading="lazy" />
          {hetHang && <span className="sold-out"><StatusBadge status={productStatus(sp)} /></span>}
        </span>
        <h3>{sp.tenMay}</h3>
      </Link>
      {tags.length > 0 && <div className="spec-tags">{tags.map((t) => <span key={t}>{t}</span>)}</div>}
      <span className="price">{nhieuGia ? 'Từ ' : ''}{formatPrice(sp.giaBan)}</span>
      <div className="row between meta">
        <span className="rating">{sp.diemDanhGiaTb}</span>
        {productStatus(sp) === 'SAP_HET' && <span className="low-stock">Chỉ còn {sp.soLuongTon} máy</span>}
      </div>
      <div className="actions-row">
        <Link className={`btn btn-primary ${hetHang ? 'disabled' : ''}`} to={`/products/${sp.maSanPham}`} onClick={(e) => { if (hetHang) e.preventDefault(); }}>
          {hetHang ? 'Hết hàng' : 'Chọn phiên bản'}
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
