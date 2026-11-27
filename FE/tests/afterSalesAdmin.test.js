import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeRequests,filterRequests,countPending,formatAdminDate, isOverdue, completedToday, parseRequestText, canComplete, STATUS_LABELS } from '../src/utils/afterSalesAdmin.js';
const rows = normalizeRequests({
  returns:[{maYeuCau:'YC001',maDonHang:'DH003',tenKhach:'Nguyễn A',soDienThoai:'0901234567',loaiYeuCau:'DOI_HANG',trangThai:'CHO_DUYET',ngayTao:'2026-11-14'}],
  afterSales:[{maYeuCau:'HM001',maDonHang:'DH004',tenKhach:'Trần B',loai:'BAO_HANH',trangThai:'HOAN_TAT',ngayTao:'2026-11-13T09:30:00'}],
});
test('gộp 2 nhóm yêu cầu và giữ nguồn để gọi API xử lý đúng',()=>{assert.equal(rows.length,2);assert.equal(rows[0].source,'return');assert.equal(rows[1].source,'after');});
test('tìm mã yêu cầu, mã đơn, tên và số điện thoại',()=>{
  for (const q of ['YC001','DH003','nguyễn','0901234567']) assert.equal(filterRequests(rows,{query:q}).length,1);
});
test('lọc loại, trạng thái, ngày',()=>{
  assert.equal(filterRequests(rows,{status:'waiting',type:'DOI_HANG',from:'2026-11-14',to:'2026-11-14'}).length,1);
  assert.equal(filterRequests(rows,{status:'completed'}).length,1);
  assert.equal(filterRequests(rows,{from:'2026-11-15'}).length,0);
});
test('ưu tiên đơn chưa xử lý, sắp xếp ngày',()=>{
  assert.equal(filterRequests(rows,{sort:'priority'})[0].maYeuCau,'YC001');
  assert.equal(filterRequests(rows,{sort:'dateAsc'})[0].maYeuCau,'HM001');
  assert.equal(countPending(rows),1);
});
test('ngày giờ theo định dạng Việt Nam',()=>assert.match(formatAdminDate('2026-11-14T21:18:00'),/14\/11\/2026/));

test('thống nhất trạng thái chờ, giữ mã gốc',()=>{assert.equal(STATUS_LABELS.CHO_DUYET,STATUS_LABELS.CHO_TIEP_NHAN);assert.equal(rows[0].maYeuCau,'YC001');});
test('SLA 48 giờ chỉ tính yêu cầu chưa phản hồi',()=>{const row={trangThai:'CHO_DUYET',ngayTao:'2026-11-14',ngayTaoChinhXac:'2026-11-14T12:00:00'};const now=new Date('2026-11-16T12:00:00').getTime();assert.equal(isOverdue(row,now-1),false);assert.equal(isOverdue(row,now),true);assert.equal(isOverdue({...row,ghiChuXuLy:'Đã tiếp nhận'},now),false);assert.equal(isOverdue({...row,trangThai:'DA_DUYET'},now),false);});
test('tách nội dung form nhưng giữ nguyên mô tả nhiều dòng và payload cũ',()=>{const x=parseRequestText('Lý do: Máy lỗi\nNgân hàng: MB\nMô tả chi tiết: Lỗi pin\nNgân hàng: không phải trường');assert.equal(x.fields['Lý do'],'Máy lỗi');assert.equal(x.fields['Ngân hàng'],'MB');assert.equal(x.description,'Lỗi pin\nNgân hàng: không phải trường');assert.equal(parseRequestText('Nội dung cũ').description,'Nội dung cũ');});
test('không hoàn tất trả hàng khi thiếu nhập kho hoặc hoàn tiền',()=>{const r={source:'return',loai:'TRA_HANG_HOAN_TIEN',trangThai:'DANG_XU_LY'};assert.equal(canComplete(r),false);assert.equal(canComplete({...r,daNhapKho:true}),false);assert.equal(canComplete({...r,daNhapKho:true,ngayHoanTien:'2026-11-15'}),true);});
test('đổi sản phẩm và bảo hành phải giao/trả máy trước khi đóng',()=>{const e={source:'return',loai:'DOI_HANG',trangThai:'DANG_XU_LY',daNhapKho:true};const w={source:'after',loai:'BAO_HANH',trangThai:'DANG_XU_LY'};assert.equal(canComplete(e),false);assert.equal(canComplete(e,['EXCHANGE_HANDOVER']),true);assert.equal(canComplete(w),false);assert.equal(canComplete(w,['WARRANTY_HANDOVER']),true);});
test('thống kê hoàn tất hôm nay',()=>{assert.equal(completedToday({trangThai:'HOAN_TAT',ngayCapNhat:'2026-11-14T10:00:00'},new Date(2026,10,14)),true);});
