import { useMemo, useState } from 'react';
import Modal from '../../components/Modal';
import StatusBadge from '../../components/StatusBadge';
import { ORDER_STATUS } from '../../config/constants';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatDate, formatPrice } from '../../utils/format';

// Admin không được chọn tay các trạng thái này (đi qua luồng riêng / do khách xác nhận)
const KHONG_CHON_TAY = ['CHO_KHACH_XAC_NHAN', 'DA_GIAO', 'DANG_XU_LY'];
const CHON_DUOC = Object.entries(ORDER_STATUS).filter(([v]) => !KHONG_CHON_TAY.includes(v));

export default function AdminOrdersPage() {
  const { data: orders, reload } = useFetch(adminService.orders);
  const [selected, setSelected] = useState(null);
  const [xacNhanGiao, setXacNhanGiao] = useState(null);
  const [dangGui, setDangGui] = useState(false);
  const [tt, setTt] = useState('');
  const [q, setQ] = useState('');
  const toast = useToast();

  const shown = useMemo(() => {
    const key = q.trim().toLowerCase();
    return (orders || []).filter((o) => (!tt || o.trangThai === tt)
        && (!key || o.maDonHang.toLowerCase().includes(key) || (o.tenKhach || '').toLowerCase().includes(key)));
  }, [orders, tt, q]);

  const run = async (action, message) => {
    try {
      const updated = await action();
      toast(message);
      if (updated?.maDonHang) setSelected(updated);
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
    reload();
  };

  const danhDauDaGiao = async () => {
    setDangGui(true);
    try {
      const updated = await adminService.markDelivered(xacNhanGiao.maDonHang);
      toast('Đã chuyển đơn sang trạng thái chờ khách hàng xác nhận');
      if (selected?.maDonHang === updated.maDonHang) setSelected(updated);
      setXacNhanGiao(null);
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
    setDangGui(false);
    reload();
  };

  const thoiGian = (o) => {
    if (o.trangThai === 'CHO_KHACH_XAC_NHAN') return `Đã giao lúc ${formatDate(o.ngayGiao)} · chờ khách xác nhận`;
    if (o.trangThai === 'DA_GIAO' && o.ngayKhachXacNhan) return `${o.nguoiXacNhan} xác nhận lúc ${formatDate(o.ngayKhachXacNhan)}`;
    return null;
  };

  const trangThaiCell = (o) => {
    if (!CHON_DUOC.some(([v]) => v === o.trangThai)) {
      return (
          <>
            <StatusBadge status={o.trangThai} />
            {thoiGian(o) && <div className="muted"><small>{thoiGian(o)}</small></div>}
          </>
      );
    }
    return (
        <div className="admin-order-status-actions">
          <select
              className="admin-order-status-select"
              value={o.trangThai}
              onChange={(e) =>
                  run(
                      () => adminService.updateOrderStatus(
                          o.maDonHang,
                          e.target.value
                      ),
                      'Đã cập nhật trạng thái'
                  )
              }
          >
            {CHON_DUOC.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
            ))}
          </select>
          {o.trangThai === 'DANG_GIAO' && (
              <button className="btn btn-primary" onClick={() => setXacNhanGiao(o)}>Xác nhận đã giao hàng</button>
          )}
        </div>
    );
  };

  return (
      <>
        <h2>Quản lý đơn hàng (mới nhất trước)</h2>
        <div className="order-tools">
          <input placeholder="Tìm theo mã đơn hoặc tên khách..." value={q} onChange={(e) => setQ(e.target.value)}/>
          <select value={tt} onChange={(e) => setTt(e.target.value)}>
            <option value="">Tất cả đơn hàng</option>
            {Object.entries(ORDER_STATUS).filter(([v]) => v !== 'DANG_XU_LY').map(([v, label]) => <option key={v} value={v}>{label}</option>)}
          </select>
        </div>
        <table className="table">
          <thead>
          <tr><th>Mã đơn</th><th>Khách hàng</th><th>Ngày mua</th><th>Sản phẩm</th><th>Tổng tiền</th><th>Trạng thái</th><th></th></tr>
          </thead>
          <tbody>
          {shown.map((o) => (
              <tr key={o.maDonHang}>
                <td>{o.maDonHang}</td>
                <td>{o.tenKhach || o.maKh}</td>
                <td>{formatDate(o.ngayMua)}</td>
                <td>{o.chiTiet.map((d) => `${d.tenMay} × ${d.soLuong}`).join(', ')}</td>
                <td>{formatPrice(o.tongTien)}</td>
                <td>{trangThaiCell(o)}</td>
                <td><button className="btn" onClick={() => setSelected(o)}>Chi tiết</button></td>
              </tr>
          ))}
          {shown.length === 0 && <tr><td colSpan="7" className="muted">Không có đơn hàng phù hợp.</td></tr>}
          </tbody>
        </table>

        {selected && (
            <Modal title={`Đơn ${selected.maDonHang}`} onClose={() => setSelected(null)}>
              <p className="muted">Khách: {selected.tenKhach || selected.maKh} · {formatDate(selected.ngayMua)}</p>
              <p>Giao đến: {selected.diaChiGiaoHang || '—'}</p>
              {selected.ngayGiao && <p>Admin đánh dấu đã giao: {formatDate(selected.ngayGiao)}</p>}
              {selected.ngayKhachXacNhan && <p>Khách xác nhận nhận hàng: {formatDate(selected.ngayKhachXacNhan)} (bởi {selected.nguoiXacNhan})</p>}
              {selected.chiTiet.map((d) => (
                  <div key={d.maPhienBan} className="row between">
                    <span>{d.tenMay} · {formatPrice(d.donGia)}</span>
                    {selected.trangThai === 'CHO_XAC_NHAN' ? (
                        <input
                            className="qty"
                            type="number"
                            min="0"
                            defaultValue={d.soLuong}
                            onBlur={(e) => Number(e.target.value) !== d.soLuong && run(() => adminService.updateOrderLine(selected.maDonHang, d.maPhienBan, Number(e.target.value)), 'Đã cập nhật số lượng')}
                        />
                    ) : (
                        <span>× {d.soLuong}</span>
                    )}
                  </div>
              ))}
              <p className="muted">Chỉ chỉnh số lượng được khi đơn đang chờ xác nhận.</p>
              <div className="summary">
                <div className="row between"><span>Tạm tính</span><span>{formatPrice(selected.tamTinh)}</span></div>
                <div className="row between"><span>Giảm giá {selected.maKm && `(${selected.maKm})`}</span><span>- {formatPrice(selected.giamGia)}</span></div>
                <div className="row between"><b>Tổng</b><b className="price">{formatPrice(selected.tongTien)}</b></div>
              </div>
              {trangThaiCell(selected)}
            </Modal>
        )}

        {xacNhanGiao && (
            <Modal title="Xác nhận đã giao hàng" onClose={() => !dangGui && setXacNhanGiao(null)}>
              <p>Bạn xác nhận đơn hàng <b>{xacNhanGiao.maDonHang}</b> này đã được giao đến khách? Đơn hàng sẽ chuyển sang trạng thái chờ khách hàng xác nhận đã nhận hàng.</p>
              <div className="row end">
                <button className="btn" disabled={dangGui} onClick={() => setXacNhanGiao(null)}>Hủy</button>
                <button className="btn btn-primary" disabled={dangGui} onClick={danhDauDaGiao}>{dangGui ? 'Đang xử lý...' : 'Xác nhận đã giao'}</button>
              </div>
            </Modal>
        )}
      </>
  );
}