import api from '../config/api';

export const cartService = {
  get: () => api.get('/customer/cart'),
  add: (maSanPham, soLuong) => api.post('/customer/cart', { maSanPham, soLuong }),
  update: (maSanPham, soLuong) => api.put('/customer/cart', { maSanPham, soLuong }),
  remove: (ma) => api.delete(`/customer/cart/${ma}`),
  clear: () => api.delete('/customer/cart'),
  sync: (items) => api.post('/customer/cart/sync', items),
};
