import { useState } from 'react';
import AddressForm from '../components/AddressForm';
import Modal from '../components/Modal';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { errorMessage } from '../utils/format';

export default function AddressesPage() {
  const { data: addresses, loading, reload } = useFetch(customerService.addresses);
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

  return (
    <>
      <div className="row between">
        <h2>Địa chỉ giao hàng</h2>
        <button className="btn btn-primary" onClick={() => setEditing({})}>+ Thêm địa chỉ</button>
      </div>
      {loading && <p className="muted">Đang tải...</p>}
      {addresses?.length === 0 && <p className="muted">Bạn chưa có địa chỉ nào.</p>}
      <div className="grid">
        {addresses?.map((a) => (
          <div key={a.maDiaChi} className="card">
            <b>{a.tenNguoiNhan}</b> {a.macDinh && <span className="badge badge-DA_GIAO">Mặc định</span>}
            <p className="muted">{a.soDienThoaiNhan}</p>
            <p>{a.diaChiNhanHang}, {a.tenPhuong}, {a.tenTinh}</p>
            <div className="row">
              <button className="btn" onClick={() => setEditing(a)}>Sửa</button>
              {!a.macDinh && <button className="btn" onClick={() => run(() => customerService.setDefaultAddress(a.maDiaChi), 'Đã đặt làm mặc định')}>Đặt mặc định</button>}
              <button className="btn btn-danger" onClick={() => window.confirm('Xóa địa chỉ này?') && run(() => customerService.deleteAddress(a.maDiaChi), 'Đã xóa địa chỉ')}>Xóa</button>
            </div>
          </div>
        ))}
      </div>
      {editing && (
        <Modal title={editing.maDiaChi ? 'Sửa địa chỉ' : 'Thêm địa chỉ'} onClose={() => setEditing(null)}>
          <AddressForm initial={editing} onSaved={() => { toast('Đã lưu địa chỉ'); setEditing(null); reload(); }} onCancel={() => setEditing(null)} />
        </Modal>
      )}
    </>
  );
}
