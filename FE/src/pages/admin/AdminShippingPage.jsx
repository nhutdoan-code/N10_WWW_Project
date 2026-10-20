import { useState } from 'react';
import FormField from '../../components/FormField';
import Modal from '../../components/Modal';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatPrice } from '../../utils/format';

const EMPTY = { khuVuc: '', phiCoBan: '', mienPhiTu: '', thoiGianGiao: '' };

export default function AdminShippingPage() {
  const { data: list, reload } = useFetch(adminService.shippingFees);
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
      <p className="muted">Phí tính theo tỉnh / thành của địa chỉ giao hàng. Khu vực không có trong danh sách dùng mức "Khác".</p>
      <table className="table">
        <thead>
          <tr><th>Khu vực</th><th>Phí giao hàng</th><th>Miễn phí từ</th><th>Thời gian giao</th><th></th></tr>
        </thead>
        <tbody>
          {list?.map((p) => (
            <tr key={p.maPhi}>
              <td><b>{p.khuVuc}</b></td>
              <td>{formatPrice(p.phiCoBan)}</td>
              <td>{p.mienPhiTu ? formatPrice(p.mienPhiTu) : 'Không áp dụng'}</td>
              <td>{p.thoiGianGiao}</td>
              <td><div className="row">
                <button className="btn" onClick={() => setEditing(p)}>Sửa</button>
                {p.khuVuc !== 'Khác' && (
                  <button className="btn btn-danger" onClick={() => window.confirm(`Xóa khu vực ${p.khuVuc}?`) && run(() => adminService.deleteShippingFee(p.maPhi), 'Đã xóa khu vực')}>Xóa</button>
                )}
              </div></td>
            </tr>
          ))}
        </tbody>
      </table>
      {editing && (
        <Modal title={editing.maPhi ? 'Sửa phí vận chuyển' : 'Thêm khu vực'} onClose={() => setEditing(null)}>
          <form className="form" onSubmit={submit}>
            <FormField label="Khu vực (tỉnh / thành phố)" value={editing.khuVuc} onChange={set('khuVuc')} disabled={editing.khuVuc === 'Khác'} required />
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
