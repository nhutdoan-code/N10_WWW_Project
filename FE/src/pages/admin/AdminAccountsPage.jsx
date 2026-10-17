import { useState } from 'react';
import FormField from '../../components/FormField';
import Modal from '../../components/Modal';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatDay } from '../../utils/format';

export default function AdminAccountsPage() {
  const { data: accounts, reload } = useFetch(adminService.accounts);
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
      <h2>Quản lý tài khoản</h2>
      <table className="table">
        <thead>
          <tr><th>Mã</th><th>Họ tên</th><th>Email</th><th>Số điện thoại</th><th>Vai trò</th><th>Ngày tạo</th><th>Trạng thái</th><th></th></tr>
        </thead>
        <tbody>
          {accounts?.map((a) => (
            <tr key={a.maTk}>
              <td>{a.maTk}</td>
              <td>{a.hoTen}</td>
              <td>{a.email}</td>
              <td>{a.soDienThoai}</td>
              <td>{a.role}</td>
              <td>{formatDay(a.ngayTao)}</td>
              <td>{a.trangThaiHoatDong ? 'Hoạt động' : 'Đã khóa'}</td>
              <td className="row">
                {a.role === 'CUSTOMER' && (
                  <>
                    <button className="btn" onClick={() => setEditing(a)}>Sửa</button>
                    <button className="btn" onClick={() => run(() => adminService.toggleLock(a.maTk), 'Đã đổi trạng thái tài khoản')}>{a.trangThaiHoatDong ? 'Khóa' : 'Mở khóa'}</button>
                    <button className="btn btn-danger" onClick={() => window.confirm(`Xóa tài khoản ${a.email}?`) && run(() => adminService.deleteAccount(a.maTk), 'Đã xóa tài khoản')}>Xóa</button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {editing && (
        <Modal title="Cập nhật tài khoản" onClose={() => setEditing(null)}>
          <form
            className="form"
            onSubmit={(e) => {
              e.preventDefault();
              run(() => adminService.updateAccount(editing.maTk, { hoTen: editing.hoTen, soDienThoai: editing.soDienThoai }), 'Đã cập nhật tài khoản');
            }}
          >
            <FormField label="Họ tên" value={editing.hoTen} onChange={(e) => setEditing({ ...editing, hoTen: e.target.value })} required />
            <FormField label="Số điện thoại" value={editing.soDienThoai} onChange={(e) => setEditing({ ...editing, soDienThoai: e.target.value })} required />
            <p className="muted">Không thể xem hoặc sửa mật khẩu của khách hàng. Chỉ xóa được tài khoản chưa từng đặt hàng.</p>
            <button className="btn btn-primary">Lưu</button>
          </form>
        </Modal>
      )}
    </>
  );
}
