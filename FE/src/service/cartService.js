import api from '../config/api';

export const cartService = {
  get: () => api.get('/customer/cart'),

  add: (maPhienBan, soLuong) => api.post('/customer/cart', { maPhienBan, soLuong }),

  update: (maPhienBan, soLuong) => api.put('/customer/cart', { maPhienBan, soLuong }),

  remove: (maPhienBan) =>api.delete(`/customer/cart/${encodeURIComponent(maPhienBan)}`),

  clear: () => api.delete('/customer/cart'),

  sync: (items) => api.post('/customer/cart/sync', items),
};