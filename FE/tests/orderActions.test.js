import test from 'node:test';
import assert from 'node:assert/strict';
import { getOrderActions } from '../src/utils/orderActions.js';

const cases = [
  ['CHO_XAC_NHAN', false, false, false, true],
  ['DA_XAC_NHAN', false, false, false, true],
  ['DANG_CHUAN_BI', false, false, false, true],
  ['DANG_GIAO', false, false, true, false],
  ['CHO_KHACH_XAC_NHAN', false, false, true, false],
  ['DA_GIAO', true, true, true, false],
  ['DA_HUY', true, false, true, false],
];

for (const [status, reorder, afterDelivery, complain, cancel] of cases) {
  test(`Hành động của đơn ${status}`, () => {
    const actual = getOrderActions(status);
    assert.equal(actual.reorder, reorder);
    assert.equal(actual.afterDelivery, afterDelivery);
    assert.equal(actual.complain, complain);
    assert.equal(actual.cancel, cancel);
  });
}

test('Trạng thái không xác định không hiển thị tác vụ nghiệp vụ', () => {
  const actions = getOrderActions('KHONG_HOP_LE');
  assert.deepEqual(actions, {
    reorder: false, afterDelivery: false, complain: false,
    cancel: false, confirmReceived: false,
  });
});
