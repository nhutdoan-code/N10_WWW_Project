import test from 'node:test';
import assert from 'node:assert/strict';
import { bestDeals, brandsOnSale, popularLines, productOnSale } from '../src/utils/catalogInsights.js';

const pb = (o) => ({ duocPhepBan: true, trangThai: 'DANG_KINH_DOANH', giaBan: 100, giaNiemYet: 100, soLuongTon: 5, ...o });
const products = [
  { maSanPham: 'A', tenMay: 'iPhone 16', maThuongHieu: 'TH1', tenThuongHieu: 'Apple', phienBans: [pb({ giaBan: 80, giaNiemYet: 100 })] },
  { maSanPham: 'B', tenMay: 'Galaxy S25', maThuongHieu: 'TH2', tenThuongHieu: 'Samsung', phienBans: [pb({ giaBan: 50, giaNiemYet: 100 })] },
  { maSanPham: 'C', tenMay: 'Galaxy A07', maThuongHieu: 'TH2', tenThuongHieu: 'Samsung', phienBans: [pb({ duocPhepBan: false, giaBan: null })] },
  { maSanPham: 'D', tenMay: 'Nokia 3310', maThuongHieu: 'TH3', tenThuongHieu: 'Nokia', trangThai: 'NGUNG_KINH_DOANH', phienBans: [pb({ giaBan: 10, giaNiemYet: 100 })] },
  { maSanPham: 'E', tenMay: 'Redmi 13', maThuongHieu: 'TH4', tenThuongHieu: 'Xiaomi', phienBans: [pb({ giaBan: 10, giaNiemYet: 100, soLuongTon: 0 })] },
];

test('chỉ tính sản phẩm đang mở bán', () => {
  assert.equal(productOnSale(products[0]), true);
  assert.equal(productOnSale(products[2]), false);
  assert.equal(productOnSale(products[3]), false);
});

test('thương hiệu chỉ gồm hãng có sản phẩm đang mở bán', () => {
  const ten = brandsOnSale(products).map((b) => b.tenThuongHieu);
  assert.deepEqual(ten, ['Apple', 'Samsung', 'Xiaomi']);
});

test('giá tốt xếp theo mức chênh lệch so với giá niêm yết, bỏ sản phẩm ngừng bán và hết hàng', () => {
  const ds = bestDeals(products).map((x) => x.product.maSanPham);
  assert.deepEqual(ds, ['B', 'A']);
});

test('dòng được tìm nhiều bỏ từ khóa không khớp sản phẩm và bổ sung sản phẩm xem nhiều', () => {
  const popular = {
    tuKhoa: [{ tuKhoa: 'galaxy', luot: 9 }, { tuKhoa: 'asdfgh', luot: 5 }, { tuKhoa: 'iphone 16', luot: 3 }],
    sanPhamXemNhieu: [{ maSanPham: 'E', luot: 4 }, { maSanPham: 'D', luot: 3 }],
  };
  const ds = popularLines(popular, products);
  assert.deepEqual(ds.map((x) => x.label), ['Galaxy', 'iPhone 16', 'Redmi 13']);
  assert.equal(ds[0].to, '/?q=galaxy');
});
