import api from '../config/api';

export const customerService = {
  profile: () => api.get('/customer/profile'),
  updateProfile: (data) => api.put('/customer/profile', data),
  changePassword: (data) => api.put('/customer/profile/password', data),

  addresses: () => api.get('/customer/addresses'),
  saveAddress: (ma, data) => (ma ? api.put(`/customer/addresses/${ma}`, data) : api.post('/customer/addresses', data)),
  setDefaultAddress: (ma) => api.put(`/customer/addresses/${ma}/default`),
  deleteAddress: (ma) => api.delete(`/customer/addresses/${ma}`),

  orderReviews: (maDonHang) => api.get(`/customer/reviews/order/${maDonHang}`),
  review: (data) => api.post('/customer/reviews', data),
  uploadReviewImage: (file) => {
    const body = new FormData();
    body.append('file', file);
    return api.post('/customer/reviews/images', body);
  },
  ask: (maSanPham, noiDung) => api.post('/customer/questions', { maSanPham, noiDung }),

  returns: () => api.get('/customer/returns'),
  requestReturn: (data) => api.post('/customer/returns', data),

  notifications: () => api.get('/notifications'),
  markRead: (ma) => api.put(`/notifications/${ma}/read`),
  markAllRead: () => api.put('/notifications/read-all'),
};
