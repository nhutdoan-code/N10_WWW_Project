import { formatDate } from './format.js';
import { TYPES } from './afterSalesForm.js';
export function receiptText(request) {
  return ['PHONENEST — PHIẾU TIẾP NHẬN YÊU CẦU', '',
    `Mã yêu cầu: ${request.maYeuCau || ''}`,
    `Loại yêu cầu: ${TYPES[request.loai || request.loaiYeuCau] || 'Trả hàng / hoàn tiền'}`,
    `Đơn hàng: ${request.maDonHang || 'Không gắn đơn hàng'}`,
    `Sản phẩm: ${request.tenSanPham || 'Không chỉ định'}`,
    `Ngày gửi: ${formatDate(request.ngayTao) || 'Không rõ'}`, '',
    'Phiếu xác nhận đã gửi yêu cầu, không phải xác nhận đã duyệt, nhận máy hoặc hoàn tiền.',
    'Vui lòng mang theo mã yêu cầu khi đến cửa hàng. Hotline: 1800 6868.'].join('\n');
}
export function downloadReceipt(request) {
  const url = URL.createObjectURL(new Blob(['\ufeff', receiptText(request)], {type:'text/plain;charset=utf-8'}));
  const a = document.createElement('a'); a.href = url; a.download = `PhoneNest-${String(request.maYeuCau).replace(/[^a-zA-Z0-9_-]/g,'')}.txt`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function printReceipt(request) {
  const win = window.open('', '_blank');
  if (!win) return false;
  win.opener = null;
  win.document.title = 'Phiếu tiếp nhận PhoneNest';
  const pre = win.document.createElement('pre'); pre.textContent = receiptText(request);
  pre.style.cssText = 'white-space:pre-wrap;overflow-wrap:anywhere;font:16px/1.7 Arial,sans-serif;padding:24px;color:#111';
  win.document.body.appendChild(pre); win.focus(); win.print(); return true;
}
