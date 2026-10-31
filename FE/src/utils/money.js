// Helper định dạng tiền cho input admin.
// State luôn giữ chuỗi chỉ gồm chữ số (ví dụ "9990000"), UI hiển thị "9.990.000".

export function onlyDigits(value) {
  return String(value ?? '').replace(/\D/g, '');
}

/**
 * Chuyển giá trị thô (number/string có hoặc không có dấu phân cách)
 * thành chuỗi hiển thị dạng 9.990.000.
 */
export function formatMoneyInput(value) {
  const digits = onlyDigits(value).replace(/^0+(?=\d)/, '');
  if (!digits) return '';
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/**
 * Lấy phần chữ số từ input người dùng nhập hoặc dán.
 * "10.990.000" -> "10990000"; "" -> "".
 */
export function parseMoneyInput(value) {
  return onlyDigits(value).replace(/^0+(?=\d)/, '');
}

/**
 * Chuyển về number để gửi backend. Chuỗi rỗng trả về null.
 */
export function moneyToNumber(value) {
  const digits = onlyDigits(value);
  if (!digits) return null;
  return Number(digits);
}
