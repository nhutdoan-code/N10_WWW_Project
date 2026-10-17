export function validateRegister(f) {
  const errors = {};
  if (!f.hoTen.trim()) errors.hoTen = 'Vui lòng nhập họ tên';
  if (!/^\S+@\S+\.\S+$/.test(f.email)) errors.email = 'Email không đúng định dạng';
  if (!/^0\d{9}$/.test(f.soDienThoai)) errors.soDienThoai = 'Số điện thoại phải đủ 10 chữ số, bắt đầu bằng 0';
  if (f.matKhau.length < 8) errors.matKhau = 'Mật khẩu phải từ 8 ký tự';
  if (f.matKhau !== f.nhapLai) errors.nhapLai = 'Mật khẩu nhập lại không khớp';
  return errors;
}
