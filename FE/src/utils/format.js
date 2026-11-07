export const formatPrice = (n) => `${Number(n || 0).toLocaleString('vi-VN')} ₫`;

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
