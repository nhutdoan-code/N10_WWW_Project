import { useState } from 'react';
import FormField from '../../components/FormField';
import Modal from '../../components/Modal';
import { locationService } from '../../service/locationService';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatPrice } from '../../utils/format';

const EMPTY = { maTinh: '', phiCoBan: '', mienPhiTu: '', thoiGianGiao: '' };

export default function AdminShippingPage() {
  const { data: list, reload } = useFetch(adminService.shippingFees);
  const { data: provinces } = useFetch(locationService.provinces);
  const [editing, setEditing] = useState(null);
  const toast = useToast();

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
    run(
      () => adminService.saveShippingFee(editing.maPhi, {
        ...editing,
        phiCoBan: Number(editing.phiCoBan),
        mienPhiTu: editing.mienPhiTu === '' || editing.mienPhiTu == null ? null : Number(editing.mienPhiTu),
      }),
      'Đã lưu bảng phí'
    );
  };

  return (
    <>
      <div className="row between">
        <h2>Phí vận chuyển</h2>
        <button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY })}>+ Thêm khu vực</button>
      </div>
      <p className="muted">Phí tính theo tỉnh / thành phố của địa chỉ giao hàng. Nơi chưa có trong danh sách sẽ dùng mức mặc định.</p>
      <table className="table">
        <thead>
          <tr><th>Khu vực</th><th>Phí giao hàng</th><th>Miễn phí từ</th><th>Thời gian giao</th><th></th></tr>
        </thead>
        <tbody>
          {list?.map((p) => (
            <tr key={p.maPhi}>
              <td><b>{p.tenKhuVuc}</b></td>
              <td>{formatPrice(p.phiCoBan)}</td>
              <td>{p.mienPhiTu ? formatPrice(p.mienPhiTu) : 'Không áp dụng'}</td>
              <td>{p.thoiGianGiao}</td>
              <td><div className="row">
                <button className="btn" onClick={() => setEditing(p)}>Sửa</button>
                {p.maTinh && (
                  <button className="btn btn-danger" onClick={() => window.confirm(`Xóa mức phí của ${p.tenKhuVuc}?`) && run(() => adminService.deleteShippingFee(p.maPhi), 'Đã xóa khu vực')}>Xóa</button>
                )}
              </div></td>
            </tr>
          ))}
        </tbody>
      </table>
      {editing && (
        <Modal title={editing.maPhi ? 'Sửa phí vận chuyển' : 'Thêm khu vực'} onClose={() => setEditing(null)}>
          <form className="form" onSubmit={submit}>
            {editing.maPhi ? (
              <FormField label="Khu vực" value={editing.tenKhuVuc} disabled />
            ) : (
              <label className="field">
                <span>Tỉnh / Thành phố</span>
                <select value={editing.maTinh} onChange={set('maTinh')} required>
                  <option value="">Chọn tỉnh / thành phố</option>
                  {(provinces || []).slice().sort((a, b) => a.ten.localeCompare(b.ten, 'vi')).map((t) => <option key={t.ma} value={t.ma}>{t.ten}</option>)}
                </select>
              </label>
            )}
            <div className="grid-2">
              <FormField label="Phí giao hàng (₫)" type="number" min="0" value={editing.phiCoBan} onChange={set('phiCoBan')} required />
              <FormField label="Miễn phí cho đơn từ (₫)" type="number" min="0" value={editing.mienPhiTu ?? ''} onChange={set('mienPhiTu')} />
            </div>
            <FormField label="Thời gian giao dự kiến" value={editing.thoiGianGiao || ''} onChange={set('thoiGianGiao')} placeholder="vd: 2 - 3 ngày" />
            <button className="btn btn-primary">Lưu</button>
          </form>
        </Modal>
      )}
    </>
  );
}
