import api from '../config/api';

const save = (base) => (ma, data) => (ma ? api.put(`${base}/${ma}`, data) : api.post(base, data));

export const adminService = {
  products: (q) => api.get('/admin/products', { params: { q } }),
  saveProduct: save('/admin/products'),
  deleteProduct: (ma) => api.delete(`/admin/products/${ma}`),
  saveCategory: save('/admin/categories'),
  deleteCategory: (ma) => api.delete(`/admin/categories/${ma}`),

  orders: () => api.get('/admin/orders'),
  updateOrderStatus: (ma, trangThai) => api.put(`/admin/orders/${ma}/status`, null, { params: { trangThai } }),
  updateOrderLine: (ma, maSanPham, soLuong) => api.put(`/admin/orders/${ma}/items/${maSanPham}`, { soLuong }),

  accounts: () => api.get('/admin/accounts'),
  updateAccount: (ma, data) => api.put(`/admin/accounts/${ma}`, data),
  deleteAccount: (ma) => api.delete(`/admin/accounts/${ma}`),
  toggleLock: (ma) => api.put(`/admin/accounts/${ma}/toggle-lock`),

  inventory: (loc) => api.get('/admin/inventory', { params: { loc } }),
  importStock: (ma, soLuong) => api.post(`/admin/inventory/${ma}/import`, { soLuong }),
  setStock: (ma, soLuong) => api.put(`/admin/inventory/${ma}`, { soLuong }),

  promotions: () => api.get('/admin/promotions'),
  savePromotion: (ma, data) => (ma ? api.put(`/admin/promotions/${ma}`, data) : api.post('/admin/promotions', data)),
  shippingFees: () => api.get('/admin/shipping-fees'),
  saveShippingFee: (ma, data) => (ma ? api.put(`/admin/shipping-fees/${ma}`, data) : api.post('/admin/shipping-fees', data)),
  deleteShippingFee: (ma) => api.delete(`/admin/shipping-fees/${ma}`),
  deletePromotion: (ma) => api.delete(`/admin/promotions/${ma}`),

  reviews: () => api.get('/admin/reviews'),
  deleteReview: (ma) => api.delete(`/admin/reviews/${ma}`),
  questions: () => api.get('/admin/questions'),
  answer: (ma, noiDung) => api.put(`/admin/questions/${ma}/answer`, { noiDung }),
  deleteQuestion: (ma) => api.delete(`/admin/questions/${ma}`),
  returns: () => api.get('/admin/returns'),
  updateReturn: (ma, trangThai) => api.put(`/admin/returns/${ma}/status`, null, { params: { trangThai } }),

  stats: (tu, den) => api.get('/admin/stats', { params: { tu: tu || undefined, den: den || undefined } }),
  exportStats: (tu, den) => api.get('/admin/stats/export', { params: { tu: tu || undefined, den: den || undefined }, responseType: 'blob' }),
  search: (q) => api.get('/admin/search', { params: { q } }),
};
