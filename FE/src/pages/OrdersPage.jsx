import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import ProductImage from '../components/ProductImage';
import StatusBadge from '../components/StatusBadge';
import { ORDER_STATUS, PAYMENT_METHODS } from '../config/constants';
import useFetch from '../hooks/useFetch';
import { orderService } from '../service/orderService';
import { formatDate, formatPrice } from '../utils/format';

const SHOW_ITEMS = 2;

export default function OrdersPage() {
  const { data: orders, loading, error } = useFetch(orderService.list);
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState('');
  const [oldest, setOldest] = useState(false);
  const tt = params.get('tt') || '';

  const counts = useMemo(() => {
    const c = { '': orders?.length || 0 };
    (orders || []).forEach((o) => { c[o.trangThai] = (c[o.trangThai] || 0) + 1; });
    return c;
  }, [orders]);

  const shown = useMemo(() => {
    const key = q.trim().toLowerCase();
    const list = (orders || []).filter((o) => (!tt || o.trangThai === tt)
      && (!key || o.maDonHang.toLowerCase().includes(key) || o.chiTiet.some((d) => d.tenMay.toLowerCase().includes(key))));
    return oldest ? [...list].reverse() : list;
  }, [orders, tt, q, oldest]);

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;
  if (orders.length === 0) return <p className="muted">Bạn chưa có đơn hàng nào. <Link to="/">Mua sắm ngay</Link></p>;

  const tabs = [['', 'Tất cả'], ...Object.entries(ORDER_STATUS)];
  const chon = (v) => setParams(v ? { tt: v } : {});

  return (
    <>
      <h2>Lịch sử đơn hàng</h2>
      <div className="status-tabs">
        {tabs.map(([v, label]) => (
          <button key={v} className={tt === v ? 'tab active' : 'tab'} onClick={() => chon(v)}>
            {label} <span className="count">{counts[v] || 0}</span>
          </button>
        ))}
      </div>
      <div className="order-tools">
        <input placeholder="Tìm theo mã đơn hoặc tên sản phẩm..." value={q} onChange={(e) => setQ(e.target.value)} />
        <select value={oldest ? 'old' : 'new'} onChange={(e) => setOldest(e.target.value === 'old')}>
          <option value="new">Mới nhất trước</option>
          <option value="old">Cũ nhất trước</option>
        </select>
      </div>

      {shown.length === 0 && <p className="muted">Không có đơn hàng nào phù hợp.</p>}
      {shown.map((o) => (
        <div key={o.maDonHang} className="card order">
          <div className="row between">
            <Link to={`/orders/${o.maDonHang}`}><b>{o.maDonHang}</b></Link>
            <StatusBadge status={o.trangThai} />
          </div>
          <p className="muted">
            {formatDate(o.ngayMua)} · {PAYMENT_METHODS.find((m) => m.value === o.phuongThucTt)?.label}
            {o.maKm && ` · Mã ${o.maKm}`}
          </p>
          {o.chiTiet.slice(0, SHOW_ITEMS).map((d) => (
            <div key={d.maPhienBan || d.maSanPham} className="row between">
              <span className="row"><ProductImage className="thumb" src={d.anhChinh} alt="" />{d.tenMay} × {d.soLuong}</span>
              <span>{formatPrice(d.donGia * d.soLuong)}</span>
            </div>
          ))}
          {o.chiTiet.length > SHOW_ITEMS && <p className="muted">và {o.chiTiet.length - SHOW_ITEMS} sản phẩm khác</p>}
          <div className="row between">
            <b className="price">Tổng: {formatPrice(o.tongTien)}</b>
            <Link className="btn" to={`/orders/${o.maDonHang}`}>Xem chi tiết</Link>
          </div>
        </div>
      ))}
    </>
  );
}
