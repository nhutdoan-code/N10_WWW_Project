import test from 'node:test';
import assert from 'node:assert/strict';
import { trangThaiVoucher } from '../src/utils/voucherAdmin.js';

const base = { ngayBatDau: '2026-01-01', ngayKetThuc: '2026-12-31', daDung: 0, soLuong: null, trangThaiHoatDong: true };
const today = '2026-11-15';

test('voucher đã tắt hiển thị "Đã tắt" dù còn trong thời hạn', () => {
  assert.equal(trangThaiVoucher({ ...base, trangThaiHoatDong: false }, today).label, 'Đã tắt');
});

test('voucher theo khoảng ngày: sắp diễn ra / đang chạy / hết hạn', () => {
  assert.equal(trangThaiVoucher({ ...base, ngayBatDau: '2026-12-01' }, today).label, 'Sắp diễn ra');
  assert.equal(trangThaiVoucher(base, today).label, 'Đang chạy');
  assert.equal(trangThaiVoucher({ ...base, ngayKetThuc: '2026-11-01' }, today).label, 'Hết hạn');
});

test('voucher dùng hết số lượt hiển thị "Hết lượt"', () => {
  assert.equal(trangThaiVoucher({ ...base, soLuong: 5, daDung: 5 }, today).label, 'Hết lượt');
});

import { mucGiam, tinhGiamVoucher, kiemTraVoucher } from '../src/utils/voucherAdmin.js';

test('nhãn mức giảm: tiền mặt / phần trăm / miễn phí ship', () => {
  assert.equal(mucGiam({ kieuGiam: 'SO_TIEN', soTienGiamCoDinh: 150000, loai: 'DON_HANG' }).ngan, '-150K');
  assert.equal(mucGiam({ kieuGiam: 'SO_TIEN', soTienGiamCoDinh: 1000000, loai: 'DON_HANG' }).ngan, '-1TR');
  assert.equal(mucGiam({ kieuGiam: 'PHAN_TRAM', phanTramGiam: 5, soTienGiamToiDa: 300000, loai: 'DON_HANG' }).ngan, '-5%');
  assert.equal(mucGiam({ kieuGiam: 'PHAN_TRAM', phanTramGiam: 100, soTienGiamToiDa: 50000, loai: 'VAN_CHUYEN' }).ngan, 'Miễn phí');
});

test('tiền giảm thực tế theo công thức M23', () => {
  const nest5 = { kieuGiam: 'PHAN_TRAM', phanTramGiam: 5, soTienGiamToiDa: 300000, donToiThieu: 5000000 };
  assert.equal(tinhGiamVoucher(nest5, 20000000), 300000);
  assert.equal(tinhGiamVoucher(nest5, 4000000), 0);
  const giam400 = { kieuGiam: 'SO_TIEN', soTienGiamCoDinh: 400000, donToiThieu: 7000000 };
  assert.equal(tinhGiamVoucher(giam400, 9000000), 400000);
});

test('kiểm tra form: % phải có trần, tiền mặt không vượt đơn tối thiểu', () => {
  assert.ok(kiemTraVoucher({ kieuGiam: 'PHAN_TRAM', phanTramGiam: 5, soTienGiamToiDa: '' }).loi.length > 0);
  assert.ok(kiemTraVoucher({ kieuGiam: 'SO_TIEN', soTienGiamCoDinh: 500000, donToiThieu: 300000, loai: 'DON_HANG' }).loi.length > 0);
  assert.equal(kiemTraVoucher({ kieuGiam: 'SO_TIEN', soTienGiamCoDinh: 150000, donToiThieu: 3000000, loai: 'DON_HANG' }).loi.length, 0);
});
