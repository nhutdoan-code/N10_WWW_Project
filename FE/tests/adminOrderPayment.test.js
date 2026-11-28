import test from 'node:test';
import assert from 'node:assert/strict';
import { paymentView, PAYMENT_LABELS, canConfirmCodPayment } from '../src/utils/adminOrderPayment.js';

const order = (method, status, more = {}) => ({
  trangThai: 'CHO_XAC_NHAN', phuongThucTt: method, tongTien: 8490000,
  khoaSuaSoLuong: method !== 'COD' || status !== 'CHO_THANH_TOAN',
  thanhToan: { phuongThuc: method, trangThai: status, soTien: 8490000, ...more },
});
test('VNPay paid status is independent of order status', () => {
  const view = paymentView(order('CHUYEN_KHOAN', 'DA_THANH_TOAN', { maGiaoDichNganHang: '123', ngayThanhToan: '2026-11-28T14:46:47' }));
  assert.equal(view.label, 'Đã thanh toán'); assert.equal(view.method, 'VNPay');
  assert.equal(view.providerId, '123'); assert.equal(view.canEdit, false);
});
test('COD pending remains locked even if an older API allows editing', () => {
  const cod = order('COD', 'CHO_THANH_TOAN');
  assert.equal(paymentView(cod).label, 'Chưa thu tiền (COD)');
  assert.equal(paymentView(cod).canEdit, false);
  assert.equal(paymentView({ ...cod, trangThai: 'DA_GIAO' }).canEdit, false);
  assert.equal(paymentView({ ...cod, khoaSuaSoLuong: true }).canEdit, false);
  assert.equal(paymentView(order('COD', 'DA_THANH_TOAN')).canEdit, false);
});
test('No payment record does not imply paid or editable even if delivered', () => {
  const view = paymentView({ trangThai: 'DA_GIAO', phuongThucTt: 'CHUYEN_KHOAN' });
  assert.equal(view.label, 'Chưa có dữ liệu'); assert.equal(view.canEdit, false);
});
test('Refund and failed statuses remain distinct from paid', () => {
  for (const s of ['CAN_HOAN_TIEN','DANG_HOAN_TIEN','DA_HOAN_TIEN','THAT_BAI','DA_HUY']) {
    const view = paymentView(order('VI_DIEN_TU', s));
    assert.equal(view.label, PAYMENT_LABELS[s]); assert.notEqual(view.tone, 'paid');
    assert.equal(view.canEdit, false);
  }
});
test('Amount mismatch is visible and provider reference matches the method', () => {
  const view = paymentView(order('VI_DIEN_TU', 'DA_THANH_TOAN', { soTien: 100, maGiaoDichMoMo: 'momo1', maGiaoDich: 'ref1' }));
  assert.equal(view.mismatch, true); assert.equal(view.providerId, 'momo1'); assert.equal(view.reference, 'ref1');
});

test('COD collection is allowed only at delivery stages, with the original pending amount', () => {
  const cod = order('COD', 'CHO_THANH_TOAN');
  for (const status of ['CHO_XAC_NHAN','DA_XAC_NHAN','DANG_CHUAN_BI','DA_HUY']) assert.equal(canConfirmCodPayment({...cod, trangThai:status}), false);
  for (const status of ['DANG_GIAO','CHO_KHACH_XAC_NHAN','DA_GIAO']) assert.equal(canConfirmCodPayment({...cod, trangThai:status}), true);
  assert.equal(canConfirmCodPayment({...cod, trangThai:'DA_GIAO', tongTien:100}), false);
  assert.equal(canConfirmCodPayment({...cod, trangThai:'DA_GIAO', thanhToan:null}), false);
});
test('COD cannot be confirmed again and online payments cannot be marked COD', () => {
  for (const status of ['DA_THANH_TOAN','DA_HUY','CAN_HOAN_TIEN','DANG_HOAN_TIEN','DA_HOAN_TIEN','THAT_BAI']) assert.equal(canConfirmCodPayment({...order('COD',status),trangThai:'DA_GIAO'}),false);
  assert.equal(canConfirmCodPayment({...order('COD','CHO_THANH_TOAN',{ngayThanhToan:'2026-11-28T14:00:00'}),trangThai:'DA_GIAO'}),false);
  for (const method of ['CHUYEN_KHOAN','VI_DIEN_TU']) assert.equal(canConfirmCodPayment({...order(method,'CHO_THANH_TOAN'),trangThai:'DANG_GIAO'}),false);
});
