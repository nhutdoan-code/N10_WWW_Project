import { useState } from 'react';
import FormField from '../../components/FormField';
import Modal from '../../components/Modal';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatPrice } from '../../utils/format';

const EMPTY = { maKm: '', tenKm: '', loai: 'DON_HANG', phanTramGiam: '', soTienGiamToiDa: '', donToiThieu: 0, soLuong: '', chiKhachMoi: false, ngayBatDau: '', ngayKetThuc: '', dieuKienApDung: '' };

export default function AdminPromotionsPage() {
  const { data: list, reload } = useFetch(adminService.promotions);
  const [editing, setEditing] = useState(null);
  const toast = useToast();
  const today = new Date().toISOString().slice(0, 10);

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      setEditing(null);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  const set = (key) => (e) => setEditing({ ...editing, [key]: e.target.value });
  const submit = (e) => {
    e.preventDefault();
    const { isNew, ...data } = editing;
    run(
      () => adminService.savePromotion(isNew ? null : data.maKm, {
        ...data,
        phanTramGiam: Number(data.phanTramGiam),
        soTienGiamToiDa: data.soTienGiamToiDa === '' || data.soTienGiamToiDa == null ? null : Number(data.soTienGiamToiDa),
        donToiThieu: Number(data.donToiThieu || 0),
        soLuong: data.soLuong === '' || data.soLuong == null ? null : Number(data.soLuong),
      }),
      'Đã lưu khuyến mãi'
    );
  };

  return (
    <>
      <div className="row between">
        <h2>Quản lý khuyến mãi</h2>
        <button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY, isNew: true })}>+ Tạo mã</button>
      </div>
      <table className="table">
        <thead>
          <tr><th>Mã</th><th>Tên chương trình</th><th>Loại</th><th>Giảm</th><th>Tối đa</th><th>Đơn tối thiểu</th><th>Đã dùng</th><th>Thời gian</th><th>Trạng thái</th><th></th></tr>
        </thead>
        <tbody>
          {list?.map((k) => {
            const hieuLuc = k.ngayBatDau <= today && today <= k.ngayKetThuc;
            return (
              <tr key={k.maKm}>
                <td><b>{k.maKm}</b></td>
                <td>{k.tenKm}</td>
                <td>{k.loai === 'VAN_CHUYEN' ? 'Vận chuyển' : 'Đơn hàng'}</td>
                <td>{k.phanTramGiam}%</td>
                <td>{k.soTienGiamToiDa ? formatPrice(k.soTienGiamToiDa) : '—'}</td>
                <td>{Number(k.donToiThieu) > 0 ? formatPrice(k.donToiThieu) : '—'}</td>
                <td>{k.daDung}{k.soLuong != null && ` / ${k.soLuong}`}</td>
                <td>{k.ngayBatDau} → {k.ngayKetThuc}</td>
                <td>{hieuLuc ? 'Đang chạy' : today < k.ngayBatDau ? 'Sắp diễn ra' : 'Hết hạn'}</td>
                <td className="row">
                  <button className="btn" onClick={() => setEditing(k)}>Sửa</button>
                  <button className="btn btn-danger" onClick={() => window.confirm(`Xóa mã ${k.maKm}?`) && run(() => adminService.deletePromotion(k.maKm), 'Đã xóa khuyến mãi')}>Xóa</button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {editing && (
        <Modal title={editing.isNew ? 'Tạo khuyến mãi' : 'Sửa khuyến mãi'} onClose={() => setEditing(null)}>
          <form className="form" onSubmit={submit}>
            <FormField label="Mã khuyến mãi" value={editing.maKm} onChange={set('maKm')} disabled={!editing.isNew} required />
            <FormField label="Tên chương trình" value={editing.tenKm} onChange={set('tenKm')} required />
            <label className="field">
              <span>Loại voucher</span>
              <select value={editing.loai || 'DON_HANG'} onChange={set('loai')}>
                <option value="DON_HANG">Giảm giá đơn hàng</option>
                <option value="VAN_CHUYEN">Giảm phí vận chuyển</option>
              </select>
            </label>
            <div className="grid-2">
              <FormField label="Phần trăm giảm (%)" type="number" min="1" max="100" value={editing.phanTramGiam} onChange={set('phanTramGiam')} required />
              <FormField label="Giảm tối đa (₫)" type="number" min="0" value={editing.soTienGiamToiDa ?? ''} onChange={set('soTienGiamToiDa')} />
            </div>
            <div className="grid-2">
              <FormField label="Đơn tối thiểu (₫)" type="number" min="0" value={editing.donToiThieu ?? 0} onChange={set('donToiThieu')} />
              <FormField label="Số lượng mã (để trống = không giới hạn)" type="number" min="1" value={editing.soLuong ?? ''} onChange={set('soLuong')} />
            </div>
            <label className="option">
              <input type="checkbox" checked={!!editing.chiKhachMoi} onChange={(e) => setEditing({ ...editing, chiKhachMoi: e.target.checked })} /> Chỉ dành cho khách mua lần đầu
            </label>
            <div className="grid-2">
              <FormField label="Từ ngày" type="date" value={editing.ngayBatDau} onChange={set('ngayBatDau')} required />
              <FormField label="Đến ngày" type="date" value={editing.ngayKetThuc} onChange={set('ngayKetThuc')} required />
            </div>
            <FormField label="Điều kiện áp dụng" value={editing.dieuKienApDung || ''} onChange={set('dieuKienApDung')} />
            <button className="btn btn-primary">Lưu</button>
          </form>
        </Modal>
      )}
    </>
  );
}
