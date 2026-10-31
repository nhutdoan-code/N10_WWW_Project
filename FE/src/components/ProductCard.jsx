
import { Link } from 'react-router-dom';
import useCompare from '../hooks/useCompare';
import useToast from '../hooks/useToast';
import { errorMessage, formatPrice } from '../utils/format';
import Icon from './Icon';
import ProductImage from './ProductImage';
import StatusBadge from './StatusBadge';
import { productStatus } from '../utils/stock';

export default function ProductCard({ sp }) {
  const { ids, toggle } = useCompare();
  const toast = useToast();

  // Lấy tồn kho trực tiếp từ bảng SanPham
  const soLuongTon = Number(sp.soLuongTon ?? 0);

  // Database sử dụng CON_HANG, HET_HANG, NGUNG_KINH_DOANH
  const dangKinhDoanh = sp.trangThai === 'CON_HANG';

  // Chỉ hết hàng khi tồn kho bằng 0 hoặc sản phẩm ngừng kinh doanh
  const hetHang = soLuongTon <= 0 || !dangKinhDoanh;

  const t = sp.thongSo || {};

  const tags = [
    t.ram && t.boNho && `${t.ram}GB | ${t.boNho}GB`,
    t.kichThuocManHinh && `${t.kichThuocManHinh}"`,
    t.ho5G && '5G',
  ].filter(Boolean);

  const run = async (action, message) => {
    try {
      await action();

      if (message) {
        toast(message);
      }
    } catch (e) {
      toast(
          e.response ? errorMessage(e) : e.message,
          'error'
      );
    }
  };

  return (
      <div className="card product-card">
        <Link to={`/products/${sp.maSanPham}`}>
        <span className="thumb-wrap">
          <ProductImage
              src={sp.anhChinh}
              alt={sp.tenMay}
              loading="lazy"
          />

          {hetHang && (
              <span className="sold-out">
              <StatusBadge status={productStatus(sp)} />
            </span>
          )}
        </span>

          <h3>{sp.tenMay}</h3>
        </Link>

        {tags.length > 0 && (
            <div className="spec-tags">
              {tags.map((tag) => (
                  <span key={tag}>{tag}</span>
              ))}
            </div>
        )}

        <span className="price">
        {formatPrice(sp.giaBan)}
      </span>

        <div className="row between meta">
        <span className="rating">
          {sp.diemDanhGiaTb}
        </span>

          {productStatus(sp) === 'SAP_HET' && (
              <span className="low-stock">
            Chỉ còn {soLuongTon} máy
          </span>
          )}
        </div>

        <div className="actions-row">
          <Link
              className={`btn btn-primary ${hetHang ? 'disabled' : ''}`}
              to={`/products/${sp.maSanPham}`}
              onClick={(e) => {
                if (hetHang) {
                  e.preventDefault();
                }
              }}
              aria-disabled={hetHang}
          >
            {hetHang ? 'Hết hàng' : 'Xem chi tiết'}
          </Link>

          <button
              className={
                ids.includes(sp.maSanPham)
                    ? 'btn icon-sq active'
                    : 'btn icon-sq'
              }
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
