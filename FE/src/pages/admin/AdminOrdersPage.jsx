import { useState } from 'react';
import Modal from '../../components/Modal';
import { ORDER_STATUS } from '../../config/constants';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatDate, formatPrice } from '../../utils/format';

export default function AdminOrdersPage() {
  const { data: orders, reload } = useFetch(adminService.orders);
  const [selected, setSelected] = useState(null);
  const toast = useToast();

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

  const select = (o) => (
    <select value={o.trangThai} onChange={(e) => run(() => adminService.updateOrderStatus(o.maDonHang, e.target.value), 'Đã cập nhật trạng thái')}>
      {Object.entries(ORDER_STATUS).map(([value, label]) => (
        <option key={value} value={value}>{label}</option>
      ))}
    </select>
  );

  return (
    <>
      <h2>Quản lý đơn hàng (mới nhất trước)</h2>
      <table className="table">
        <thead>
          <tr><th>Mã đơn</th><th>Khách hàng</th><th>Ngày mua</th><th>Sản phẩm</th><th>Tổng tiền</th><th>Trạng thái</th><th></th></tr>
        </thead>
        <tbody>
          {orders?.map((o) => (
            <tr key={o.maDonHang}>
              <td>{o.maDonHang}</td>
              <td>{o.tenKhach || o.maKh}</td>
              <td>{formatDate(o.ngayMua)}</td>
              <td>{o.chiTiet.map((d) => `${d.tenMay} × ${d.soLuong}`).join(', ')}</td>
              <td>{formatPrice(o.tongTien)}</td>
              <td>{select(o)}</td>
              <td><button className="btn" onClick={() => setSelected(o)}>Chi tiết</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      {selected && (
        <Modal title={`Đơn ${selected.maDonHang}`} onClose={() => setSelected(null)}>
          <p className="muted">Khách: {selected.tenKhach || selected.maKh} · {formatDate(selected.ngayMua)}</p>
          <p>Giao đến: {selected.diaChiGiaoHang || '—'}</p>
          {selected.chiTiet.map((d) => (
            <div key={d.maSanPham} className="row between">
              <span>{d.tenMay} · {formatPrice(d.donGia)}</span>
              {selected.trangThai === 'CHO_XAC_NHAN' ? (
                <input
                  className="qty"
                  type="number"
                  min="0"
                  defaultValue={d.soLuong}
                  onBlur={(e) => Number(e.target.value) !== d.soLuong && run(() => adminService.updateOrderLine(selected.maDonHang, d.maSanPham, Number(e.target.value)), 'Đã cập nhật số lượng')}
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
          {select(selected)}
        </Modal>
      )}
    </>
  );
}
