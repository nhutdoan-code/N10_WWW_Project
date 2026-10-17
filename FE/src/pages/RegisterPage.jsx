import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import FormField from '../components/FormField';
import useToast from '../hooks/useToast';
import { authService } from '../service/authService';
import { errorMessage } from '../utils/format';
import { validateRegister } from '../utils/validate';

const EMPTY = { hoTen: '', email: '', soDienThoai: '', ngaySinh: '', matKhau: '', nhapLai: '', maGioiThieu: '' };

export default function RegisterPage() {
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const toast = useToast();
  const navigate = useNavigate();
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    const found = validateRegister(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    try {
      const { nhapLai, ...data } = form;
      await authService.register({ ...data, ngaySinh: data.ngaySinh || null, maGioiThieu: data.maGioiThieu.trim() || null });
      toast('Đăng ký tài khoản thành công!');
      navigate('/login');
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  };

  return (
    <form className="form auth" onSubmit={submit}>
      <h2>Đăng ký tài khoản</h2>
      <FormField label="Họ tên" value={form.hoTen} onChange={set('hoTen')} error={errors.hoTen} />
      <FormField label="Email" value={form.email} onChange={set('email')} error={errors.email} />
      <FormField label="Số điện thoại" value={form.soDienThoai} onChange={set('soDienThoai')} error={errors.soDienThoai} />
      <FormField label="Ngày sinh" type="date" value={form.ngaySinh} onChange={set('ngaySinh')} />
      <FormField label="Mật khẩu" type="password" value={form.matKhau} onChange={set('matKhau')} error={errors.matKhau} />
      <FormField label="Nhập lại mật khẩu" type="password" value={form.nhapLai} onChange={set('nhapLai')} error={errors.nhapLai} />
      <FormField label="Mã giới thiệu (nếu có)" value={form.maGioiThieu} onChange={set('maGioiThieu')} />
      <button className="btn btn-primary">Tạo tài khoản</button>
      <p className="muted">Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>
    </form>
  );
}
