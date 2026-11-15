import { useState } from 'react';
import { BarList } from '../../components/Charts';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { trackingService } from '../../service/trackingService';
import { errorMessage, formatNumber } from '../../utils/format';

const pad = (value) => String(value).padStart(2, '0');
const localDate = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const fromDaysAgo = (days) => { const date = new Date(); date.setDate(date.getDate() - days); return localDate(date); };
const EVENT_LABELS = {
  PAGE_VIEW: 'Xem trang', PRODUCT_VIEW: 'Xem sản phẩm', PRODUCT_CLICK: 'Nhấn sản phẩm',
  SEARCH: 'Tìm kiếm', ADD_TO_CART: 'Thêm vào giỏ', REMOVE_FROM_CART: 'Xóa khỏi giỏ',
  PURCHASE: 'Mua hàng', RECOMMENDATION_CLICK: 'Nhấn gợi ý',
};

export default function AdminAnalyticsPage() {
  const [range, setRange] = useState({ tuNgay: fromDaysAgo(30), denNgay: localDate(new Date()) });
  const [eventType, setEventType] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [preferenceQuery, setPreferenceQuery] = useState('');
  const [lookupCustomerId, setLookupCustomerId] = useState('');
  const [retentionDays, setRetentionDays] = useState(90);
  const [cleanupResult, setCleanupResult] = useState(null);
  const toast = useToast();
  const invalidRange = Boolean(range.tuNgay && range.denNgay && range.tuNgay > range.denNgay);
  const params = { tuNgay: range.tuNgay || undefined, denNgay: range.denNgay || undefined,
    loaiHanhVi: eventType || undefined, maKh: customerId || undefined };
  const { data, loading, error, reload } = useFetch(
    () => invalidRange ? Promise.resolve(null) : trackingService.getBehaviorStats(params),
    [range.tuNgay, range.denNgay, eventType, customerId, invalidRange]
  );
  const { data: preferences, loading: preferenceLoading, error: preferenceError, reload: reloadPreferences } = useFetch(
    () => lookupCustomerId ? trackingService.getUserPreferences(encodeURIComponent(lookupCustomerId)) : Promise.resolve(null),
    [lookupCustomerId]
  );

  const cleanup = async () => {
    const days = Number(retentionDays);
    if (!Number.isInteger(days) || days < 7 || days > 3650) return toast('Thời gian lưu phải từ 7 đến 3650 ngày.', 'error');
    if (!window.confirm(`Xóa vĩnh viễn các sự kiện hành vi cũ hơn ${days} ngày? Thao tác này không thể hoàn tác.`)) return;
    try {
      const result = await trackingService.cleanupBehavior(days);
      setCleanupResult(result);
      toast(`Đã xóa ${formatNumber(result.soBanGhiDaXoa || 0)} sự kiện cũ`);
      reload();
    } catch (e) { toast(errorMessage(e), 'error'); }
  };

  const eventRows = Object.entries(data?.theoLoai || {}).map(([name, value]) => ({ name: EVENT_LABELS[name] || name, value: Number(value) }));
  const renderProducts = (items, key) => (items || []).length ? <table className="table"><thead><tr><th>Sản phẩm</th><th>Lượt xem</th><th>Lượt nhấn</th><th>Thêm giỏ</th><th>Mua</th></tr></thead><tbody>{items.map((item) => <tr key={`${key}-${item.maSanPham}`}><td>{item.tenSanPham}<div className="muted">{item.maSanPham}</div></td><td>{formatNumber(item.viewCount)}</td><td>{formatNumber(item.clickCount)}</td><td>{formatNumber(item.addToCartCount)}</td><td>{formatNumber(item.purchaseCount)}</td></tr>)}</tbody></table> : <p className="muted">Chưa có dữ liệu trong khoảng đã chọn.</p>;

  return <div className="admin-analytics">
    <h2>Phân tích hành vi khách hàng</h2>
    <p className="muted">Dữ liệu giúp hiểu lượt xem, tìm kiếm và tương tác với sản phẩm. Sự kiện gần đây tuân theo bộ lọc bên dưới.</p>
    <div className="row" style={{ flexWrap: 'wrap', gap: 10, margin: '16px 0' }}>
      <label className="field"><span>Từ ngày</span><input type="date" value={range.tuNgay} max={range.denNgay || undefined} onChange={e => setRange({ ...range, tuNgay: e.target.value })} /></label>
      <label className="field"><span>Đến ngày</span><input type="date" value={range.denNgay} min={range.tuNgay || undefined} onChange={e => setRange({ ...range, denNgay: e.target.value })} /></label>
      <label className="field"><span>Loại hành vi</span><select value={eventType} onChange={e => setEventType(e.target.value)}><option value="">Tất cả</option>{Object.entries(EVENT_LABELS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <label className="field"><span>Mã khách hàng (tùy chọn)</span><input value={customerId} onChange={e => setCustomerId(e.target.value.trim())} placeholder="KH..." /></label>
    </div>
    {invalidRange && <p className="error">Ngày bắt đầu không được sau ngày kết thúc.</p>}
    {loading && <p className="muted">Đang tải thống kê...</p>}
    {error && <p className="error">{error} <button className="btn" onClick={reload}>Thử lại</button></p>}
    {data && !invalidRange && <>
      <div className="stats-grid"><div className="stat-card"><span>Tổng lượt tương tác</span><b>{formatNumber(data.tongLuot)}</b></div><div className="stat-card"><span>Sự kiện trong bảng gần đây</span><b>{formatNumber(data.suKienGanDay?.length || 0)}</b></div></div>
      <div className="grid-2" style={{ marginTop: 18 }}>
        <section className="card"><h3>Lượt theo loại hành vi</h3><BarList items={eventRows} color="#4775e8" /></section>
        <section className="card"><h3>Từ khóa tìm kiếm phổ biến</h3>{data.topTuKhoa?.length ? <table className="table"><thead><tr><th>Từ khóa</th><th>Lượt tìm</th></tr></thead><tbody>{data.topTuKhoa.map(item => <tr key={item.tuKhoa}><td>{item.tuKhoa}</td><td>{formatNumber(item.count)}</td></tr>)}</tbody></table> : <p className="muted">Chưa có từ khóa tìm kiếm.</p>}</section>
      </div>
      <section className="card" style={{ marginTop: 18 }}><h3>Sản phẩm được xem nhiều</h3>{renderProducts(data.topSanPhamXem, 'view')}</section>
      <section className="card" style={{ marginTop: 18 }}><h3>Sản phẩm được nhấn nhiều</h3>{renderProducts(data.topSanPhamClick, 'click')}</section>
      <section className="card" style={{ marginTop: 18 }}><h3>Sự kiện gần đây</h3>{data.suKienGanDay?.length ? <div style={{ overflowX: 'auto' }}><table className="table"><thead><tr><th>Thời gian</th><th>Loại</th><th>Khách / phiên</th><th>Sản phẩm / từ khóa</th><th>Nguồn</th></tr></thead><tbody>{data.suKienGanDay.map(event => <tr key={event.maHanhVi}><td>{event.thoiGian ? new Date(event.thoiGian).toLocaleString('vi-VN') : '—'}</td><td>{EVENT_LABELS[event.loaiHanhVi] || event.loaiHanhVi}</td><td>{event.maKh || `Khách vãng lai · ${(event.sessionId || '').slice(0, 8)}`}</td><td>{event.tenSanPham || event.tuKhoa || event.maSanPham || '—'}</td><td>{event.nguonSuKien || '—'}</td></tr>)}</tbody></table></div> : <p className="muted">Không có sự kiện phù hợp bộ lọc.</p>}</section>
    </>}
    <section className="card" style={{ marginTop: 18 }}><h3>Sở thích của khách hàng</h3><form className="row" onSubmit={e => { e.preventDefault(); setLookupCustomerId(preferenceQuery.trim()); }}><input value={preferenceQuery} onChange={e => setPreferenceQuery(e.target.value)} placeholder="Nhập mã khách hàng" /><button className="btn btn-primary">Xem sở thích</button></form>{preferenceLoading && <p className="muted">Đang tải sở thích...</p>}{preferenceError && <p className="error">{preferenceError} <button className="btn" onClick={reloadPreferences}>Thử lại</button></p>}{preferences && <div className="grid-2" style={{ marginTop: 12 }}><p><b>Tổng tương tác:</b> {formatNumber(preferences.tongLuotTuongTac)}</p><p><b>Khoảng giá:</b> {preferences.giaThapNhat == null ? '—' : `${Number(preferences.giaThapNhat).toLocaleString('vi-VN')} ₫`} – {preferences.giaCaoNhat == null ? '—' : `${Number(preferences.giaCaoNhat).toLocaleString('vi-VN')} ₫`}</p><p><b>Thương hiệu quan tâm:</b> {(preferences.topThuongHieu || []).join(', ') || '—'}</p><p><b>Danh mục quan tâm:</b> {(preferences.topDanhMuc || []).join(', ') || '—'}</p><p><b>Từ khóa gần đây:</b> {(preferences.tuKhoaGanDay || []).join(', ') || '—'}</p><p><b>Sản phẩm quan tâm:</b> {(preferences.sanPhamQuanTam || []).join(', ') || '—'}</p><p style={{ gridColumn: '1 / -1' }}><b>Tóm tắt:</b> {preferences.tomTatAi || '—'}</p></div>}</section>
    <section className="card" style={{ marginTop: 18 }}><h3>Lưu trữ dữ liệu hành vi</h3><p className="muted">Xóa sự kiện cũ hơn số ngày đã chọn. Mức cho phép: 7–3650 ngày.</p><div className="row analytics-cleanup-controls"><label className="field"><span>Giữ lại (ngày)</span><input type="number" min="7" max="3650" value={retentionDays} onChange={e => setRetentionDays(e.target.value)} /></label><button className="btn btn-danger" onClick={cleanup}>Dọn dữ liệu cũ</button></div>{cleanupResult && <p>Đã xóa {formatNumber(cleanupResult.soBanGhiDaXoa || 0)} sự kiện cũ hơn {cleanupResult.soNgayLuuTru} ngày.</p>}</section>
  </div>;
}
