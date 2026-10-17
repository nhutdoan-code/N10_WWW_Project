import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import FormField from '../components/FormField';
import useToast from '../hooks/useToast';
import { authService } from '../service/authService';
import { errorMessage } from '../utils/format';

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const [form, setForm] = useState({ matKhau: '', nhapLai: '' });
  const toast = useToast();
  const navigate = useNavigate();

  const submit = async (e) => {
    e.preventDefault();
    if (form.matKhau.length < 8) return toast('Mật khẩu phải từ 8 ký tự', 'error');
    if (form.matKhau !== form.nhapLai) return toast('Mật khẩu nhập lại không khớp', 'error');
    try {
      await authService.resetPassword(token, form.matKhau);
      toast('Đặt lại mật khẩu thành công, hãy đăng nhập');
      navigate('/login');
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  };

  return (
    <form className="form auth" onSubmit={submit}>
      <h2>Đặt lại mật khẩu</h2>
      <FormField label="Mật khẩu mới" type="password" value={form.matKhau} onChange={(e) => setForm({ ...form, matKhau: e.target.value })} />
      <FormField label="Nhập lại mật khẩu" type="password" value={form.nhapLai} onChange={(e) => setForm({ ...form, nhapLai: e.target.value })} />
      <button className="btn btn-primary">Đổi mật khẩu</button>
      <p className="muted"><Link to="/login">← Quay lại đăng nhập</Link></p>
    </form>
  );
}
