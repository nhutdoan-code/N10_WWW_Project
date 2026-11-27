export const WARRANTY_OPTIONS = ['Sửa chữa miễn phí', 'Thay linh kiện miễn phí', 'Đổi máy tương đương (không thể sửa chữa)'];
const date = value => { const d = value ? new Date(value) : null; return d && Number.isFinite(d.getTime()) ? d : null; };
// Server trả hạn từ AfterSalesPolicy.java; không tính lại 7 ngày / 12 tháng trên client.
export const warrantyEnd = order => date(order?.hanBaoHanh);
export const returnEnd = order => date(order?.hanDoiTra);
export function eligible(order, type, now = new Date()) {
  if (!type || !order) return false;
  if (type === 'KHIEU_NAI') return true;
  if (order.trangThai !== 'DA_GIAO') return false;
  const warranty = type === 'BAO_HANH';
  const start = date(order.ngayKhachXacNhan || order.ngayGiao);
  const end = warranty ? warrantyEnd(order) : returnEnd(order);
  const confirmed = date(order.ngayKhachXacNhan);
  return !!start && !!end && (!confirmed || now >= confirmed) && now >= start && now <= end;
}
export const warrantyActive = (order, now = new Date()) => eligible(order, 'BAO_HANH', now);
export const returnActive = (order, now = new Date()) => eligible(order, 'TRA_HANG_HOAN_TIEN', now);
export function remainingLabel(order, type, now = new Date()) {
  const end = type === 'BAO_HANH' ? warrantyEnd(order) : returnEnd(order);
  if (!end || type === 'KHIEU_NAI') return '';
  const days = Math.max(0, Math.ceil((end - now) / 86400000));
  if (now > end) return 'Đã quá hạn';
  return `Còn ${days} ngày ${type === 'BAO_HANH' ? 'BH' : 'đổi trả'}`;
}

export function deadlineTone(order, type, now = new Date()) {
  const end = type === 'BAO_HANH' ? warrantyEnd(order) : returnEnd(order);
  if (!end || type === 'KHIEU_NAI') return '';
  const days = Math.ceil((end-now)/86400000);
  return now > end || days <= 1 ? 'urgent' : days <= 2 ? 'soon' : '';
}
