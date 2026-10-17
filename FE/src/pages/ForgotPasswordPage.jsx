import { useState } from 'react';
import { Link } from 'react-router-dom';
import FormField from '../components/FormField';
import useToast from '../hooks/useToast';
import { authService } from '../service/authService';
import { errorMessage } from '../utils/format';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const toast = useToast();

  const submit = async (e) => {
    e.preventDefault();
    try {
      await authService.forgotPassword(email.trim());
      setSent(true);
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  };

  return (
    <form className="form auth" onSubmit={submit}>
      <h2>Quên mật khẩu</h2>
      {sent ? (
        <p>Nếu email tồn tại trong hệ thống, liên kết đặt lại mật khẩu đã được gửi. Vui lòng kiểm tra hộp thư.</p>
      ) : (
        <>
          <FormField label="Email đã đăng ký" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <button className="btn btn-primary">Gửi liên kết đặt lại</button>
        </>
      )}
      <p className="muted"><Link to="/login">← Quay lại đăng nhập</Link></p>
    </form>
  );
}
