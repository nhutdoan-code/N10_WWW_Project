import api from '../config/api';

export const orderService = {
  checkout: (data) => api.post('/customer/orders', data),
  list: () => api.get('/customer/orders'),
  get: (ma) => api.get(`/customer/orders/${ma}`),
  cancel: (ma, lyDo, ghiChu) => api.put(`/customer/orders/${ma}/cancel`, { lyDo, ghiChu }),
  reorder: (ma) => api.post(`/customer/orders/${ma}/reorder`),
  promotion: (ma) => api.get(`/customer/promotions/${ma}`),
};
