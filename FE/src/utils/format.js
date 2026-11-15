export const formatPrice = (n) => `${Number(n || 0).toLocaleString('vi-VN')} ₫`;

/**
 * 2B.7D: gia san pham CONG BO. Backend khong tra truong gia cho bien the chua duoc phep ban
 * -> hien "Gia dang cap nhat", KHONG bao gio hien "0 ₫".
 */
export const formatGiaCongBo = (n) => (n === null || n === undefined || n === '' ? 'Giá đang cập nhật' : formatPrice(n));

/** Dung lượng bộ nhớ (GB) dạng gọn: 1024 -> "1TB", 256 -> "256GB". */
export const formatDungLuong = (gb) => {
  const n = Number(gb);
  if (!Number.isFinite(n) || n <= 0) return '';
  return n >= 1024 && n % 1024 === 0 ? `${n / 1024}TB` : `${n}GB`;
};

export const formatNumber = (n) => Number(n || 0).toLocaleString('vi-VN');

export const formatDate = (d) => (d ? new Date(d).toLocaleString('vi-VN') : '');

export const errorMessage = (e) =>
  e.response?.data?.message || (e.response ? 'Có lỗi xảy ra, vui lòng thử lại' : 'Không thể kết nối đến máy chủ, vui lòng kiểm tra đường truyền mạng!');

export const formatDay = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '');

export function downloadFile(data, name) {
  const url = URL.createObjectURL(new Blob([data], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}
