export const COLORS = {
  blue: '#2a78d6',
  orange: '#eb6834',
  aqua: '#1baf7a',
  yellow: '#eda100',
  violet: '#6250d6',
  red: '#e34948',
  magenta: '#e87ba4',
  green: '#008300',
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
  const out = [];
  for (let d = new Date(rows[0].ngay); key(d) <= rows[rows.length - 1].ngay; d.setUTCDate(d.getUTCDate() + 1)) {
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
