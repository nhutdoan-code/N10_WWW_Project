import { Link } from 'react-router-dom';
import StatusBadge from '../components/StatusBadge';
import { PAYMENT_METHODS } from '../config/constants';
import useFetch from '../hooks/useFetch';
import { orderService } from '../service/orderService';
import { formatDate, formatPrice } from '../utils/format';

export default function OrdersPage() {
  const { data: orders, loading, error } = useFetch(orderService.list);

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;
  if (orders.length === 0) return <p className="muted">Bạn chưa có đơn hàng nào. <Link to="/">Mua sắm ngay</Link></p>;

  return (
    <>
      <h2>Lịch sử đơn hàng</h2>
      {orders.map((o) => (
        <div key={o.maDonHang} className="card order">
          <div className="row between">
            <Link to={`/orders/${o.maDonHang}`}><b>{o.maDonHang}</b></Link>
            <StatusBadge status={o.trangThai} />
          </div>
          <p className="muted">
            {formatDate(o.ngayMua)} · {PAYMENT_METHODS.find((m) => m.value === o.phuongThucTt)?.label}
            {o.maKm && ` · Mã ${o.maKm}`}
          </p>
          {o.chiTiet.map((d) => (
            <div key={d.maSanPham} className="row between">
              <span className="row"><img className="thumb" src={d.anhChinh} alt="" />{d.tenMay} × {d.soLuong}</span>
              <span>{formatPrice(d.donGia * d.soLuong)}</span>
            </div>
          ))}
          <div className="row between">
            <b className="price">Tổng: {formatPrice(o.tongTien)}</b>
            <Link className="btn" to={`/orders/${o.maDonHang}`}>Xem chi tiết</Link>
          </div>
        </div>
      ))}
    </>
  );
}
