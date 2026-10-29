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
