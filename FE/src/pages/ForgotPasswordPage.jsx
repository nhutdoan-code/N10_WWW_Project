
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import FormField from '../components/FormField';
import useToast from '../hooks/useToast';
import { authService } from '../service/authService';
import { errorMessage } from '../utils/format';

function EyeIcon({ visible }) {
  return (
      <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        {visible ? (
            <>
              <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
              <circle cx="12" cy="12" r="3" />
            </>
        ) : (
            <>
              <path d="M3 3l18 18" />
              <path d="M10.6 10.6a2 2 0 0 0 2.8 2.8" />
              <path d="M9.9 5.2A10.7 10.7 0 0 1 12 5c6 0 9.5 7 9.5 7a15.7 15.7 0 0 1-3 3.8" />
              <path d="M6.6 6.6A16.2 16.2 0 0 0 2.5 12s3.5 7 9.5 7a9.6 9.6 0 0 0 4-.8" />
            </>
        )}
      </svg>
  );
}

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const toast = useToast();

  const [step, setStep] = useState(1);
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [matKhau, setMatKhau] = useState('');
  const [xacNhanMatKhau, setXacNhanMatKhau] = useState('');
  const [showMatKhau, setShowMatKhau] = useState(false);
  const [showXacNhanMatKhau, setShowXacNhanMatKhau] = useState(false);
  const [loading, setLoading] = useState(false);

  const inputStyle = {
    width: '100%',
    boxSizing: 'border-box',
    padding: '12px',
    marginTop: '8px',
    marginBottom: '16px',
    border: '1px solid #d1d5db',
    borderRadius: '8px',
    fontSize: '16px',
  };

  const passwordWrapperStyle = {
    position: 'relative',
    marginTop: '8px',
    marginBottom: '16px',
  };

  const eyeButtonStyle = {
    position: 'absolute',
    right: '10px',
    top: '50%',
    transform: 'translateY(-50%)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '4px',
    border: 'none',
    background: 'transparent',
    color: '#666',
    cursor: 'pointer',
  };

  // Bước 1: Gửi OTP đến email
  const guiOtp = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      await authService.forgotPassword(email.trim());

      setStep(2);
      setOtp('');
      toast('Nếu email tồn tại, mã OTP sẽ được gửi đến email đó.', 'success');
    } catch (err) {
      toast(errorMessage(err), 'error');
    } finally {
      setLoading(false);
    }
  };

  // Bước 2: Xác minh OTP với backend
  const xacMinhOtp = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      const response = await authService.verifyResetOtp(email.trim(), otp.trim());
      const data = response?.data ?? response;
      const token = data?.resetToken;

      if (!token) {
        throw new Error('Không nhận được token đặt lại mật khẩu.');
      }

      setResetToken(token);
      setMatKhau('');
      setXacNhanMatKhau('');
      setShowMatKhau(false);
      setShowXacNhanMatKhau(false);
      setStep(3);

      toast('Xác minh OTP thành công.', 'success');
    } catch (err) {
      toast(errorMessage(err), 'error');
    } finally {
      setLoading(false);
    }
  };

  // Bước 3: Đặt mật khẩu mới
  const datLaiMatKhau = async (e) => {
    e.preventDefault();

    if (matKhau.length < 8) {
      toast('Mật khẩu phải có ít nhất 8 ký tự.', 'error');
      return;
    }

    if (matKhau !== xacNhanMatKhau) {
      toast('Mật khẩu xác nhận không khớp.', 'error');
      return;
    }

    setLoading(true);

    try {
      await authService.resetPassword(resetToken, matKhau);

      setResetToken('');
      setOtp('');
      setMatKhau('');
      setXacNhanMatKhau('');
      setStep(4);

      toast('Đổi mật khẩu thành công.', 'success');
    } catch (err) {
      toast(errorMessage(err), 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="form auth">
        <h2>Quên mật khẩu</h2>

        <p className="muted">
          {step === 1 && 'Nhập email đã đăng ký tài khoản PhoneNest.'}
          {step === 2 && 'Nhập mã OTP 6 chữ số được gửi đến email của bạn.'}
          {step === 3 && 'Tạo mật khẩu mới cho tài khoản của bạn.'}
          {step === 4 && 'Mật khẩu đã được cập nhật thành công.'}
        </p>

        {step < 4 && (
            <div style={{ display: 'flex', gap: '6px', margin: '20px 0' }}>
              {[1, 2, 3].map((n) => (
                  <div
                      key={n}
                      style={{
                        height: '5px',
                        flex: 1,
                        borderRadius: '5px',
                        background: step >= n ? '#1769aa' : '#e5e7eb',
                      }}
                  />
              ))}
            </div>
        )}

        {step === 1 && (
            <form onSubmit={guiOtp}>
              <FormField
                  label="Email đã đăng ký"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
              />

              <button className="btn btn-primary" type="submit" disabled={loading}>
                {loading ? 'Đang gửi OTP...' : 'Gửi mã OTP'}
              </button>
            </form>
        )}

        {step === 2 && (
            <form onSubmit={xacMinhOtp}>
              <label htmlFor="otp">Mã OTP</label>
              <input
                  id="otp"
                  style={inputStyle}
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  placeholder="Nhập mã OTP 6 chữ số"
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  pattern="[0-9]{6}"
                  maxLength={6}
                  required
              />

              <button className="btn btn-primary" type="submit" disabled={loading}>
                {loading ? 'Đang xác minh...' : 'Xác nhận OTP'}
              </button>

              <button
                  className="btn"
                  type="button"
                  disabled={loading}
                  onClick={() => {
                    setOtp('');
                    setStep(1);
                  }}
              >
                Quay lại
              </button>
            </form>
        )}

        {step === 3 && (
            <form onSubmit={datLaiMatKhau}>
              <label htmlFor="matKhau">Mật khẩu mới</label>
              <div style={passwordWrapperStyle}>
                <input
                    id="matKhau"
                    style={{ ...inputStyle, margin: 0, paddingRight: '48px' }}
                    type={showMatKhau ? 'text' : 'password'}
                    autoComplete="new-password"
                    placeholder="Nhập mật khẩu mới"
                    value={matKhau}
                    onChange={(e) => setMatKhau(e.target.value)}
                    minLength={8}
                    required
                />

                <button
                    type="button"
                    style={eyeButtonStyle}
                    onClick={() => setShowMatKhau((value) => !value)}
                    aria-label={showMatKhau ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    aria-pressed={showMatKhau}
                    title={showMatKhau ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  <EyeIcon visible={showMatKhau} />
                </button>
              </div>

              <label htmlFor="xacNhanMatKhau">Xác nhận mật khẩu mới</label>
              <div style={passwordWrapperStyle}>
                <input
                    id="xacNhanMatKhau"
                    style={{ ...inputStyle, margin: 0, paddingRight: '48px' }}
                    type={showXacNhanMatKhau ? 'text' : 'password'}
                    autoComplete="new-password"
                    placeholder="Nhập lại mật khẩu mới"
                    value={xacNhanMatKhau}
                    onChange={(e) => setXacNhanMatKhau(e.target.value)}
                    minLength={8}
                    required
                />

                <button
                    type="button"
                    style={eyeButtonStyle}
                    onClick={() => setShowXacNhanMatKhau((value) => !value)}
                    aria-label={showXacNhanMatKhau ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
                    aria-pressed={showXacNhanMatKhau}
                    title={showXacNhanMatKhau ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
                >
                  <EyeIcon visible={showXacNhanMatKhau} />
                </button>
              </div>

              <button className="btn btn-primary" type="submit" disabled={loading}>
                {loading ? 'Đang cập nhật...' : 'Đặt lại mật khẩu'}
              </button>
            </form>
        )}

        {step === 4 && (
            <button
                className="btn btn-primary"
                type="button"
                onClick={() => navigate('/login')}
            >
              Đăng nhập bằng mật khẩu mới
            </button>
        )}

        {step !== 4 && (
            <p className="muted">
              <Link to="/login">← Quay lại đăng nhập</Link>
            </p>
        )}
      </div>
  );
}
