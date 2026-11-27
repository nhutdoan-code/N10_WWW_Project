import test from 'node:test';
import assert from 'node:assert/strict';
import {eligible, remainingLabel} from '../src/utils/warrantyPolicy.js';
import {proofRequired, validateForm, imageError, MAX_BYTES} from '../src/utils/afterSalesForm.js';
const delivered = {trangThai:'DA_GIAO',ngayGiao:'2026-11-01T12:00:00',ngayKhachXacNhan:'2026-11-02T12:00:00',hanDoiTra:'2026-11-09T12:00:00',hanBaoHanh:'2027-11-02T12:00:00'};
const date = value => new Date(value);
test('return includes exactly day seven but not one second later or day eight', () => {
  assert(eligible(delivered,'DOI_HANG',date('2026-11-09T12:00:00')));
  assert(!eligible(delivered,'DOI_HANG',date('2026-11-09T12:00:01')));
  assert(!eligible(delivered,'TRA_HANG_HOAN_TIEN',date('2026-11-10T12:00:00')));
});
test('pending receipt blocks both return and warranty even with delivery dates', () => {
  const order = {...delivered,trangThai:'CHO_KHACH_XAC_NHAN',ngayKhachXacNhan:null};
  assert(!eligible(order,'DOI_HANG',date('2026-11-03T00:00:00')));
  assert(!eligible(order,'BAO_HANH',date('2026-11-03T00:00:00')));
  assert(eligible(order,'KHIEU_NAI'));
});
test('warranty includes exact deadline and legacy delivered date', () => {
  assert(eligible(delivered,'BAO_HANH',date('2027-11-02T12:00:00')));
  assert(!eligible(delivered,'BAO_HANH',date('2027-11-02T12:00:01')));
  assert(eligible({...delivered,ngayKhachXacNhan:null,hanBaoHanh:'2027-11-01T12:00:00'},'BAO_HANH',date('2027-11-01T12:00:00')));
});
test('future and missing dates are rejected, blank type is rejected', () => {
  assert(!eligible(delivered,'BAO_HANH',date('2026-11-01T12:00:00')));
  assert(!eligible({...delivered,hanDoiTra:null},'DOI_HANG',date('2026-11-02T12:00:00')));
  assert(!eligible(delivered,''));
  assert.match(remainingLabel(delivered,'DOI_HANG',date('2026-11-03T12:00:00')),/6 ngày/);
});
test('photos required only for warranty and specified return reasons', () => {
  assert(proofRequired('BAO_HANH',''));
  for(const reason of ['Máy lỗi','Giao sai hàng','Không đúng mô tả']) assert(proofRequired('DOI_HANG',reason));
  assert(!proofRequired('TRA_HANG_HOAN_TIEN','Đổi ý'));
  assert(!proofRequired('KHIEU_NAI','Máy lỗi'));
});
test('file extension, MIME and exact 5MB limit', () => {
  assert.equal(imageError({name:'photo.webp',type:'image/webp',size:MAX_BYTES}),'');
  assert(imageError({name:'photo.webp',type:'image/webp',size:MAX_BYTES+1}));
  assert(imageError({name:'photo.exe',type:'image/jpeg',size:1}));
  assert(imageError({name:'photo.jpg',type:'text/plain',size:1}));
});
test('complaint needs no order; description cannot be whitespace or out of range', () => {
  const f = {maDonHang:'',topic:'Khác',lyDo:'a'.repeat(20)};
  assert.deepEqual(validateForm('KHIEU_NAI',f,null,null,[]),{});
  for(const desc of [' '.repeat(20),'a'.repeat(19),'a'.repeat(1001)]) assert(validateForm('KHIEU_NAI',{...f,lyDo:desc},null,null,[]).lyDo);
  assert.deepEqual(validateForm('KHIEU_NAI',{...f,lyDo:'a'.repeat(1000)},null,null,[]),{});
});
test('dynamic forms require product, fields, bank account and evidence', () => {
  const form = {maDonHang:'DH1',maPhienBan:'PB1',soLuong:1,lyDo:'a'.repeat(20),reason:'Máy lỗi',condition:'Có lỗi',fault:'Không lên nguồn',refund:'Chuyển khoản',bank:'MB',account:'12345678',holder:'NGUYEN VAN A'};
  assert(validateForm('TRA_HANG_HOAN_TIEN',form,delivered,{soLuong:1},[]).images);
  assert(validateForm('TRA_HANG_HOAN_TIEN',{...form,account:'bad'},delivered,{soLuong:1},[{}]).account);
  assert(validateForm('TRA_HANG_HOAN_TIEN',form,delivered,null,[{}]).maPhienBan);
  assert.deepEqual(validateForm('TRA_HANG_HOAN_TIEN',form,delivered,{soLuong:1},[{}]),{});
});

