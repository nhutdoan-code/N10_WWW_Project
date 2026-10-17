import useFetch from '../hooks/useFetch';
import { customerService } from '../service/customerService';
import { formatDate } from '../utils/format';

export default function NotificationsPage() {
  const { data: list, loading, reload } = useFetch(customerService.notifications);

  const read = async (n) => {
    if (!n.daDoc) await customerService.markRead(n.maThongBao);
    reload();
  };

  if (loading) return <p className="muted">Đang tải...</p>;

  return (
    <div className="checkout">
      <div className="row between">
        <h2>Thông báo</h2>
        <button className="btn" onClick={async () => { await customerService.markAllRead(); reload(); }}>Đánh dấu đã đọc tất cả</button>
      </div>
      {list.length === 0 && <p className="muted">Chưa có thông báo nào.</p>}
      {list.map((n) => (
        <div key={n.maThongBao} className={n.daDoc ? 'card notice' : 'card notice unread'} onClick={() => read(n)}>
          <p>{n.noiDung}</p>
          <small className="muted">{formatDate(n.ngayTao)}</small>
        </div>
      ))}
    </div>
  );
}
