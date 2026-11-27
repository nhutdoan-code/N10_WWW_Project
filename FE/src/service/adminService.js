import api from '../config/api';

const save = (base) => (ma, data) => (ma ? api.put(`${base}/${ma}`, data) : api.post(base, data));

export const adminService = {
  products: (q) => api.get('/admin/products', { params: { q } }),
  product: (ma) => api.get(`/admin/products/${ma}`),
  productsPage: (params) => api.get('/admin/products/page', { params }),
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
  saveBrand: (ma, data) => (ma ? api.put(`/admin/brands/${ma}`, data) : api.post('/admin/brands', data)),
  brandImpact: (ma) => api.get(`/admin/brands/${ma}/impact`),
  archiveBrand: (ma) => api.put(`/admin/brands/${ma}/archive`),
  restoreBrand: (ma) => api.put(`/admin/brands/${ma}/restore`),

  orders: () => api.get('/admin/orders'),

  filterOrders: (params) =>
      api.get('/admin/orders/filter', { params }),

  updateOrderStatus: (ma, trangThai) => api.put(`/admin/orders/${ma}/status`, null, { params: { trangThai } }),
  cancelOrder: (ma, data) => api.put(`/admin/orders/${ma}/cancel`, data),
  markDelivered: (ma) => api.put(`/admin/orders/${ma}/mark-delivered`),
  updateOrderLine: (ma, maSanPham, soLuong) => api.put(`/admin/orders/${ma}/items/${maSanPham}`, { soLuong }),

  accounts: () => api.get('/admin/accounts'),
  accountsPage: (params) => api.get('/admin/accounts/page', { params }),
  createAccount: (data) => api.post('/admin/accounts', data),
  updateAccount: (ma, data) => api.put(`/admin/accounts/${ma}`, data),
  deleteAccount: (ma) => api.delete(`/admin/accounts/${ma}`),
  toggleLock: (ma) => api.put(`/admin/accounts/${ma}/toggle-lock`),

  inventory: (q, loc, page, size) => api.get('/admin/inventory', { params: { q: q || undefined, loc: loc || undefined, page, size } }),
  inventoryStats: () => api.get('/admin/inventory/stats'),
  inventoryHistory: (ma) => api.get(ma ? `/admin/inventory/${ma}/history` : '/admin/inventory/history'),
  importStock: (ma, soLuong, ghiChu) => api.post(`/admin/inventory/${ma}/import`, { soLuong, ghiChu }),
  importStockBatch: (items, ghiChu) => api.post('/admin/inventory/batch-import', { items, ghiChu }),
  // 2B.7D: gui kem so ton dang thay de backend tu choi (409) neu ton da doi trong luc kiem ke
  adjustStock: (ma, soLuongThucTe, lyDo, soLuongHienTai) => api.put(`/admin/inventory/${ma}/adjust`, { soLuongThucTe, lyDo, soLuongHienTai }),

  promotions: () => api.get('/admin/promotions'),
  promotionsPage: (params) => api.get('/admin/promotions/page', { params }),
  savePromotion: (ma, data) => (ma ? api.put(`/admin/promotions/${ma}`, data) : api.post('/admin/promotions', data)),
  deletePromotion: (ma) => api.delete(`/admin/promotions/${ma}`),
  promotionImpact: (ma) => api.get(`/admin/promotions/${ma}/impact`),
  archivePromotion: (ma) => api.put(`/admin/promotions/${ma}/archive`),
  restorePromotion: (ma) => api.put(`/admin/promotions/${ma}/restore`),

  shippingFees: () => api.get('/admin/shipping-fees'),
  saveShippingFee: (ma, data) => (ma ? api.put(`/admin/shipping-fees/${ma}`, data) : api.post('/admin/shipping-fees', data)),
  deleteShippingFee: (ma) => api.delete(`/admin/shipping-fees/${ma}`),

  reviews: () => api.get('/admin/reviews'),
  reviewsPage: (params) => api.get('/admin/reviews/page', { params }),
  deleteReview: (ma) => api.delete(`/admin/reviews/${ma}`),
  questions: () => api.get('/admin/questions'),
  questionsPage: (params) => api.get('/admin/questions/page', { params }),
  answer: (ma, noiDung) => api.put(`/admin/questions/${ma}/answer`, { noiDung }),
  deleteQuestion: (ma) => api.delete(`/admin/questions/${ma}`),
  returns: () => api.get('/admin/returns'),
  updateReturn: (ma, trangThai, ghiChuXuLy) => api.put(`/admin/returns/${ma}/status`, null, { params: { trangThai, ghiChuXuLy } }),
  restockReturn: (ma) => api.post(`/admin/returns/${ma}/restock`),
  recordReturnRefund: (ma, data) => api.post(`/admin/returns/${ma}/refund`, data),
  afterSales: () => api.get('/admin/after-sales'),
  updateAfterSale: (ma, payload) => api.put(`/admin/after-sales/${ma}/status`, payload),

  getInternalAfterSalesNote: (source, ma) => api.get(`/admin/${source === 'return' ? 'returns' : 'after-sales'}/${encodeURIComponent(ma)}/internal-note`),
  saveInternalAfterSalesNote: (source, ma, ghiChu) => api.put(`/admin/${source === 'return' ? 'returns' : 'after-sales'}/${encodeURIComponent(ma)}/internal-note`, { ghiChu }),

  stats: (tu, den) => api.get('/admin/stats', { params: { tu: tu || undefined, den: den || undefined } }),
  exportStats: (tu, den) => api.get('/admin/stats/export', { params: { tu: tu || undefined, den: den || undefined }, responseType: 'blob' }),
  search: (q) => api.get('/admin/search', { params: { q } }),

  auditLogs: (params) => api.get('/admin/audit-logs', { params }),
  auditLogDetail: (ma) => api.get(`/admin/audit-logs/${ma}`),

  // Phe duyet ban hang bien the (2B.7B/2B.7C) - chi ADMIN
  variants: (params) => api.get('/admin/variants', { params }),
  variantApproval: (ma) => api.get(`/admin/variants/${ma}/approval`),
  variantApprovalHistory: (ma) => api.get(`/admin/variants/${ma}/approval-history`),
  updateVariantApproval: (ma, body) => api.put(`/admin/variants/${ma}/approval`, body),
  unblockVariant: (ma, body) => api.put(`/admin/variants/${ma}/unblock`, body),
};