test('IMEI Luhn, serial, and saved device choices', async () => {
  const {validImei, deviceIdError, deviceIdentifiers} = await import('../src/utils/afterSalesForm.js');
  assert(validImei('490154203237518'));
  assert(!validImei('490154203237519'));
  assert(!validImei('49015420323751'));
  assert(!validImei('49015420323751A'));
  assert.equal(deviceIdError('SERIAL','AB-1234'),'');
  assert(deviceIdError('SERIAL','a'));
  assert.deepEqual(deviceIdentifiers({imeis:['490154203237518','490154203237519','490154203237518']},'IMEI'),['490154203237518']);
});
test('warranty requires device and appearance, but no customer outcome', () => {
  const f = {maDonHang:'DH1',maPhienBan:'PB1',lyDo:'a'.repeat(20),serialKind:'IMEI',serial:'490154203237518',appearance:'Bình thường',fault:'Camera / âm thanh'};
  assert.deepEqual(validateForm('BAO_HANH',f,delivered,{},[{}]),{});
  assert(validateForm('BAO_HANH',{...f,serial:'490154203237519'},delivered,{},[{}]).serial);
  assert(validateForm('BAO_HANH',{...f,appearance:''},delivered,{},[{}]).appearance);
});
test('exact remaining days and expired labels', async () => {
  const {WARRANTY_OPTIONS} = await import('../src/utils/warrantyPolicy.js');
  assert.equal(remainingLabel(delivered,'BAO_HANH',date('2027-10-02T12:00:00')),'Còn 31 ngày BH');
  assert.equal(remainingLabel(delivered,'BAO_HANH',date('2027-11-02T12:00:01')),'Đã quá hạn');
  assert.equal(WARRANTY_OPTIONS.length,3);
  assert(!WARRANTY_OPTIONS.some(x=>x.includes('Hoàn tiền')));
});
test('receipt excludes sensitive description and clearly distinguishes submission from approval', async () => {
  const {receiptText} = await import('../src/utils/afterSalesReceipt.js');
  const text = receiptText({maYeuCau:'HM001',loai:'BAO_HANH',maDonHang:'DH001',lyDo:'IMEI secret',tenSanPham:'Máy A'});
  assert(text.includes('HM001'));
  assert(text.includes('không phải xác nhận đã duyệt'));
  assert(!text.includes('IMEI secret'));
});

