import api from '../config/api';

export const orderService = {
  checkout: (data, idempotencyKey) => api.post('/customer/orders', data, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
  }),
  startPrepaidCheckout: (data, selectedVariants, idempotencyKey) => api.post(
    '/customer/checkout/payment',
    data,
    {
      params: selectedVariants?.length ? { chon: selectedVariants } : {},
      paramsSerializer: { indexes: null },
      timeout: 30000,
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    }
  ),
  checkoutPaymentStatus: (sessionId) => api.get(`/customer/checkout/payment/${sessionId}`),
  createMomoPayment: (ma) => api.post(`/customer/orders/${ma}/momo`),
  createVnPayPayment: (ma) => api.post(`/customer/orders/${ma}/vnpay`),
  paymentStatus: (ma) => api.get(`/customer/orders/${ma}/payment-status`),
  list: () => api.get('/customer/orders'),
  get: (ma) => api.get(`/customer/orders/${ma}`),
  cancel: (ma, lyDo, ghiChu) => api.put(`/customer/orders/${ma}/cancel`, { lyDo, ghiChu }),
  confirmReceived: (ma) => api.put(`/customer/orders/${ma}/confirm-received`),
  reorder: (ma) => api.post(`/customer/orders/${ma}/reorder`),
  // phuongThucTt: để backend tính phụ phí COD cho đơn giá trị lớn.
  preview: (maDiaChi, maSanPham, soLuong, phuongThucTt) => api.get('/customer/checkout/preview', {
    params: { maDiaChi, maSanPham, soLuong, phuongThucTt },
  }),
  // Thanh toán các sản phẩm được chọn trong giỏ (chon = danh sách mã phiên bản).
  previewSelected: (maDiaChi, chon, phuongThucTt) => api.get('/customer/checkout/preview-selected', {
    params: { maDiaChi, chon, phuongThucTt },
    paramsSerializer: { indexes: null },
  }),
  checkoutSelected: (data, chon, idempotencyKey) => api.post('/customer/orders/selected', data, {
    params: { chon },
    paramsSerializer: { indexes: null },
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
  }),
};
