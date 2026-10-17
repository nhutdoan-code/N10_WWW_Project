import { Link } from 'react-router-dom';
import useCart from '../hooks/useCart';
import useCompare from '../hooks/useCompare';
import useToast from '../hooks/useToast';
import { errorMessage, formatPrice } from '../utils/format';
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
        <img src={sp.anhChinh} alt={sp.tenMay} />
        <h3>{sp.tenMay}</h3>
      </Link>
      <div className="price">{formatPrice(sp.giaBan)}</div>
      <div className="row between">
        <StatusBadge status={sp.trangThai} />
        <span className="muted">★ {sp.diemDanhGiaTb}</span>
      </div>
      <div className="row">
        <button className="btn btn-primary grow" disabled={hetHang} onClick={() => run(() => add(sp), 'Đã thêm vào giỏ hàng')}>
          {hetHang ? 'Hết hàng' : 'Thêm vào giỏ'}
        </button>
        <button className={ids.includes(sp.maSanPham) ? 'btn active' : 'btn'} title="So sánh" onClick={() => run(() => toggle(sp.maSanPham))}>⇄</button>
      </div>
    </div>
  );
}
