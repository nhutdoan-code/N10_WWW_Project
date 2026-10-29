
import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import FormField from '../components/FormField';
import useAuth from '../hooks/useAuth';
import useToast from '../hooks/useToast';
import { errorMessage } from '../utils/format';

function EyeIcon({ visible }) {
    return (
        <svg
            width="21"
            height="21"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
        >
            {visible ? (
                <>
                    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
                    <circle cx="12" cy="12" r="3" />
                </>
            ) : (
                <>
                    <path d="M3 3l18 18" />
                    <path d="M9.9 5.2A10.7 10.7 0 0 1 12 5c6 0 9.5 7 9.5 7a15.7 15.7 0 0 1-3 3.8" />
                    <path d="M6.6 6.6A16.2 16.2 0 0 0 2.5 12s3.5 7 9.5 7a9.6 9.6 0 0 0 4-.8" />
                </>
            )}
        </svg>
    );
}

export default function LoginPage() {
    const [form, setForm] = useState({
        taiKhoan: '',
        matKhau: '',
    });
    const [error, setError] = useState('');
    const [showMatKhau, setShowMatKhau] = useState(false);

    const { login } = useAuth();
    const toast = useToast();
    const navigate = useNavigate();
    const from = useLocation().state?.from;

    const submit = async (e) => {
        e.preventDefault();
        setError('');

        if (!form.taiKhoan.trim() || !form.matKhau) {
            setError('Vui lòng nhập đầy đủ thông tin');
            return;
        }

        try {
            const user = await login(form.taiKhoan, form.matKhau);

            toast('Đăng nhập thành công!');

            navigate(
                user.role === 'ADMIN' ? '/admin' : from || '/',
                { replace: true }
            );
        } catch (err) {
            setError(errorMessage(err));
        }
    };

    return (
        <>
            <style>{`
        .login-password-field {
          position: relative;
        }

        .login-password-field input {
          padding-right: 46px !important;
          box-sizing: border-box;
        }

        .login-password-toggle {
          position: absolute;
          right: 10px;
          top: 37px;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 4px;
          border: none;
          border-radius: 4px;
          background: transparent;
          color: #6b7280;
          cursor: pointer;
        }

        .login-password-toggle:hover {
          color: #1769aa;
        }

        .login-password-toggle:focus-visible {
          outline: 2px solid #1769aa;
          outline-offset: 2px;
        }
      `}</style>

            <form className="form auth" onSubmit={submit}>
                <h2>Đăng nhập</h2>

                <FormField
                    label="Email hoặc số điện thoại"
                    value={form.taiKhoan}
                    onChange={(e) =>
                        setForm({ ...form, taiKhoan: e.target.value })
                    }
                />

                <div className="login-password-field">
                    <FormField
                        label="Mật khẩu"
                        type={showMatKhau ? 'text' : 'password'}
                        value={form.matKhau}
                        onChange={(e) =>
                            setForm({ ...form, matKhau: e.target.value })
                        }
                    />

                    <button
                        className="login-password-toggle"
                        type="button"
                        onClick={() => setShowMatKhau((prev) => !prev)}
                        aria-label={showMatKhau ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                        aria-pressed={showMatKhau}
                        title={showMatKhau ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    >
                        <EyeIcon visible={showMatKhau} />
                    </button>
                </div>

                {error && <p className="error">{error}</p>}

                <button className="btn btn-primary" type="submit">
                    Đăng nhập
                </button>

                <p className="muted">
                    <Link to="/forgot-password">Quên mật khẩu?</Link>
                </p>

                <p className="muted">
                    Chưa có tài khoản? <Link to="/register">Đăng ký</Link>
                </p>
            </form>
        </>
    );
}
