
import api from '../config/api';

export const authService = {
  // Đăng nhập
  login: (taiKhoan, matKhau) =>
      api.post('/auth/login', { taiKhoan, matKhau }),

  // Đăng ký
  register: (data) =>
      api.post('/auth/register', data),

  // Bước 1: Gửi OTP qua email
  forgotPassword: (email) =>
      api.post('/auth/forgot-password', { email }),

  // Bước 2: Xác minh OTP
  verifyResetOtp: (email, otp) =>
      api.post('/auth/verify-reset-otp', { email, otp }),

  // Bước 3: Đặt lại mật khẩu
  resetPassword: (resetToken, matKhau) =>
      api.post('/auth/reset-password', { resetToken, matKhau }),
};
