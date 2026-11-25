import test from 'node:test';
import assert from 'node:assert/strict';
import { notificationLink } from '../src/utils/notificationLink.js';
import { formatKhoangNgayGiao } from '../src/utils/shipping.js';

test('thông báo có đường dẫn backend gửi kèm thì mở đúng trang đó', () => {
  assert.equal(notificationLink({ duongDan: '/orders/DH123', noiDung: 'x' }), '/orders/DH123');
  assert.equal(notificationLink({ duongDan: '/products/SP01#hoi-dap', noiDung: 'x' }), '/products/SP01#hoi-dap');
});

test('không mở URL ngoài từ dữ liệu thông báo', () => {
  assert.equal(notificationLink({ duongDan: 'https://evil.example', noiDung: 'abc' }), null);
  assert.equal(notificationLink({ duongDan: '//evil.example', noiDung: 'abc' }), null);
});

test('thông báo cũ chưa có đường dẫn: suy ra từ nội dung', () => {
  assert.equal(notificationLink({ noiDung: 'Đơn hàng DH1763000000123 đã được giao.' }), '/orders/DH1763000000123');
  assert.equal(notificationLink({ noiDung: 'Yêu cầu đổi trả đơn DH1: DA_DUYET' }), '/returns');
  assert.equal(notificationLink({ noiDung: 'Câu hỏi của bạn về sản phẩm đã được trả lời' }), null);
});

test('định dạng khoảng ngày giao dự kiến', () => {
  assert.equal(formatKhoangNgayGiao('2026-11-26', '2026-11-27'), 'Thứ 5, 26/11 - Thứ 6, 27/11');
  assert.equal(formatKhoangNgayGiao('2026-11-29', '2026-11-29'), 'CN, 29/11');
  assert.equal(formatKhoangNgayGiao(null, null), null);
});
