export const PRODUCT_PLACEHOLDER = '/product-placeholder.svg';

const ABSOLUTE_URL = /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i;

/**
 * Tra ve URL anh san pham dung de dua vao <img src>.
 * - null/empty -> placeholder
 * - URL tuyet doi (http, https, data, blob, protocol-relative) -> giu nguyen,
 *   khong prepend them lan nua.
 * - duong dan tuong doi -> chuan hoa ve mot dau "/" va bo cac doan "uploads"
 *   bi lap (vi du /uploads//uploads/a.jpg).
 */
export function getProductImageUrl(value) {
  if (typeof value !== 'string') return PRODUCT_PLACEHOLDER;

  let url = value.trim();
  if (!url || url === 'undefined' || url === 'null') return PRODUCT_PLACEHOLDER;
  if (ABSOLUTE_URL.test(url)) return url;

  url = url.replace(/\\/g, '/').replace(/\/{2,}/g, '/');
  url = url.replace(/^(?:\/?uploads\/)+/i, '/uploads/');

  if (!url.startsWith('/')) url = `/${url}`;
  return url;
}


const chuanMau = (v) => (v || '').trim().toLowerCase();

/** Danh sách ảnh áp dụng cho một phiên bản: theo phiên bản > theo màu > ảnh chung > ảnh chính. */
export function danhSachAnhTheoPhienBan(sp, phienBan) {
  const ds = sp?.hinhAnhs || [];
  if (!ds.length) return [];

  const ma = phienBan?.maPhienBan;
  if (ma) {
    const theoPb = ds.filter((a) => a.maPhienBan === ma);
    if (theoPb.length) return theoPb;
  }

  const m = chuanMau(phienBan?.mauSac);
  if (m) {
    const theoMau = ds.filter((a) => !a.maPhienBan && chuanMau(a.mauSac) === m);
    if (theoMau.length) return theoMau;
  }

  const chung = ds.filter((a) => !a.maPhienBan && !chuanMau(a.mauSac));
  if (chung.length) return chung;

  const chinh = ds.filter((a) => a.duongDan === sp.anhChinh);
  return chinh.length ? chinh : [ds[0]];
}

/** Ảnh đại diện của một phiên bản (giỏ hàng, thanh toán...). */
export function anhTheoPhienBan(sp, phienBan) {
  const ds = danhSachAnhTheoPhienBan(sp, phienBan);
  return (ds.find((a) => a.duongDan === sp?.anhChinh) || ds[0])?.duongDan || sp?.anhChinh;
}
