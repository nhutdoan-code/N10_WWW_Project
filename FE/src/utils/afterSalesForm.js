export const TYPES = { TRA_HANG_HOAN_TIEN: 'Trả hàng / hoàn tiền', DOI_HANG: 'Đổi sản phẩm', BAO_HANH: 'Bảo hành', KHIEU_NAI: 'Khiếu nại' };
export const REASONS = ['Máy lỗi', 'Giao sai hàng', 'Không đúng mô tả', 'Đổi ý', 'Khác'];
export const sameExchange = reason => ['Máy lỗi','Giao sai hàng'].includes(reason);
export const upgradeExchange = reason => ['Đổi ý','Không đúng mô tả'].includes(reason);
export function exchangeQuote(order, product, qty, reason, target) {
  const net = refundEstimate(order, product, qty);
  if (!net || !target || !Number.isSafeInteger(Number(target.stock)) || Number(target.stock) < Number(qty)) return null;
  if (sameExchange(reason)) return target.id === product.maPhienBan ? {net:net.amount, difference:0} : null;
  if (!upgradeExchange(reason) || !Number.isSafeInteger(Number(target.price)) || Number(target.price) <= 0) return null;
  const total = Number(target.price) * Number(qty);
  return Number.isSafeInteger(total) && total >= net.amount ? {net:net.amount,difference:total-net.amount} : null;
}
export const CONDITIONS = ['Còn nguyên seal', 'Đã kích hoạt', 'Có lỗi'];
export const FAULTS = ['Không lên nguồn', 'Màn hình / cảm ứng', 'Pin / sạc', 'Camera / âm thanh', 'Kết nối', 'Lỗi khác'];
export const allowedConditions = reason => reason === 'Máy lỗi' ? ['Có lỗi'] : reason === 'Đổi ý' ? ['Còn nguyên seal'] : ['Giao sai hàng','Không đúng mô tả'].includes(reason) ? ['Còn nguyên seal','Đã kích hoạt'] : CONDITIONS;
// Keep the existing string-valued chiTiet API; selected faults use '; ' as separator.
export const selectedFaults = value => typeof value === 'string' && value ? value.split('; ') : [];
export const validFaults = value => { const items = selectedFaults(value); return items.length > 0 && items.length <= FAULTS.length && new Set(items).size === items.length && items.every(item => FAULTS.includes(item)); };
export function toggleFault(value, item) {
  const selected = selectedFaults(value).filter(x => FAULTS.includes(x));
  if (!FAULTS.includes(item)) return selected.join('; ');
  return FAULTS.filter(x => x === item ? !selected.includes(x) : selected.includes(x)).join('; ');
}
// No minimum character quota; nonblank descriptions and a technical size cap remain.
export const descriptionMin = () => 1;
export const MISMATCHES = ['Sai dung lượng','Sai màu','Sai phiên bản','Thiếu phụ kiện','Khác'];
export const BANKS = ['Vietcombank', 'VietinBank', 'BIDV', 'Agribank', 'Techcombank', 'MB', 'ACB', 'VPBank', 'TPBank', 'Sacombank', 'HDBank', 'VIB', 'SHB', 'OCB', 'MSB', 'SeABank', 'LPBank', 'Eximbank', 'Nam A Bank', 'BVBank', 'ABBANK', 'PVcomBank', 'Vietbank', 'Bac A Bank', 'KienlongBank', 'NCB', 'Viet A Bank', 'UOB', 'HSBC', 'Standard Chartered', 'Woori Bank', 'Shinhan Bank', 'Public Bank', 'Hong Leong Bank', 'CIMB', 'Co-opBank', 'Bao Viet Bank'];
export const accountDigits = value => String(value ?? '').replace(/[^0-9]/g,'').slice(0,20);
export const normalizeHolder = value => String(value ?? '').normalize('NFD').replace(/\p{M}/gu,'').toLocaleUpperCase('vi-VN').replace(/Đ/g,'D');
export const validHolder = value => /^[A-Z][A-Z .’'\-]{1,99}$/u.test(String(value ?? '').trim());
// Clear validation on controls whose meaning/visibility changes; validate again on submit.
export function clearFieldErrors(errors, name) {
  if (name === 'reason' || name === 'loai') return {};
  const reset = {maDonHang:['maPhienBan','soLuong','targetId','refund','bank','account','holder','sealConfirmed','fault','appearance'], maPhienBan:['soLuong','targetId','sealConfirmed','fault','appearance'], soLuong:['sealConfirmed'], condition:['fault','images'], refund:['bank','account','holder']};
  const next = {...errors};
  for (const key of [name,'submit',...(reset[name] || [])]) delete next[key];
  return next;
}
// A field is revealed only after its own blur or a submit of the current form.
export const visibleValidationErrors = (invalid, interaction) => Object.fromEntries(Object.entries(invalid).filter(([key]) => interaction[key] === 'touched' || interaction[key] === 'submitted'));
export function validationAfterChange(interaction, name) {
  const next = clearFieldErrors(interaction, name);
  if (!['reason','loai'].includes(name) && interaction[name]) next[name] = interaction[name];
  return next;
}
export const preferredCondition = reason => reason === 'Máy lỗi' ? 'Có lỗi' : ['Đổi ý','Giao sai hàng'].includes(reason) ? 'Còn nguyên seal' : '';
// Match Backend: floor the allocated merchandise discount, exclude delivery/fees.
export function refundEstimate(order, product, quantity) {
  if (!order || !product || order.tamTinh == null || order.giamGia == null || product.donGia == null) return null;
  const values = [order.tamTinh, order.giamGia, product.donGia, quantity].map(Number);
  if (values.some(v => !Number.isSafeInteger(v) || v < 0) || values[3] < 1 || values[3] > Number(product.soLuong)) return null;
  const [subtotal, discount, unit, qty] = values.map(BigInt);
  const gross = unit * qty;
  const allocated = subtotal > 0n ? discount * gross / subtotal : 0n;
  const net = gross > allocated ? gross - allocated : 0n;
  if (net > BigInt(Number.MAX_SAFE_INTEGER)) return null;
  return {amount:Number(net),discount:Number(gross-net)};
}
export const maskAccount = value => String(value ?? '').replace(/(^|\n)(Số tài khoản:[ \t]*)([0-9]{4,30})(?=[ \t]*(?:\r?\n|$))/g, (_,start,label,number) => `${start}${label}•••• ${number.slice(-4)}`);
export const evidenceHint = (type, reason, condition) => {
  if (type === 'BAO_HANH') return 'Bắt buộc: chụp mặt trước, mặt sau và vị trí lỗi. Tránh để lộ thông tin cá nhân khác.';
  if (reason === 'Không đúng mô tả' && ['DOI_HANG','TRA_HANG_HOAN_TIEN'].includes(type)) return 'Bắt buộc: chụp máy, hộp/tem hoặc màn hình thông tin thiết bị để đối chiếu.';
  if (reason === 'Giao sai hàng' && ['DOI_HANG','TRA_HANG_HOAN_TIEN'].includes(type)) return 'Bắt buộc: chụp sản phẩm thực nhận, hộp và tem.';
  if (proofRequired(type,reason,condition)) return 'Bắt buộc: chụp mặt trước, mặt sau và vị trí lỗi.';
  return 'Không bắt buộc; có thể thêm ảnh để cửa hàng dễ kiểm tra.';
};
export const refundOptions = order => order?.phuongThucTt === 'COD' ? ['Chuyển khoản'] : order?.phuongThucTt === 'CHUYEN_KHOAN' ? ['Phương thức thanh toán gốc'] : order?.phuongThucTt === 'VI_DIEN_TU' ? REFUNDS : [];
export const REFUNDS = ['Phương thức thanh toán gốc', 'Chuyển khoản'];
export const warrantyAppearanceWarning = appearance => ['Rơi vỡ','Vào nước'].includes(appearance) ? 'Hư hỏng do rơi vỡ/vào nước có thể không thuộc diện bảo hành miễn phí; cửa hàng sẽ báo phí sau khi kiểm tra.' : '';
export const APPEARANCES = ['Bình thường', 'Trầy xước', 'Rơi vỡ', 'Vào nước', 'Khác'];
export const TOPICS = ['Giao hàng', 'Thái độ nhân viên', 'Giá/khuyến mãi', 'Sản phẩm', 'Khác'];
export const MAX_IMAGES = 5;
export const MAX_BYTES = 5 * 1024 * 1024;
export const proofRequired = (type, reason, condition) => type === 'BAO_HANH' || (['DOI_HANG', 'TRA_HANG_HOAN_TIEN'].includes(type) && (REASONS.slice(0, 3).includes(reason) || condition === 'Có lỗi'));
export function imageError(file) {
  if (!/\.(jpe?g|png|webp)$/i.test(file.name) || !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return 'Chỉ nhận JPG, PNG hoặc WEBP.';
  if (file.size <= 0 || file.size > MAX_BYTES) return 'Ảnh phải lớn hơn 0 và không quá 5 MB.';
  return '';
}
export function validateForm(type, f, order, product, images) {
  const errors = {};
  if (!TYPES[type]) return { loai: 'Vui lòng chọn loại yêu cầu.' };
  const isReturn = type === 'TRA_HANG_HOAN_TIEN' || type === 'DOI_HANG';
  if (type !== 'KHIEU_NAI' || f.maDonHang) { if (!order) errors.maDonHang = 'Vui lòng chọn đơn hàng đủ điều kiện.'; }
  if (type !== 'KHIEU_NAI' && !product) errors.maPhienBan = 'Vui lòng chọn sản phẩm.';
  if (isReturn && product && (!Number.isInteger(Number(f.soLuong)) || Number(f.soLuong) < 1 || Number(f.soLuong) > product.soLuong)) errors.soLuong = 'Số lượng không hợp lệ.';
  if (isReturn) {
    if (!REASONS.includes(f.reason)) errors.reason = 'Vui lòng chọn lý do.';
    if (f.reason !== 'Máy lỗi' && !allowedConditions(f.reason).includes(f.condition)) errors.condition = 'Vui lòng chọn tình trạng máy phù hợp với lý do.';
    if (f.reason === 'Máy lỗi' && !validFaults(f.fault)) errors.fault = 'Vui lòng chọn ít nhất một loại lỗi hợp lệ.';
    if (f.reason === 'Đổi ý' && f.sealConfirmed !== 'true') errors.sealConfirmed = 'Vui lòng xác nhận máy chưa bóc seal, đầy đủ phụ kiện.';
    if (f.reason === 'Không đúng mô tả' && !MISMATCHES.includes(f.mismatch)) errors.mismatch = 'Vui lòng chọn điểm khác với mô tả.';
  }
  if (type === 'TRA_HANG_HOAN_TIEN') { if (!refundOptions(order).includes(f.refund)) errors.refund = 'Vui lòng chọn hình thức hoàn tiền phù hợp.'; if (f.refund === 'Chuyển khoản') { if (!BANKS.includes(f.bank)) errors.bank = 'Vui lòng chọn ngân hàng từ danh sách.'; if (!/^[0-9]{6,20}$/.test(String(f.account ?? '').trim())) errors.account = 'Số tài khoản gồm 6–20 chữ số.'; if (!validHolder(f.holder)) errors.holder = 'Vui lòng nhập tên chủ tài khoản đúng như tại ngân hàng.'; } }
  if (type === 'DOI_HANG' && !sameExchange(f.reason) && !f.targetId) errors.targetId = 'Vui lòng chọn phiên bản muốn đổi từ danh mục.';
  if (type === 'DOI_HANG' && !sameExchange(f.reason) && !upgradeExchange(f.reason)) errors.reason = 'Vui lòng chọn lý do đổi máy hợp lệ.';
  if (type === 'BAO_HANH') { if (!validFaults(f.fault)) errors.fault = 'Vui lòng chọn ít nhất một loại lỗi hợp lệ.'; if (!APPEARANCES.includes(f.appearance)) errors.appearance = 'Vui lòng chọn tình trạng ngoại hình.'; }
  if (type === 'KHIEU_NAI' && !TOPICS.includes(f.topic)) errors.topic = 'Vui lòng chọn chủ đề.';
  const length = String(f.lyDo ?? '').trim().length;
  if (!length) errors.lyDo = 'Vui lòng mô tả vấn đề.';
  else if (length > 1000) errors.lyDo = 'Mô tả tối đa 1000 ký tự.';
  if (proofRequired(type, f.reason, f.condition) && !images.length) errors.images = 'Vui lòng thêm ít nhất 1 ảnh minh chứng.';
  return errors;
}

export const descriptionHint = (type, reason) => {
  if (type === 'BAO_HANH') return 'Lỗi xảy ra khi nào? Thường xuyên hay thỉnh thoảng? Bạn đã thử khởi động lại/cập nhật chưa? Ví dụ: camera sau bị đen mỗi lần mở ứng dụng, đã khởi động lại nhưng vẫn lỗi.';
  if (reason === 'Không đúng mô tả') return 'Ví dụ: trang web ghi 256GB nhưng máy nhận 128GB. Nêu thêm thông tin để cửa hàng đối chiếu.';
  if (type === 'DOI_HANG' && !reason) return 'Mô tả thêm tình trạng máy, lý do cụ thể hoặc yêu cầu khác.';
  return ({'Máy lỗi':'Ví dụ: máy không lên nguồn khi mở hộp, đã thử sạc…','Giao sai hàng':'Mô tả sản phẩm thực nhận và điểm khác với đơn hàng.','Không đúng mô tả':'Nêu điểm khác với thông tin sản phẩm trên website.','Đổi ý':'Giải thích lý do đổi ý và tình trạng seal, phụ kiện.','Khác':'Nêu rõ lý do khác và thông tin cần cửa hàng hỗ trợ.'})[reason] || 'Mô tả tình trạng sản phẩm và lý do yêu cầu.';
};
