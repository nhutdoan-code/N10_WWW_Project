import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import FormField from '../components/FormField';
import useAuth from '../hooks/useAuth';
import useToast from '../hooks/useToast';
import { errorMessage } from '../utils/format';

export default function LoginPage() {
  const [form, setForm] = useState({ taiKhoan: '', matKhau: '' });
  const [error, setError] = useState('');
  const { login } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const from = useLocation().state?.from;

  const submit = async (e) => {
    e.preventDefault();
    if (!form.taiKhoan.trim() || !form.matKhau) return setError('Vui lòng nhập đầy đủ thông tin');
    try {
      const user = await login(form.taiKhoan, form.matKhau);
      toast('Đăng nhập thành công!');
      navigate(user.role === 'ADMIN' ? '/admin' : from || '/', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <form className="form auth" onSubmit={submit}>
      <h2>Đăng nhập</h2>
      <FormField label="Email hoặc số điện thoại" value={form.taiKhoan} onChange={(e) => setForm({ ...form, taiKhoan: e.target.value })} />
      <FormField label="Mật khẩu" type="password" value={form.matKhau} onChange={(e) => setForm({ ...form, matKhau: e.target.value })} />
      {error && <p className="error">{error}</p>}
      <button className="btn btn-primary">Đăng nhập</button>
      <p className="muted"><Link to="/forgot-password">Quên mật khẩu?</Link></p>
      <p className="muted">Chưa có tài khoản? <Link to="/register">Đăng ký</Link></p>
    </form>
  );
}
