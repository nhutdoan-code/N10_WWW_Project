import { isVariantApproved, isVariantPendingReview } from './discontinuedProductRules.js';

export const APPROVAL_LABEL = {
  APPROVED: 'Đã phê duyệt',
  PENDING_REVIEW: 'Chờ phê duyệt',
  BLOCKED: 'Đang khóa bán',
};

export const LY_DO_KHOA = {
  GIA_SEED_CHUA_XAC_MINH: 'Giá seed chưa xác minh',
  SAI_CAU_HINH: 'Sai cấu hình',
  QUYET_DINH_QUAN_TRI: 'Quyết định quản trị',
  NGUNG_KINH_DOANH: 'Ngừng kinh doanh',
  KHAC: 'Lý do khác',
};

/** Nhãn trạng thái (Admin): APPROVED nhưng dấu vân tay lệch KHÔNG được hiển thị như sẵn sàng bán. */
export function approvalView(v) {
  if (v?.trangThaiPheDuyet === 'APPROVED' && (v.canhBaoPheDuyetLech || v.duocPhepBan !== true)) {
    return { cls: 'approval-warn', label: 'Đã duyệt · dữ liệu đã thay đổi', sanSang: false };
  }
  return {
    cls: `badge-${v?.trangThaiPheDuyet}`,
    label: APPROVAL_LABEL[v?.trangThaiPheDuyet] || String(v?.trangThaiPheDuyet ?? 'Không rõ'),
    sanSang: v?.trangThaiPheDuyet === 'APPROVED' && v.duocPhepBan === true,
  };
}

/**
 * Giá/tồn hiển thị trên thẻ sản phẩm (khách hàng): chỉ tính biến thể đã duyệt hợp lệ.
 * Giá seed của biến thể chưa duyệt không bao giờ được hiển thị như giá bán.
 */
export function cardPricing(sp) {
  const isNgungKD = sp?.trangThai === 'NGUNG_KINH_DOANH';
  const active = (sp?.phienBans || []).filter((p) => p.trangThai === 'DANG_KINH_DOANH');
  const sellable = active.filter((p) => isVariantApproved(p));
  const noneSellable = active.length > 0 && sellable.length === 0;
  const allBlocked = noneSellable && active.every((p) => p.trangThaiPheDuyet === 'BLOCKED');
  const allPending = noneSellable && !allBlocked && active.some((p) => isVariantPendingReview(p));
  const tonBan = sellable.reduce((s, p) => s + Number(p.soLuongTon || 0), 0);
  const hetHang = (isNgungKD || (sellable.length > 0 && tonBan <= 0)) && !allPending && !allBlocked;
  const gias = (isNgungKD ? active : sellable).filter((p) => p.giaBan != null).map((p) => Number(p.giaBan));
  return {
    isNgungKD,
    allPending,
    allBlocked,
    hetHang,
    tonBan,
    nhieuGia: new Set(gias).size > 1,
    gia: gias.length ? Math.min(...gias) : null,
  };
}
