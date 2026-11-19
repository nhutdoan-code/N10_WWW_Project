import { useState } from 'react';
import AddressForm from '../components/AddressForm';
import Icon from '../components/Icon';
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

  // Địa chỉ mặc định luôn đứng đầu danh sách.
  const ds = (addresses || []).slice().sort((a, b) => Number(b.macDinh) - Number(a.macDinh));

  return (
    <div className="account-page">
      <div className="page-head">
        <div>
          <h2>Địa chỉ giao hàng</h2>
          <p className="muted">Địa chỉ mặc định được chọn sẵn khi bạn thanh toán. Bạn có thể đổi sang địa chỉ khác ở bước thanh toán.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setEditing({})}><Icon name="plus" size={16} /> Thêm địa chỉ</button>
      </div>

      {loading && <p className="muted">Đang tải...</p>}

      {addresses?.length === 0 && (
        <div className="empty-state">
          <h2>Chưa có địa chỉ giao hàng</h2>
          <p className="muted">Thêm một địa chỉ để thanh toán nhanh hơn ở lần mua sau.</p>
          <button className="btn btn-primary" onClick={() => setEditing({})}><Icon name="plus" size={16} /> Thêm địa chỉ</button>
        </div>
      )}

      {ds.length > 0 && (
        <ul className="address-list">
          {ds.map((a) => (
            <li key={a.maDiaChi} className={`address-item ${a.macDinh ? 'is-default' : ''}`}>
              <span className="address-icon" aria-hidden="true"><Icon name="pin" size={20} /></span>
              <div className="address-body">
                <div className="address-name">
                  <b>{a.tenNguoiNhan}</b>
                  <span className="address-phone">{a.soDienThoaiNhan}</span>
                  {a.macDinh && <span className="tag">Mặc định</span>}
                </div>
                <p>{[a.diaChiNhanHang, a.tenPhuong, a.tenTinh].filter(Boolean).join(', ')}</p>
              </div>
              <div className="address-actions">
                {!a.macDinh && (
                  <button className="btn btn-sm" onClick={() => run(() => customerService.setDefaultAddress(a.maDiaChi), 'Đã đặt làm mặc định')}>
                    Đặt mặc định
                  </button>
                )}
                <button className="btn btn-sm" onClick={() => setEditing(a)}><Icon name="edit" size={15} /> Sửa</button>
                <button
                  className="btn btn-sm btn-danger"
                  onClick={() => window.confirm('Xóa địa chỉ này?') && run(() => customerService.deleteAddress(a.maDiaChi), 'Đã xóa địa chỉ')}
                >
                  <Icon name="trash" size={15} /> Xóa
                </button>
              </div>
            </li>
          ))}
          <li>
            <button type="button" className="address-add" onClick={() => setEditing({})}>
              <Icon name="plus" size={18} /> Thêm địa chỉ mới
            </button>
          </li>
        </ul>
      )}

      {editing && (
        <Modal title={editing.maDiaChi ? 'Sửa địa chỉ' : 'Thêm địa chỉ'} onClose={() => setEditing(null)}>
          <AddressForm initial={editing} onSaved={() => { toast('Đã lưu địa chỉ'); setEditing(null); reload(); }} onCancel={() => setEditing(null)} />
        </Modal>
      )}
    </div>
  );
}
