/**
 * Trạng thái hiển thị của voucher trong trang Admin.
 * Cờ bật/tắt (trangThaiHoatDong) ưu tiên hơn khoảng ngày; hết lượt cũng coi như không còn dùng được.
 * @param k voucher (KhuyenMaiDto)
 * @param today chuỗi yyyy-MM-dd
 */
export function trangThaiVoucher(k, today) {
  if (k?.trangThaiHoatDong === false) return { label: 'Đã tắt', className: 'badge-NGUNG_KINH_DOANH' };
  if (today < k.ngayBatDau) return { label: 'Sắp diễn ra', className: 'badge-CHO_KHACH_XAC_NHAN' };
  if (today > k.ngayKetThuc) return { label: 'Hết hạn', className: 'badge-HET_HANG' };
  if (k.soLuong != null && Number(k.daDung) >= Number(k.soLuong)) return { label: 'Hết lượt', className: 'badge-SAP_HET' };
  return { label: 'Đang chạy', className: 'badge-CON_HANG' };
}

const vnd = (n) => `${Number(n || 0).toLocaleString('vi-VN')} ₫`;
const gon = (n) => {
  const x = Number(n || 0);
  if (x >= 1_000_000 && x % 100_000 === 0) return `${(x / 1_000_000).toLocaleString('vi-VN')}TR`;
  if (x >= 1_000 && x % 1_000 === 0) return `${x / 1_000}K`;
  return x.toLocaleString('vi-VN');
};

export const laTienMat = (v) => v?.kieuGiam === 'SO_TIEN';

/**
 * Nhãn mức giảm của voucher (M23).
 * @returns {{ ngan: string, chiTiet: string }} ngan: "-150K" / "-5%" / "Miễn phí"; chiTiet: câu mô tả đầy đủ.
 */
export function mucGiam(v) {
  if (!v) return { ngan: '', chiTiet: '' };
  const ship = v.loai === 'VAN_CHUYEN';
  const doiTuong = ship ? 'phí giao hàng' : 'đơn hàng';
  if (laTienMat(v)) {
    return { ngan: `-${gon(v.soTienGiamCoDinh)}`, chiTiet: `Giảm ${vnd(v.soTienGiamCoDinh)} ${doiTuong}` };
  }
  const pt = Number(v.phanTramGiam || 0);
  const tran = v.soTienGiamToiDa ? `, tối đa ${vnd(v.soTienGiamToiDa)}` : '';
  if (ship && pt === 100) return { ngan: 'Miễn phí', chiTiet: `Miễn phí giao hàng${tran}` };
  return { ngan: `-${pt.toLocaleString('vi-VN')}%`, chiTiet: `Giảm ${pt.toLocaleString('vi-VN')}% ${doiTuong}${tran}` };
}

/** Tiền giảm thực tế, giống KhuyenMaiService.tinhGiam (0 nếu chưa đạt đơn tối thiểu). */
export function tinhGiamVoucher(v, tongDon, coSo = tongDon) {
  const base = Math.max(0, Number(coSo) || 0);
  if (!v || Number(tongDon) < Number(v.donToiThieu || 0)) return 0;
  let giam;
  if (laTienMat(v)) {
    giam = Number(v.soTienGiamCoDinh || 0);
  } else {
    giam = Math.floor((base * Number(v.phanTramGiam || 0)) / 100);
    if (v.soTienGiamToiDa != null && v.soTienGiamToiDa !== '') giam = Math.min(giam, Number(v.soTienGiamToiDa));
  }
  return Math.max(0, Math.min(giam, base));
}

/**
 * Kiểm tra form voucher ở FE (BE vẫn kiểm tra lại). Trả về { loi: [], canhBao: [] }.
 */
export function kiemTraVoucher(v) {
  const loi = [];
  const canhBao = [];
  const toiThieu = Number(v.donToiThieu || 0);
  if (laTienMat(v)) {
    const st = Number(v.soTienGiamCoDinh);
    if (!(st > 0)) loi.push('Nhập số tiền giảm lớn hơn 0.');
    else if (v.loai !== 'VAN_CHUYEN' && st > toiThieu) loi.push('Đơn tối thiểu phải lớn hơn hoặc bằng số tiền giảm.');
    else if (v.loai !== 'VAN_CHUYEN' && toiThieu > 0 && st / toiThieu > 0.1) {
      canhBao.push(`Mức giảm bằng ${Math.round((st / toiThieu) * 100)}% đơn tối thiểu — cao hơn mức phổ biến (≤ 5%).`);
    }
  } else {
    const pt = Number(v.phanTramGiam);
    if (!(pt > 0 && pt <= 100)) loi.push('Phần trăm giảm phải trong khoảng (0, 100].');
    if (!(Number(v.soTienGiamToiDa) > 0)) loi.push('Voucher giảm % bắt buộc có mức giảm tối đa.');
    if (v.loai !== 'VAN_CHUYEN' && pt > 8) canhBao.push('Voucher điện thoại thường giảm 2–5% (tối đa 7–8% dịp siêu sale).');
    if (v.loai !== 'VAN_CHUYEN' && Number(v.soTienGiamToiDa) > 1_000_000) canhBao.push('Mức trần trên 1.000.000 ₫ chỉ nên dùng cho flagship trên 25 triệu.');
  }
  return { loi, canhBao };
}
