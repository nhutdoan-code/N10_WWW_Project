// Hiển thị thời gian giao hàng dự kiến từ ngày (LocalDate "yyyy-MM-dd") backend trả về.
const THU = ['CN', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];

function parseNgay(s) {
  if (!s) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(s));
  if (!m) return null;
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
}

const dd = (n) => String(n).padStart(2, '0');
const nhan = (d) => `${THU[d.getDay()]}, ${dd(d.getDate())}/${dd(d.getMonth() + 1)}`;

/** "Thứ 5, 27/11 - Thứ 6, 28/11" hoặc một ngày nếu trùng; null nếu không có dữ liệu. */
export function formatKhoangNgayGiao(tu, den) {
  const a = parseNgay(tu);
  const b = parseNgay(den) || a;
  if (!a) return null;
  if (a.getTime() === b.getTime()) return nhan(a);
  return `${nhan(a)} - ${nhan(b)}`;
}

export const LOAI_DIA_BAN = {
  PHUONG: 'Phường (nội thành)',
  XA: 'Xã (ngoại thành)',
  DAC_KHU: 'Đặc khu / huyện đảo',
};
