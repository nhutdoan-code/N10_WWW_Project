import { useEffect, useState } from 'react';
import AdminPagination from '../../components/AdminPagination';
import AdminFetchError from '../../components/AdminFetchError';
import FormField from '../../components/FormField';
import Modal from '../../components/Modal';
import useDebounce from '../../hooks/useDebounce';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatDay } from '../../utils/format';

export default function AdminAccountsPage() {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const debounced = useDebounce(q);
  const { data: result, loading, error, reload } = useFetch(
    () => adminService.accountsPage({ q: debounced || undefined, page, size }),
    [debounced, page, size]
  );
  const accounts = result?.content || [];
  const [editing, setEditing] = useState(null);
  const [creating, setCreating] = useState(false);
  const [createForm, setCreateForm] = useState({ hoTen: '', email: '', soDienThoai: '', matKhau: '', ngaySinh: '' });
  const [creatingSubmitting, setCreatingSubmitting] = useState(false);
  const toast = useToast();

  useEffect(() => {
    if (result && page > 0 && (result.totalElements === 0 || page >= result.totalPages)) {
      setPage(Math.max(0, result.totalPages - 1));
    }
  }, [result, page]);

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

  const submitCreate = async (event) => {
    event.preventDefault();
    setCreatingSubmitting(true);
    try {
      await adminService.createAccount({
        ...createForm,
        ngaySinh: createForm.ngaySinh || null,
      });
      toast('Đã tạo tài khoản khách hàng');
      setCreating(false);
      setCreateForm({ hoTen: '', email: '', soDienThoai: '', matKhau: '', ngaySinh: '' });
      setPage(0);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setCreatingSubmitting(false);
    }
  };

  return (
    <>
      <div className="row between">
        <h2>Quản lý tài khoản</h2>
        <button className="btn btn-primary" onClick={() => setCreating(true)}>+ Tạo tài khoản khách hàng</button>
      </div>
      <div className="row" style={{ gap: '12px', marginBottom: '16px' }}>
        <input
          style={{ flex: 1 }}
          type="search"
          placeholder="Tìm theo mã, họ tên, email hoặc số điện thoại..."
          value={q}
          onChange={(event) => { setQ(event.target.value); setPage(0); }}
        />
      </div>
      <AdminFetchError error={error} onRetry={reload} loading={loading} />
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
          {!loading && !error && accounts.length === 0 && (
            <tr><td colSpan={8} style={{ textAlign: 'center', padding: '24px' }}>Không tìm thấy tài khoản phù hợp.</td></tr>
          )}
        </tbody>
      </table>
      <AdminPagination
        page={page}
        totalPages={result?.totalPages}
        totalElements={result?.totalElements}
        size={size}
        loading={loading}
        onPageChange={setPage}
        onSizeChange={(nextSize) => { setSize(nextSize); setPage(0); }}
      />
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
      {creating && (
        <Modal title="Tạo tài khoản khách hàng" onClose={() => !creatingSubmitting && setCreating(false)}>
          <form className="form" onSubmit={submitCreate}>
            <FormField label="Họ tên" value={createForm.hoTen} onChange={(e) => setCreateForm({ ...createForm, hoTen: e.target.value })} required maxLength={150} />
            <FormField label="Email" type="email" value={createForm.email} onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })} required maxLength={150} />
            <FormField label="Số điện thoại" type="tel" inputMode="tel" pattern="0[0-9]{9}" title="Số điện thoại gồm 10 chữ số, bắt đầu bằng 0" value={createForm.soDienThoai} onChange={(e) => setCreateForm({ ...createForm, soDienThoai: e.target.value })} required />
            <FormField label="Mật khẩu khởi tạo (ít nhất 8 ký tự)" type="password" minLength={8} maxLength={100} value={createForm.matKhau} onChange={(e) => setCreateForm({ ...createForm, matKhau: e.target.value })} required />
            <FormField label="Ngày sinh (không bắt buộc)" type="date" value={createForm.ngaySinh} onChange={(e) => setCreateForm({ ...createForm, ngaySinh: e.target.value })} />
            <p className="muted">Chỉ tạo tài khoản khách hàng. Mật khẩu được mã hóa trước khi lưu.</p>
            <div className="row end">
              <button type="button" className="btn" disabled={creatingSubmitting} onClick={() => setCreating(false)}>Hủy</button>
              <button className="btn btn-primary" disabled={creatingSubmitting}>{creatingSubmitting ? 'Đang tạo...' : 'Tạo tài khoản'}</button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}
