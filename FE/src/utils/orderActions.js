// Các hành động được phép hiển thị theo vòng đời đơn hàng.
// Backend vẫn phải xác thực trạng thái trước khi thực hiện hành động.
const REORDER_STATES = new Set(['DA_GIAO', 'DA_HUY']);
const COMPLAINT_STATES = new Set(['DANG_GIAO', 'CHO_KHACH_XAC_NHAN', 'DA_GIAO', 'DA_HUY']);
const CANCEL_STATES = new Set(['CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DANG_CHUAN_BI']);

export function getOrderActions(status) {
  return {
    reorder: REORDER_STATES.has(status),
    afterDelivery: status === 'DA_GIAO',
    complain: COMPLAINT_STATES.has(status),
    cancel: CANCEL_STATES.has(status),
    confirmReceived: status === 'CHO_KHACH_XAC_NHAN',
  };
}
