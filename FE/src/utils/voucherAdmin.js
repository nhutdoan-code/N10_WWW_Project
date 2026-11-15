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
