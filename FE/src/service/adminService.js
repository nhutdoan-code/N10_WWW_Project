import api from '../config/api';

const save = (base) => (ma, data) => (ma ? api.put(`${base}/${ma}`, data) : api.post(base, data));

export const adminService = {
  products: (q) => api.get('/admin/products', { params: { q } }),
  uploadImage: (file) => {
    const body = new FormData();
    body.append('file', file);
    return api.post('/admin/uploads', body);
  },
  saveProduct: save('/admin/products'),
  deleteProduct: (ma) => api.delete(`/admin/products/${ma}`),
  productImpact: (ma) => api.get(`/admin/products/${ma}/impact`),
  archiveProduct: (ma) => api.put(`/admin/products/${ma}/archive`),
  restoreProduct: (ma) => api.put(`/admin/products/${ma}/restore`),
  reindexProducts: () => api.post('/admin/products/reindex'),

  saveCategory: save('/admin/categories'),
  deleteCategory: (ma) => api.delete(`/admin/categories/${ma}`),

  brands: () => api.get('/admin/brands'),
  brandImpact: (ma) => api.get(`/admin/brands/${ma}/impact`),
  archiveBrand: (ma) => api.put(`/admin/brands/${ma}/archive`),
  restoreBrand: (ma) => api.put(`/admin/brands/${ma}/restore`),

  orders: () => api.get('/admin/orders'),

  filterOrders: (params) =>
      api.get('/admin/orders/filter', { params }),

  updateOrderStatus: (ma, trangThai) => api.put(`/admin/orders/${ma}/status`, null, { params: { trangThai } }),
  markDelivered: (ma) => api.put(`/admin/orders/${ma}/mark-delivered`),
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
  deletePromotion: (ma) => api.delete(`/admin/promotions/${ma}`),
  promotionImpact: (ma) => api.get(`/admin/promotions/${ma}/impact`),
  archivePromotion: (ma) => api.put(`/admin/promotions/${ma}/archive`),
  restorePromotion: (ma) => api.put(`/admin/promotions/${ma}/restore`),

  shippingFees: () => api.get('/admin/shipping-fees'),
  saveShippingFee: (ma, data) => (ma ? api.put(`/admin/shipping-fees/${ma}`, data) : api.post('/admin/shipping-fees', data)),
  deleteShippingFee: (ma) => api.delete(`/admin/shipping-fees/${ma}`),

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

  auditLogs: (params) => api.get('/admin/audit-logs', { params }),
  auditLogDetail: (ma) => api.get(`/admin/audit-logs/${ma}`),
};
