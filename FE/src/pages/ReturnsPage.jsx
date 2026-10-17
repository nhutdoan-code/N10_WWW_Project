import { Link } from 'react-router-dom';
import StatusBadge from '../components/StatusBadge';
import useFetch from '../hooks/useFetch';
import { customerService } from '../service/customerService';
import { formatDay } from '../utils/format';

export default function ReturnsPage() {
  const { data: list, loading, error } = useFetch(customerService.returns);

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;

  return (
    <div className="checkout">
      <h2>Yêu cầu đổi trả / hoàn tiền</h2>
      {list.length === 0 && <p className="muted">Bạn chưa có yêu cầu nào. Có thể tạo yêu cầu từ trang chi tiết đơn hàng đã giao.</p>}
      {list.map((r) => (
        <div key={r.maYeuCau} className="card order">
          <div className="row between">
            <b>Đơn <Link to={`/orders/${r.maDonHang}`}>{r.maDonHang}</Link></b>
            <StatusBadge status={r.trangThai} />
          </div>
          <p>{r.lyDo}</p>
          {r.hinhAnhMinhChung && <a href={r.hinhAnhMinhChung} target="_blank" rel="noreferrer">Xem ảnh minh chứng</a>}
          <small className="muted">Ngày gửi: {formatDay(r.ngayTao)}</small>
        </div>
      ))}
    </div>
  );
}
