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
