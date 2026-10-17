import { Link } from 'react-router-dom';
import useCart from '../hooks/useCart';
import useCompare from '../hooks/useCompare';
import useToast from '../hooks/useToast';
import { errorMessage, formatPrice } from '../utils/format';
import Icon from './Icon';
import StatusBadge from './StatusBadge';

export default function ProductCard({ sp }) {
  const { add } = useCart();
  const { ids, toggle } = useCompare();
  const toast = useToast();
  const hetHang = sp.trangThai !== 'CON_HANG';

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
          <img src={sp.anhChinh} alt={sp.tenMay} loading="lazy" />
          {hetHang && <span className="sold-out"><StatusBadge status={sp.trangThai} /></span>}
        </span>
        <h3>{sp.tenMay}</h3>
      </Link>
      <div className="row between">
        <span className="price">{formatPrice(sp.giaBan)}</span>
        <span className="rating">{sp.diemDanhGiaTb}</span>
      </div>
      <div className="actions-row">
        <button className="btn btn-primary" disabled={hetHang} onClick={() => run(() => add(sp), 'Đã thêm vào giỏ hàng')}>
          {hetHang ? 'Hết hàng' : 'Thêm vào giỏ'}
        </button>
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
