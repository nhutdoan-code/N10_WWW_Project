export const PAYMENT_LABELS = {
  CHO_THANH_TOAN: 'Chờ thanh toán', DA_THANH_TOAN: 'Đã thanh toán',
  THAT_BAI: 'Thanh toán thất bại', CAN_HOAN_TIEN: 'Cần hoàn tiền',
  DANG_HOAN_TIEN: 'Đang hoàn tiền', DA_HOAN_TIEN: 'Đã hoàn tiền', DA_HUY: 'Đã hủy thanh toán',
};
export const PAYMENT_METHOD_LABELS = { COD: 'COD', CHUYEN_KHOAN: 'VNPay', VI_DIEN_TU: 'MoMo' };
export function canConfirmCodPayment(order) {
  const p = order?.thanhToan;
  return ['DANG_GIAO', 'CHO_KHACH_XAC_NHAN', 'DA_GIAO'].includes(order?.trangThai)
    && p?.phuongThuc === 'COD' && p?.trangThai === 'CHO_THANH_TOAN' && !p?.ngayThanhToan
    && p?.soTien != null && order?.tongTien != null
    && Number.isFinite(Number(p.soTien)) && Number(p.soTien) >= 0
    && Number(p.soTien) === Number(order.tongTien);
}
export function paymentView(order) {
  const p = order?.thanhToan;
  const method = p?.phuongThuc || order?.phuongThucTt;
  const status = p?.trangThai;
  const label = method === 'COD' && status === 'CHO_THANH_TOAN' ? 'Chưa thu tiền (COD)' :
    PAYMENT_LABELS[status] || 'Chưa có dữ liệu';
  const tone = status === 'DA_THANH_TOAN' ? 'paid' : status === 'DA_HOAN_TIEN' ? 'refunded' :
    ['THAT_BAI', 'DA_HUY'].includes(status) ? 'failed' : status ? 'pending' : 'unknown';
  return { method: PAYMENT_METHOD_LABELS[method] || 'Chưa rõ', label, tone,
    providerId: method === 'CHUYEN_KHOAN' ? p?.maGiaoDichNganHang : method === 'VI_DIEN_TU' ? p?.maGiaoDichMoMo : null,
    reference: p?.maGiaoDich, amount: p?.soTien, paidAt: p?.ngayThanhToan,
    mismatch: status === 'DA_THANH_TOAN' && p?.soTien != null && order?.tongTien != null && Number(p.soTien) !== Number(order.tongTien),
    canEdit: false };
}
