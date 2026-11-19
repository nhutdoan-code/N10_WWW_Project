// Dữ liệu cho menu "Tất cả điện thoại": tính từ danh sách sản phẩm công khai và số liệu hành vi.
// Hàm thuần (không gọi API) để dễ kiểm thử.

const DANG_BAN = 'DANG_KINH_DOANH';

// Phiên bản đang mở bán: được phép bán (đã duyệt, giá công bố) và đang kinh doanh.
export const variantOnSale = (v) => Boolean(v && v.duocPhepBan && v.giaBan != null && (!v.trangThai || v.trangThai === DANG_BAN));

// Sản phẩm đang mở bán: còn ít nhất một phiên bản đang mở bán và sản phẩm không ngừng kinh doanh.
export const productOnSale = (p) => {
  if (!p || p.trangThai === 'NGUNG_KINH_DOANH') return false;
  const pbs = Array.isArray(p.phienBans) ? p.phienBans : [];
  if (pbs.length === 0) return p.giaBan != null;
  return pbs.some(variantOnSale);
};

// Thương hiệu có sản phẩm đang mở bán, sắp xếp theo số sản phẩm giảm dần rồi theo tên.
export const brandsOnSale = (products = []) => {
  const map = new Map();
  products.filter(productOnSale).forEach((p) => {
    if (!p.maThuongHieu) return;
    const cur = map.get(p.maThuongHieu) || { maThuongHieu: p.maThuongHieu, tenThuongHieu: p.tenThuongHieu || p.maThuongHieu, soSanPham: 0 };
    cur.soSanPham += 1;
    map.set(p.maThuongHieu, cur);
  });
  return [...map.values()].sort((a, b) => b.soSanPham - a.soSanPham || a.tenThuongHieu.localeCompare(b.tenThuongHieu, 'vi'));
};

// Mức giảm lớn nhất của sản phẩm (giá niêm yết so với giá bán) trên các phiên bản đang mở bán còn hàng.
export const bestDiscountOf = (p) => {
  let best = null;
  (p.phienBans || []).forEach((v) => {
    if (!variantOnSale(v) || !(Number(v.soLuongTon) > 0)) return;
    const ban = Number(v.giaBan);
    const goc = Number(v.giaNiemYet);
    if (!(goc > ban) || !(ban > 0)) return;
    const phanTram = (goc - ban) / goc;
    if (!best || phanTram > best.phanTram) best = { phanTram, giaBan: ban, giaNiemYet: goc, chenhLech: goc - ban };
  });
  return best;
};

// Sản phẩm giá tốt: đang mở bán, xếp theo % chênh lệch giữa giá niêm yết và giá bán (lớn trước).
export const bestDeals = (products = [], n = 6) => products
  .filter(productOnSale)
  .map((p) => ({ product: p, deal: bestDiscountOf(p) }))
  .filter((x) => x.deal)
  .sort((a, b) => b.deal.phanTram - a.deal.phanTram || b.deal.chenhLech - a.deal.chenhLech)
  .slice(0, n);

const chuanHoa = (s) => (s || '').toString().toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
const vietHoa = (s) => s.replace(/(^|\s)(\p{L})/gu, (m, sp, c) => sp + c.toUpperCase());

// Dòng sản phẩm được tìm nhiều: từ khóa tìm kiếm phổ biến (chỉ giữ từ khóa khớp ít nhất một sản phẩm
// đang mở bán), bổ sung bằng sản phẩm được xem nhiều nếu chưa đủ.
export const popularLines = (popular, products = [], n = 6) => {
  const dangBan = products.filter(productOnSale);
  const out = [];
  const seen = new Set();
  (popular?.tuKhoa || []).forEach((k) => {
    if (out.length >= n) return;
    const tk = chuanHoa(k.tuKhoa);
    if (!tk || seen.has(tk)) return;
    const khop = dangBan.filter((p) => chuanHoa(`${p.tenThuongHieu || ''} ${p.tenMay}`).includes(tk) || chuanHoa(p.tenMay).includes(tk));
    if (khop.length === 0) return;
    seen.add(tk);
    // Lấy đúng cách viết hoa từ tên sản phẩm khớp (vd. "iphone 16" -> "iPhone 16").
    const goc = khop.map((p) => `${p.tenThuongHieu || ''} ${p.tenMay}`).find((t) => t.toLowerCase().includes(tk));
    const viTri = goc ? goc.toLowerCase().indexOf(tk) : -1;
    const label = viTri >= 0 ? goc.slice(viTri, viTri + tk.length) : vietHoa(tk);
    out.push({ key: `q-${tk}`, label, to: `/?q=${encodeURIComponent(k.tuKhoa.trim())}`, luot: k.luot, loai: 'search', soKetQua: khop.length });
  });
  const byId = new Map(dangBan.map((p) => [p.maSanPham, p]));
  (popular?.sanPhamXemNhieu || []).forEach((s) => {
    if (out.length >= n) return;
    const p = byId.get(s.maSanPham);
    if (!p || seen.has(chuanHoa(p.tenMay))) return;
    seen.add(chuanHoa(p.tenMay));
    out.push({ key: `p-${p.maSanPham}`, label: p.tenMay, to: `/products/${p.maSanPham}`, luot: s.luot, loai: 'view' });
  });
  return out;
};
