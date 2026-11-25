// Màu hiển thị gần đúng cho chấm màu khi phiên bản chưa có ảnh theo màu.
const BANG_MAU = [
  [/titan sa mạc|sa mạc|desert/, '#c8a98a'],
  [/titan tự nhiên|tự nhiên|natural/, '#b9b1a5'],
  [/titan/, '#8e8d89'],
  [/đen|black|graphite|than/, '#1f1f22'],
  [/trắng|white|kem|cream/, '#f4f2ec'],
  [/bạc|silver/, '#c9ccd1'],
  [/xám|gray|grey/, '#7d8186'],
  [/vàng hồng|rose gold/, '#e3b7a0'],
  [/vàng|gold|yellow/, '#e6c35c'],
  [/cam|orange/, '#f08a3c'],
  [/hồng|pink/, '#f2a7bf'],
  [/đỏ|red/, '#d33a3a'],
  [/tím|purple|lavender/, '#9a7fc9'],
  [/xanh lá|xanh mint|mint|green/, '#5fae7d'],
  [/xanh navy|navy/, '#26345c'],
  [/xanh dương|xanh biển|blue/, '#3f74c9'],
  [/xanh/, '#4f8fb8'],
  [/nâu|brown/, '#8a5a3c'],
];

export function mauHex(ten) {
  const t = String(ten || '').toLowerCase();
  const m = BANG_MAU.find(([re]) => re.test(t));
  return m ? m[1] : '#d0d0d0';
}