test('V4 COD requires bank transfer; reason and condition must agree', async () => {
  const {refundOptions,allowedConditions} = await import('../src/utils/afterSalesForm.js');
  const order = {...delivered,phuongThucTt:'COD'};
  const f = {maDonHang:'DH1',maPhienBan:'PB1',soLuong:1,lyDo:'a'.repeat(20),reason:'Đổi ý',condition:'Còn nguyên seal',sealConfirmed:'true',refund:'Chuyển khoản',bank:'MB',account:'12345678',holder:'NGUYEN VAN A'};
  assert.deepEqual(refundOptions(order),['Chuyển khoản']);
  assert.deepEqual(allowedConditions('Máy lỗi'),['Có lỗi']);
  assert.deepEqual(validateForm('TRA_HANG_HOAN_TIEN',f,order,{soLuong:1},[]),{});
  assert(validateForm('TRA_HANG_HOAN_TIEN',{...f,refund:'Phương thức thanh toán gốc'},order,{soLuong:1},[]).refund);
  assert(validateForm('TRA_HANG_HOAN_TIEN',{...f,condition:'Có lỗi'},order,{soLuong:1},[]).condition);
  assert(validateForm('TRA_HANG_HOAN_TIEN',{...f,reason:'Máy lỗi'},order,{soLuong:1},[]).condition);
  assert(!validateForm('TRA_HANG_HOAN_TIEN',{...f,reason:'Khác',condition:'Có lỗi'},order,{soLuong:1},[]).fault);
  assert(proofRequired('DOI_HANG','Khác','Có lỗi'));
});
test('V4 exchange requires actual variant id and quantity in bounds', () => {
  const f = {maDonHang:'DH1',maPhienBan:'PB1',soLuong:1,lyDo:'a'.repeat(20),reason:'Đổi ý',condition:'Còn nguyên seal',sealConfirmed:'true',targetId:'PB2'};
  assert.deepEqual(validateForm('DOI_HANG',f,delivered,{soLuong:1},[]),{});
  assert(validateForm('DOI_HANG',{...f,targetId:''},delivered,{soLuong:1},[]).targetId);
  assert(validateForm('DOI_HANG',{...f,soLuong:2},delivered,{soLuong:1},[]).soLuong);
});
test('V4 deadline urgency and legacy order fallback', async () => {
  const {deadlineTone} = await import('../src/utils/warrantyPolicy.js');
  assert.equal(deadlineTone(delivered,'DOI_HANG',date('2026-11-07T12:00:00')),'soon');
  assert.equal(deadlineTone(delivered,'DOI_HANG',date('2026-11-08T12:00:00')),'urgent');
  assert(eligible({...delivered,ngayKhachXacNhan:null,hanDoiTra:'2026-11-08T12:00:00'},'DOI_HANG',date('2026-11-08T12:00:00')));
  assert(!eligible(delivered,'DOI_HANG',date('2026-11-01T13:00:00')));
});

test('V5 return reasons constrain conditions; only machine fault requires fault details', async () => {
  const {allowedConditions,preferredCondition,MISMATCHES,evidenceHint} = await import('../src/utils/afterSalesForm.js');
  const f = {maDonHang:'DH1',maPhienBan:'PB1',soLuong:1,lyDo:'a'.repeat(20),refund:'Phương thức thanh toán gốc'};
  for (const reason of ['Giao sai hàng','Không đúng mô tả']) {
    assert.deepEqual(allowedConditions(reason),['Còn nguyên seal','Đã kích hoạt']);
    const invalid = validateForm('TRA_HANG_HOAN_TIEN',{...f,reason,condition:'Có lỗi'},delivered,{soLuong:1},[{}]);
    assert(invalid.condition); assert(!invalid.fault);
  }
  assert.equal(preferredCondition('Máy lỗi'),'Có lỗi');
  assert.equal(preferredCondition('Giao sai hàng'),'Còn nguyên seal');
  assert.equal(preferredCondition('Đổi ý'),'Còn nguyên seal');
  const mismatch = {...f,reason:'Không đúng mô tả',condition:'Đã kích hoạt'};
  assert(validateForm('TRA_HANG_HOAN_TIEN',mismatch,delivered,{soLuong:1},[]).mismatch);
  assert.deepEqual(validateForm('TRA_HANG_HOAN_TIEN',{...mismatch,mismatch:MISMATCHES[0]},delivered,{soLuong:1},[{}]),{});
  assert(validateForm('TRA_HANG_HOAN_TIEN',{...mismatch,mismatch:MISMATCHES[0]},delivered,{soLuong:1},[]).images);
  assert.match(evidenceHint('TRA_HANG_HOAN_TIEN','Không đúng mô tả','Đã kích hoạt'),/hộp\/tem/);
  assert.match(evidenceHint('DOI_HANG','Máy lỗi','Có lỗi'),/vị trí lỗi/);
});
test('V5 bank fields are validated and customer account display is masked', async () => {
  const {accountDigits,normalizeHolder,validHolder,maskAccount} = await import('../src/utils/afterSalesForm.js');
  const f = {maDonHang:'DH1',maPhienBan:'PB1',soLuong:1,lyDo:'a'.repeat(20),reason:'Đổi ý',condition:'Còn nguyên seal',sealConfirmed:'true',refund:'Chuyển khoản',bank:'MB',account:'00123456',holder:'NGUYỄN VĂN A'};
  const order = {...delivered,phuongThucTt:'COD'};
  assert.deepEqual(validateForm('TRA_HANG_HOAN_TIEN',f,order,{soLuong:1},[]),{});
  assert(validateForm('TRA_HANG_HOAN_TIEN',{...f,bank:'FAKE'},order,{soLuong:1},[]).bank);
  assert(validateForm('TRA_HANG_HOAN_TIEN',{...f,account:'12345'},order,{soLuong:1},[]).account);
  assert(validateForm('TRA_HANG_HOAN_TIEN',{...f,holder:'A\n123'},order,{soLuong:1},[]).holder);
  assert.equal(accountDigits('00a123-456'),'00123456');
  assert.equal(accountDigits('1'.repeat(22)).length,20);
  assert.equal(normalizeHolder('Nguyễn Văn A'),'NGUYỄN VĂN A');
  assert(validHolder('NGUYEN VAN A'));
  assert.equal(maskAccount('Ngân hàng: MB\nSố tài khoản: 00123456\nChủ tài khoản: A'),'Ngân hàng: MB\nSố tài khoản: •••• 3456\nChủ tài khoản: A');
});
test('V5 refund estimate allocates merchandise voucher discount, excluding fees', async () => {
  const {refundEstimate} = await import('../src/utils/afterSalesForm.js');
  assert.deepEqual(refundEstimate({tamTinh:17690000,giamGia:0,phiVanChuyen:30000},{donGia:17690000,soLuong:1},1),{amount:17690000,discount:0});
  assert.deepEqual(refundEstimate({tamTinh:20000000,giamGia:1000000},{donGia:10000000,soLuong:1},1),{amount:9500000,discount:500000});
  assert.deepEqual(refundEstimate({tamTinh:3,giamGia:1},{donGia:2,soLuong:1},1),{amount:2,discount:0});
  assert.equal(refundEstimate({tamTinh:3,giamGia:1},{donGia:2,soLuong:1},2),null);
});

