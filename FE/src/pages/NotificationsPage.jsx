import { useNavigate } from 'react-router-dom';
import useFetch from '../hooks/useFetch';
import { customerService } from '../service/customerService';
import { formatDate } from '../utils/format';
import { notificationLink } from '../utils/notificationLink';

export default function NotificationsPage() {
  const { data: list, loading, reload } = useFetch(customerService.notifications);
  const navigate = useNavigate();

  const open = async (n) => {
    const link = notificationLink(n);
    if (!n.daDoc) {
      try {
        await customerService.markRead(n.maThongBao);
      } catch {
        // Không chặn việc mở trang nếu đánh dấu đã đọc thất bại.
      }
    }
    if (link) navigate(link);
    else reload();
  };

  if (loading) return <p className="muted">Đang tải...</p>;

  return (
    <div className="checkout">
      <div className="row between">
        <h2>Thông báo</h2>
        <button className="btn" onClick={async () => { await customerService.markAllRead(); reload(); }}>Đánh dấu đã đọc tất cả</button>
      </div>
      {list.length === 0 && <p className="muted">Chưa có thông báo nào.</p>}
      {list.map((n) => {
        const link = notificationLink(n);
        return (
          <div
            key={n.maThongBao}
            className={n.daDoc ? 'card notice' : 'card notice unread'}
            role={link ? 'link' : 'button'}
            tabIndex={0}
            style={{ cursor: 'pointer' }}
            onClick={() => open(n)}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(n); } }}
          >
            <p>{n.noiDung}</p>
            <div className="row between">
              <small className="muted">{formatDate(n.ngayTao)}</small>
              {link && <small className="link-btn">Xem chi tiết →</small>}
            </div>
          </div>
        );
      })}
    </div>
  );
}
