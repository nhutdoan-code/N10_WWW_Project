export const RETURN_ACTIONS = {
  CHO_DUYET: [['DA_DUYET', 'Duyệt'], ['TU_CHOI', 'Từ chối']],
  DA_DUYET: [['DANG_XU_LY', 'Bắt đầu xử lý'], ['HOAN_TAT', 'Hoàn tất']],
  DANG_XU_LY: [['HOAN_TAT', 'Hoàn tất']],
};
export const AFTER_ACTIONS = {
  CHO_TIEP_NHAN: [['DANG_XU_LY', 'Tiếp nhận'], ['TU_CHOI', 'Từ chối']],
  DANG_XU_LY: [['HOAN_TAT', 'Hoàn tất'], ['TU_CHOI', 'Từ chối']],
};
export const STATUS_LABELS = {
  CHO_DUYET: 'Chờ duyệt', CHO_TIEP_NHAN: 'Chờ tiếp nhận', DA_DUYET: 'Đã duyệt',
  DANG_XU_LY: 'Đang xử lý', HOAN_TAT: 'Hoàn tất', DA_HOAN_TIEN: 'Đã hoàn tiền', TU_CHOI: 'Từ chối',
};
export const GROUPS = {
  waiting: ['CHO_DUYET', 'CHO_TIEP_NHAN'],
  processing: ['DA_DUYET', 'DANG_XU_LY'],
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
      (!filters.status || GROUPS[filters.status]?.includes(r.trangThai)) &&
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