test('V5 changing reason/refund/order clears errors of newly visible dependent fields', async () => {
  const {clearFieldErrors} = await import('../src/utils/afterSalesForm.js');
  assert.deepEqual(clearFieldErrors({reason:'old',condition:'old',fault:'old',mismatch:'old',images:'old',lyDo:'keep'},'reason'),{});
  assert.deepEqual(clearFieldErrors({refund:'old',holder:'old',account:'old',bank:'old',lyDo:'keep'},'refund'),{lyDo:'keep'});
  assert.deepEqual(clearFieldErrors({maDonHang:'old',holder:'old',bank:'old',targetId:'old',lyDo:'keep'},'maDonHang'),{lyDo:'keep'});
});


test('V6 all field interactions reset on reason/type changes; dependent controls start clean', async () => {
  const {visibleValidationErrors, validationAfterChange} = await import('../src/utils/afterSalesForm.js');
  const invalid = {lyDo:'short',fault:'required',holder:'required',sealConfirmed:'required'};
  assert.deepEqual(visibleValidationErrors(invalid,{}),{});
  const submitted = {lyDo:'submitted',fault:'submitted',holder:'submitted',sealConfirmed:'submitted'};
  assert.deepEqual(visibleValidationErrors(invalid,submitted),invalid);
  for (const key of ['reason','loai']) assert.deepEqual(visibleValidationErrors(invalid,validationAfterChange(submitted,key)),{});
  assert.deepEqual(visibleValidationErrors(invalid,{lyDo:'touched'}),{lyDo:'short'});
  assert.deepEqual(visibleValidationErrors(invalid,validationAfterChange(submitted,'refund')),{lyDo:'short',fault:'required',sealConfirmed:'required'});
  assert.equal(validationAfterChange(submitted,'lyDo').lyDo,'submitted');
  assert(!validationAfterChange(submitted,'soLuong').sealConfirmed);
});
test('V6 multiple fault chips toggle; invalid or repeated labels rejected', async () => {
  const {toggleFault, selectedFaults, validFaults} = await import('../src/utils/afterSalesForm.js');
  const f = {maDonHang:'DH1',maPhienBan:'PB1',soLuong:1,lyDo:'a'.repeat(20),reason:'Máy lỗi',condition:'Có lỗi',refund:'Phương thức thanh toán gốc'};
  const value = toggleFault(toggleFault('', 'Pin / sạc'), 'Màn hình / cảm ứng');
  assert.deepEqual(selectedFaults(value),['Màn hình / cảm ứng','Pin / sạc']);
  assert(validFaults(value));
  assert.deepEqual(validateForm('TRA_HANG_HOAN_TIEN',{...f,fault:value},delivered,{soLuong:1},[{}]),{});
  assert.equal(toggleFault(value,'Pin / sạc'),'Màn hình / cảm ứng');
  for (const fault of ['', 'Pin / sạc; Pin / sạc', 'Pin / sạc; Sai', 'Pin / sạc; ']) {
    assert(validateForm('TRA_HANG_HOAN_TIEN',{...f,fault},delivered,{soLuong:1},[{}]).fault);
  }
});
test('V6 change of mind requires an explicit seal commitment and 10 characters for both return types', async () => {
  const {descriptionMin} = await import('../src/utils/afterSalesForm.js');
  const f = {maDonHang:'DH1',maPhienBan:'PB1',soLuong:1,lyDo:'a'.repeat(10),reason:'Đổi ý',condition:'Còn nguyên seal',refund:'Phương thức thanh toán gốc',targetId:'PB2'};
  for (const type of ['TRA_HANG_HOAN_TIEN','DOI_HANG']) {
    assert.equal(descriptionMin(type,'Đổi ý'),10);
    assert(validateForm(type,f,delivered,{soLuong:1},[]).sealConfirmed);
    assert(validateForm(type,{...f,sealConfirmed:'false'},delivered,{soLuong:1},[]).sealConfirmed);
    assert.deepEqual(validateForm(type,{...f,sealConfirmed:'true'},delivered,{soLuong:1},[]),{});
    for(const lyDo of ['a'.repeat(9),' '.repeat(10),'a'.repeat(1001)]) assert(validateForm(type,{...f,sealConfirmed:'true',lyDo},delivered,{soLuong:1},[]).lyDo);
  }
  assert.equal(descriptionMin('BAO_HANH','Đổi ý'),20);
  assert.equal(descriptionMin('DOI_HANG','Máy lỗi'),20);
});


