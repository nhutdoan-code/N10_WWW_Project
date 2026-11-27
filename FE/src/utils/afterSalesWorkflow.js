export const AFTER_SALES_STATUS_LABELS = {
  CHO_TIEP_NHAN: 'Chờ tiếp nhận',
  DANG_KIEM_TRA: 'Đang kiểm tra',
  DANG_XAC_MINH: 'Đang xác minh',
  DANG_XU_LY: 'Đang xử lý',
  HOAN_TAT: 'Hoàn tất',
  TU_CHOI: 'Từ chối',
};

export function afterSalesStatusLabel(status, loai) {
  if (status === 'DANG_XU_LY' && loai === 'BAO_HANH') return 'Đang sửa chữa';
  if (status === 'DANG_XU_LY' && loai === 'KHIEU_NAI') return 'Đang thực hiện hướng giải quyết';
  return AFTER_SALES_STATUS_LABELS[status] || status;
}

export function afterSalesActions(request) {
  if (!request || request.source !== 'after') return [];
  const reject = ['TU_CHOI', 'Từ chối'];
  if (request.trangThai === 'CHO_TIEP_NHAN') {
    return request.loai === 'BAO_HANH'
      ? [['DANG_KIEM_TRA', 'Bắt đầu kiểm tra bảo hành'], reject]
      : [['DANG_XAC_MINH', 'Bắt đầu xác minh khiếu nại'], reject];
  }
  if (request.trangThai === 'DANG_KIEM_TRA') {
    return [['DANG_XU_LY', 'Chuyển sang sửa chữa'], reject];
  }
  if (request.trangThai === 'DANG_XAC_MINH') {
    return [['DANG_XU_LY', 'Thực hiện hướng giải quyết'], reject];
  }
  if (request.trangThai === 'DANG_XU_LY') {
    return [['HOAN_TAT', 'Xác nhận hoàn tất'], reject];
  }
  return [];
}
