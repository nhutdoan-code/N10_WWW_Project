
import api from '../config/api';

export const cartService = {
  // Lấy giỏ hàng
  get: () => api.get('/customer/cart'),
  // Thêm sản phẩm
  add: (maPhienBan, soLuong) => api.post('/customer/cart', {maPhienBan, soLuong,}),
  // Cập nhật số lượng
  update: (maPhienBan, soLuong) => api.put('/customer/cart', {maPhienBan, soLuong,}),
  // Xóa một phiên bản
  remove: (maPhienBan) => api.delete(`/customer/cart/${encodeURIComponent(maPhienBan)}`),
  // Xóa toàn bộ giỏ hàng
  clear: () => api.delete('/customer/cart'),
  // Đồng bộ giỏ hàng khách
  sync: (items) => api.post('/customer/cart/sync', items),};
