import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import FormField from '../components/FormField';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { errorMessage } from '../utils/format';

export default function ProfilePage() {
  const { data: profile, reload } = useFetch(customerService.profile);
  const [form, setForm] = useState(null);
  const [pw, setPw] = useState({ matKhauCu: '', matKhauMoi: '', nhapLai: '' });
  const toast = useToast();

  useEffect(() => {
    if (profile) setForm({ hoTen: profile.hoTen, soDienThoai: profile.soDienThoai, ngaySinh: profile.ngaySinh || '', anhDaiDien: profile.anhDaiDien || '' });
  }, [profile]);

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  const changePassword = (e) => {
    e.preventDefault();
    if (pw.matKhauMoi !== pw.nhapLai) return toast('Mật khẩu nhập lại không khớp', 'error');
    run(async () => {
      await customerService.changePassword({ matKhauCu: pw.matKhauCu, matKhauMoi: pw.matKhauMoi });
      setPw({ matKhauCu: '', matKhauMoi: '', nhapLai: '' });
    }, 'Đã đổi mật khẩu');
  };

  if (!form) return <p className="muted">Đang tải...</p>;
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <div className="checkout">
      <h2>Tài khoản của tôi</h2>
      <p className="muted">
        {profile.email} · Hạng <b>{profile.hangThanhVien}</b> · {profile.diemTichLuy} điểm · Mã giới thiệu: <b>{profile.maGioiThieu}</b>
      </p>
      <p><Link to="/account/addresses">Quản lý địa chỉ giao hàng →</Link></p>

      <form
        className="form card"
        onSubmit={(e) => {
          e.preventDefault();
          run(() => customerService.updateProfile({ ...form, ngaySinh: form.ngaySinh || null }), 'Đã cập nhật thông tin');
        }}
      >
        <h3>Thông tin cá nhân</h3>
        <FormField label="Họ tên" value={form.hoTen} onChange={set('hoTen')} required />
        <FormField label="Số điện thoại" value={form.soDienThoai} onChange={set('soDienThoai')} required />
        <FormField label="Ngày sinh" type="date" value={form.ngaySinh} onChange={set('ngaySinh')} />
        <FormField label="Link ảnh đại diện" value={form.anhDaiDien} onChange={set('anhDaiDien')} />
        <button className="btn btn-primary">Lưu thay đổi</button>
      </form>

      <form className="form card" onSubmit={changePassword}>
        <h3>Đổi mật khẩu</h3>
        <FormField label="Mật khẩu hiện tại" type="password" value={pw.matKhauCu} onChange={(e) => setPw({ ...pw, matKhauCu: e.target.value })} required />
        <FormField label="Mật khẩu mới (từ 8 ký tự)" type="password" value={pw.matKhauMoi} onChange={(e) => setPw({ ...pw, matKhauMoi: e.target.value })} required />
        <FormField label="Nhập lại mật khẩu mới" type="password" value={pw.nhapLai} onChange={(e) => setPw({ ...pw, nhapLai: e.target.value })} required />
        <button className="btn btn-primary">Đổi mật khẩu</button>
      </form>
    </div>
  );
}
