
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import FormField from '../components/FormField';
import useToast from '../hooks/useToast';
import { authService } from '../service/authService';
import { errorMessage } from '../utils/format';
import { validateRegister } from '../utils/validate';

const EMPTY = {
    hoTen: '',
    email: '',
    soDienThoai: '',
    ngaySinh: '',
    matKhau: '',
    nhapLai: '',
    maGioiThieu: '',
};

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

export default function RegisterPage() {
    const [form, setForm] = useState(EMPTY);
    const [errors, setErrors] = useState({});
    const [showMatKhau, setShowMatKhau] = useState(false);
    const [showNhapLai, setShowNhapLai] = useState(false);
    const [loading, setLoading] = useState(false);

    const toast = useToast();
    const navigate = useNavigate();

    const set = (key) => (e) => {
        setForm((prev) => ({
            ...prev,
            [key]: e.target.value,
        }));
    };

    const submit = async (e) => {
        e.preventDefault();

        const found = validateRegister(form);
        setErrors(found);

        if (Object.keys(found).length) {
            return;
        }

        setLoading(true);

        try {
            const { nhapLai, ...data } = form;

            await authService.register({
                ...data,
                ngaySinh: data.ngaySinh || null,
                maGioiThieu: data.maGioiThieu.trim() || null,
            });

            toast('Đăng ký tài khoản thành công!');
            navigate('/login');
        } catch (err) {
            toast(errorMessage(err), 'error');
        } finally {
            setLoading(false);
        }
    };

    return (
        <>
            <style>{`
        .register-password-field {
          position: relative;
        }

        .register-password-field input {
          padding-right: 46px !important;
          box-sizing: border-box;
        }

        .register-password-toggle {
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

        .register-password-toggle:hover {
          color: #1769aa;
        }

        .register-password-toggle:focus-visible {
          outline: 2px solid #1769aa;
          outline-offset: 2px;
        }
      `}</style>

            <form className="form auth" onSubmit={submit}>
                <h2>Đăng ký tài khoản</h2>

                <FormField
                    label="Họ tên"
                    value={form.hoTen}
                    onChange={set('hoTen')}
                    error={errors.hoTen}
                />

                <FormField
                    label="Email"
                    type="email"
                    value={form.email}
                    onChange={set('email')}
                    error={errors.email}
                />

                <FormField
                    label="Số điện thoại"
                    value={form.soDienThoai}
                    onChange={set('soDienThoai')}
                    error={errors.soDienThoai}
                />

                <FormField
                    label="Ngày sinh"
                    type="date"
                    value={form.ngaySinh}
                    onChange={set('ngaySinh')}
                />

                <div className="register-password-field">
                    <FormField
                        label="Mật khẩu"
                        type={showMatKhau ? 'text' : 'password'}
                        value={form.matKhau}
                        onChange={set('matKhau')}
                        error={errors.matKhau}
                    />

                    <button
                        className="register-password-toggle"
                        type="button"
                        onClick={() => setShowMatKhau((prev) => !prev)}
                        aria-label={showMatKhau ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                        aria-pressed={showMatKhau}
                        title={showMatKhau ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    >
                        <EyeIcon visible={showMatKhau} />
                    </button>
                </div>

                <div className="register-password-field">
                    <FormField
                        label="Nhập lại mật khẩu"
                        type={showNhapLai ? 'text' : 'password'}
                        value={form.nhapLai}
                        onChange={set('nhapLai')}
                        error={errors.nhapLai}
                    />

                    <button
                        className="register-password-toggle"
                        type="button"
                        onClick={() => setShowNhapLai((prev) => !prev)}
                        aria-label={showNhapLai ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
                        aria-pressed={showNhapLai}
                        title={showNhapLai ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
                    >
                        <EyeIcon visible={showNhapLai} />
                    </button>
                </div>

                <FormField
                    label="Mã giới thiệu (nếu có)"
                    value={form.maGioiThieu}
                    onChange={set('maGioiThieu')}
                />

                <button className="btn btn-primary" type="submit" disabled={loading}>
                    {loading ? 'Đang tạo tài khoản...' : 'Tạo tài khoản'}
                </button>

                <p className="muted">
                    Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
                </p>
            </form>
        </>
    );
}
