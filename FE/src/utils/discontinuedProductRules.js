/**
 * Business rules & UI logic for discontinued products (NGUNG_KINH_DOANH)
 * and sales approval controls (APPROVED, PENDING_REVIEW, BLOCKED).
 * Supports Phase 2B.3 & Phase 2B.6D.
 */

export function isProductDiscontinued(sp) {
  return Boolean(sp && sp.trangThai === 'NGUNG_KINH_DOANH');
}

export function isVariantDiscontinued(pv) {
  return Boolean(pv && pv.trangThai === 'NGUNG_KINH_DOANH');
}

/**
 * Fail-closed (Giai đoạn 2B.7B): chỉ coi là "đã duyệt, được bán" khi backend trả
 * trangThaiPheDuyet === 'APPROVED' VÀ duocPhepBan === true. Thiếu trường hoặc giá trị
 * lạ -> KHÔNG cho mua. Backend vẫn là lớp kiểm soát quyết định.
 */
export function isVariantApproved(pv) {
  if (!pv) return false;
  return pv.trangThaiPheDuyet === 'APPROVED' && pv.duocPhepBan === true;
}

export function isVariantBlocked(pv) {
  if (!pv) return false;
  return pv.trangThaiPheDuyet === 'BLOCKED';
}

/** Mọi trạng thái không phải "đã duyệt hợp lệ" và không bị khóa đều coi là chờ duyệt. */
export function isVariantPendingReview(pv) {
  if (!pv) return false;
  return !isVariantApproved(pv) && !isVariantBlocked(pv);
}

export function isItemDiscontinued(item) {
  if (!item) return false;
  return isProductDiscontinued(item.sanPham) || isVariantDiscontinued(item.phienBan);
}

export function isItemPendingReview(item) {
  return Boolean(item && isVariantPendingReview(item.phienBan));
}

export function isItemBlocked(item) {
  return Boolean(item && isVariantBlocked(item.phienBan));
}

export function isItemUnapproved(item) {
  return isItemPendingReview(item) || isItemBlocked(item);
}

export function hasDiscontinuedItems(items) {
  if (!Array.isArray(items)) return false;
  return items.some((item) => isItemDiscontinued(item));
}

export function hasUnapprovedItems(items) {
  if (!Array.isArray(items)) return false;
  return items.some((item) => isItemUnapproved(item));
}

export function hasUnpurchasableItems(items) {
  if (!Array.isArray(items)) return false;
  return items.some((item) => isItemDiscontinued(item) || isItemUnapproved(item));
}

export function isPurchasable(sp, pv) {
  if (!sp || !pv) return false;
  if (isProductDiscontinued(sp)) return false;
  if (isVariantDiscontinued(pv)) return false;
  if (pv.trangThai !== 'DANG_KINH_DOANH') return false;
  if (!isVariantApproved(pv)) return false;
  return Number(pv.soLuongTon) > 0;
}

export function getDefaultSelectedVariant(sp) {
  const pbs = sp?.phienBans;
  if (!Array.isArray(pbs) || pbs.length === 0) return null;

  const activeVariants = pbs.filter((p) => p.trangThai === 'DANG_KINH_DOANH');
  if (activeVariants.length === 0) {
    return pbs[0];
  }

  // Ưu tiên variant đã duyệt (APPROVED) và còn hàng trong kho
  const approvedInStock = activeVariants.find(
    (p) => isVariantApproved(p) && Number(p.soLuongTon) > 0
  );
  if (approvedInStock) return approvedInStock;

  // Tiếp theo ưu tiên variant đã duyệt
  const approved = activeVariants.find((p) => isVariantApproved(p));
  if (approved) return approved;

  // Nếu không có variant đã duyệt, chọn variant còn hàng đầu tiên
  const inStock = activeVariants.find((p) => Number(p.soLuongTon) > 0);
  return inStock || activeVariants[0];
}

export function canProceedToCheckout(items, previewError) {
  if (!Array.isArray(items) || items.length === 0) return false;
  if (hasDiscontinuedItems(items)) return false;
  if (hasUnapprovedItems(items)) return false;
  if (previewError) return false;
  return true;
}

export const DISCONTINUED_MESSAGES = {
  DETAIL_BANNER: 'Sản phẩm đã ngừng kinh doanh. Bạn có thể tham khảo các dòng sản phẩm mới hơn.',
  PRICE_NOTE: '(Giá tham khảo - Ngừng bán)',
  STATUS_BADGE: 'NGUNG_KINH_DOANH',
  CART_BANNER: 'Giỏ hàng có sản phẩm không còn kinh doanh. Vui lòng xóa trước khi thanh toán.',
  CART_ITEM_WARNING: 'Sản phẩm này đã ngừng kinh doanh và không thể thanh toán. Vui lòng xóa khỏi giỏ hàng.',
  CHECKOUT_BANNER: 'Đơn hàng của bạn chứa sản phẩm đã ngừng kinh doanh. Vui lòng quay lại giỏ hàng để cập nhật.',
  CHECKOUT_ACTION_DISABLED: 'Cần xóa sản phẩm ngừng kinh doanh hoặc chưa duyệt bán để tiếp tục',
  APPROVAL_CART_BANNER: 'Giỏ hàng có sản phẩm đang tạm ngừng nhận đơn hoặc bị khóa bán. Vui lòng xóa trước khi thanh toán.',
  APPROVAL_CART_ITEM_WARNING: 'Sản phẩm này đang tạm ngừng nhận đơn hoặc bị khóa bán. Vui lòng xóa khỏi giỏ hàng.',
  APPROVAL_CHECKOUT_BANNER: 'Đơn hàng của bạn chứa sản phẩm đang tạm ngừng nhận đơn hoặc bị khóa bán. Vui lòng quay lại giỏ hàng để cập nhật.',
  APPROVAL_PENDING_TOOLTIP: 'Sản phẩm đang chờ duyệt, tạm ngừng nhận đơn',
  APPROVAL_PRICE_NOTE_PENDING: 'Giá đang cập nhật',
  APPROVAL_PRICE_NOTE_BLOCKED: 'Không khả dụng',
  APPROVAL_BADGE_PENDING: 'Tạm ngừng nhận đơn',
  APPROVAL_BADGE_BLOCKED: 'Bị khóa bán',
};
