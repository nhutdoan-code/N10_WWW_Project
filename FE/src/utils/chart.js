// Bảng màu biểu đồ lấy từ 3 màu thương hiệu (xanh rêu, vàng nghệ, xám than) ở các độ đậm khác nhau.
// Giữ nguyên tên khóa cũ để không ảnh hưởng nơi sử dụng.
export const COLORS = {
  blue: '#2f7a5a',
  orange: '#b8862a',
  aqua: '#5fa884',
  yellow: '#e2ac35',
  violet: '#6f7b75',
  red: '#c0392b',
  magenta: '#a3ada8',
  green: '#1f5641',
};

export const shortMoney = (n) => {
  const v = Number(n);
  if (v >= 1e9) return `${+(v / 1e9).toFixed(1)} tỷ`;
  if (v >= 1e6) return `${+(v / 1e6).toFixed(1)} tr`;
  if (v >= 1e3) return `${+(v / 1e3).toFixed(0)}k`;
  return String(v);
};

const key = (d) => d.toISOString().slice(0, 10);

export const fillDays = (rows) => {
  if (rows.length === 0) return [];
  const map = Object.fromEntries(rows.map((r) => [r.ngay, r]));
  const start = new Date(rows[0].ngay);
  const end = new Date(rows[rows.length - 1].ngay);
  const diffDays = Math.round((end - start) / (1000 * 60 * 60 * 24));
  // Only zero-fill if range is reasonably bounded (<= 62 days) to avoid generating huge arrays
  if (diffDays > 62 || diffDays < 0) {
    return rows;
  }
  const out = [];
  for (let d = new Date(start); key(d) <= rows[rows.length - 1].ngay; d.setUTCDate(d.getUTCDate() + 1)) {
    const k = key(d);
    out.push(map[k] || { ngay: k, doanhThu: 0, soDon: 0 });
  }
  return out;
};

export const groupMonths = (rows) => {
  const map = {};
  rows.forEach((r) => {
    const k = r.ngay.slice(0, 7);
    map[k] = { ngay: k, doanhThu: (map[k]?.doanhThu || 0) + Number(r.doanhThu), soDon: (map[k]?.soDon || 0) + r.soDon };
  });
  return Object.values(map);
};

export const niceMax = (max) => {
  if (max <= 0) return 1;
  const pow = 10 ** Math.floor(Math.log10(max));
  const f = max / pow;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * pow;
};

export const isoDaysAgo = (n) => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  const p = (x) => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};
