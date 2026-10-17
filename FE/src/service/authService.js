import api from '../config/api';

export const authService = {
  login: (taiKhoan, matKhau) => api.post('/auth/login', { taiKhoan, matKhau }),
  register: (data) => api.post('/auth/register', data),
  forgotPassword: (email) => api.post('/auth/forgot-password', { email }),
  resetPassword: (token, matKhau) => api.post('/auth/reset-password', { token, matKhau }),
};
