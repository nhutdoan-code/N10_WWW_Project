export const RETURN_ACTIONS = {
  CHO_DUYET: [['DA_DUYET', 'Duyệt'], ['TU_CHOI', 'Từ chối']],
  DA_DUYET: [['DANG_XU_LY', 'Bắt đầu xử lý']],
  DANG_XU_LY: [['HOAN_TAT', 'Hoàn tất']],
};
export const STATUS_LABELS = {
  CHO_DUYET: 'Chờ tiếp nhận', CHO_TIEP_NHAN: 'Chờ tiếp nhận', DA_DUYET: 'Đã duyệt',
  DANG_KIEM_TRA: 'Đang kiểm tra', DANG_XAC_MINH: 'Đang xác minh',
  DANG_XU_LY: 'Đang xử lý', HOAN_TAT: 'Hoàn tất', DA_HOAN_TIEN: 'Đã hoàn tiền', TU_CHOI: 'Từ chối',
};
export const AFTER_ACTIONS = {
  CHO_TIEP_NHAN: [['DANG_XU_LY', 'Tiếp nhận'], ['TU_CHOI', 'Từ chối']],
  DANG_XU_LY: [['HOAN_TAT', 'Hoàn tất'], ['TU_CHOI', 'Từ chối']],
};
export const GROUPS = {
  waiting: ['CHO_DUYET', 'CHO_TIEP_NHAN'],
  processing: ['DA_DUYET', 'DANG_KIEM_TRA', 'DANG_XAC_MINH', 'DANG_XU_LY'],
  open: ['CHO_DUYET', 'CHO_TIEP_NHAN', 'DA_DUYET', 'DANG_KIEM_TRA', 'DANG_XAC_MINH', 'DANG_XU_LY'],
  approved: ['DA_DUYET'],
  completed: ['HOAN_TAT', 'DA_HOAN_TIEN'],
  rejected: ['TU_CHOI'],
};
const priority = (s) => GROUPS.waiting.includes(s) ? 0 : GROUPS.processing.includes(s) ? 1 : GROUPS.rejected.includes(s) ? 2 : 3;
export function normalizeRequests(data) {
  return [
    ...(data?.returns || []).map(r => ({ ...r, source: 'return', loai: r.loaiYeuCau || 'TRA_HANG_HOAN_TIEN' })),
    ...(data?.afterSales || []).map(r => ({ ...r, source: 'after', loai: r.loai })),
  ];
}
export function filterRequests(rows, filters = {}) {
  const query = (filters.query || '').trim().toLocaleLowerCase('vi-VN');
  const start = filters.from || '';
  const end = filters.to || '';
  return rows.filter(r => {
    const haystack = [r.maYeuCau, r.maDonHang, r.tenKhach, r.soDienThoai, r.tenSanPham].filter(Boolean).join(' ').toLocaleLowerCase('vi-VN');
    const day = String(r.ngayTao || '').slice(0,10);
    return (!query || haystack.includes(query)) &&
      (!filters.source || r.source === filters.source) &&
      (!filters.status || (filters.status === 'overdue' ? isOverdue(r) : filters.status === 'today' ? completedToday(r) : GROUPS[filters.status]?.includes(r.trangThai))) &&
      (!filters.type || r.loai === filters.type) &&
      (!start || day >= start) && (!end || day <= end);
  }).sort((a,b) => {
    const sort = filters.sort || 'priority';
    let compare = 0;
    if (sort === 'priority') compare = priority(a.trangThai) - priority(b.trangThai);
    if (sort.startsWith('date')) compare = new Date(a.ngayTao || 0).getTime() - new Date(b.ngayTao || 0).getTime();
    if (sort.startsWith('status')) compare = priority(a.trangThai) - priority(b.trangThai);
    if (sort === 'dateDesc' || sort === 'statusDesc') compare *= -1;
    return compare || new Date(b.ngayTao || 0).getTime() - new Date(a.ngayTao || 0).getTime();
  });
}
export const countPending = (rows) => rows.filter(r => GROUPS.waiting.includes(r.trangThai)).length;
export const formatAdminDate = d => d ? new Intl.DateTimeFormat('vi-VN', {day:'2-digit', month:'2-digit', year:'numeric', hour:'2-digit', minute:'2-digit', hour12:false}).format(new Date(d)) : '—';
export const TYPE_LABELS = { DOI_HANG: 'Đổi hàng', TRA_HANG_HOAN_TIEN: 'Trả hàng / hoàn tiền', BAO_HANH: 'Bảo hành', KHIEU_NAI: 'Khiếu nại' };

export const SLA_HOURS = 48;
export function isOverdue(row, now = Date.now()) {
  if (!GROUPS.waiting.includes(row.trangThai) || row.ghiChuXuLy?.trim() || !row.ngayTao) return false;
  const date = String(row.ngayTaoChinhXac || row.ngayTao);
  // Legacy returns only expose a date: use end-of-day, never flag early.
  const start = new Date(date.length === 10 ? `${date}T23:59:59` : date).getTime();
  return Number.isFinite(start) && now - start >= SLA_HOURS * 3600000;
}
export function completedToday(row, now = new Date()) {
  const day = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;
  return GROUPS.completed.includes(row.trangThai) && String(row.ngayCapNhat || '').slice(0,10) === day;
}
export function parseRequestText(text) {
  const fields = {}, raw = String(text || '');
  const marker = /(?:^|\n)Mô tả chi tiết: /;
  const index = raw.search(marker);
  if (index < 0) return { fields, description: raw };
  const prefix = raw.slice(0,index);
  const description = raw.slice(index).replace(/^\n?Mô tả chi tiết: /,'');
  const keys = new Set(['Lý do','Tình trạng máy','Cam kết khách hàng','Điểm khác mô tả','Loại lỗi','Phiên bản muốn đổi','Hoàn tiền đề nghị','Ngân hàng','Số tài khoản','Chủ tài khoản','Chủ đề','IMEI','Serial','Ngoại hình']);
  for (const line of prefix.split('\n')) {
    const i = line.indexOf(': ');
    if (i > 0 && keys.has(line.slice(0,i))) fields[line.slice(0,i)] = line.slice(i+2);
  }
  return {fields, description};
}
export function adminRefundMethod(payment, fields = {}) {
  if (payment === 'COD') return 'CHUYEN_KHOAN';
  if (payment === 'CHUYEN_KHOAN') return 'VNPAY';
  if (payment === 'VI_DIEN_TU') return fields['Hoàn tiền đề nghị'] === 'Chuyển khoản' ? 'CHUYEN_KHOAN' : 'MOMO';
  return null;
}
export function canComplete(row, steps = []) {
  if (row.trangThai !== 'DANG_XU_LY') return false;
  if (row.source === 'return') return !!row.daNhapKho && (row.loai === 'DOI_HANG' ? steps.includes('EXCHANGE_HANDOVER') : !!row.ngayHoanTien);
  return row.loai !== 'BAO_HANH' || steps.includes('WARRANTY_HANDOVER');
}
export const REJECT_REASONS = ['Quá hạn chính sách','Không đủ điều kiện','Minh chứng không hợp lệ','Sản phẩm không khớp','Khác'];
export const money = value => `${Number(value || 0).toLocaleString('vi-VN')} ₫`;