test('V7 warranty shares fault choices, accepts physical damage for assessment, requires proof', async () => {
  const {toggleFault, warrantyAppearanceWarning, evidenceHint, descriptionHint} = await import('../src/utils/afterSalesForm.js');
  const fault = toggleFault(toggleFault('', 'Camera / âm thanh'), 'Pin / sạc');
  const f = {maDonHang:'DH1',maPhienBan:'PB1',lyDo:'Camera bị đen sau khi mở ứng dụng.',serialKind:'IMEI',serial:'490154203237518',appearance:'Rơi vỡ',fault};
  assert.deepEqual(validateForm('BAO_HANH',f,delivered,{},[{}]),{});
  assert.deepEqual(validateForm('BAO_HANH',{...f,appearance:'Vào nước'},delivered,{},[{}]),{});
  for (const bad of ['', 'Sai', 'Pin / sạc; Pin / sạc']) assert(validateForm('BAO_HANH',{...f,fault:bad},delivered,{},[{}]).fault);
  assert(validateForm('BAO_HANH',f,delivered,{},[]).images);
  assert.match(warrantyAppearanceWarning('Rơi vỡ'), /báo phí/);
  assert.match(warrantyAppearanceWarning('Vào nước'), /báo phí/);
  assert.equal(warrantyAppearanceWarning('Bình thường'),'');
  assert.match(descriptionHint('BAO_HANH',''), /Thường xuyên hay thỉnh thoảng/);
  assert.match(evidenceHint('BAO_HANH','',''), /IMEI/);
});
test('V7 warranty dependent validation is clean when switching device', async () => {
  const {validationAfterChange, visibleValidationErrors} = await import('../src/utils/afterSalesForm.js');
  for (const field of ['maDonHang','maPhienBan']) {
    const state = validationAfterChange({fault:'submitted',appearance:'touched',serial:'submitted'},field);
    assert.deepEqual(visibleValidationErrors({fault:'missing',appearance:'missing',serial:'missing'},state),{});
  }
});
