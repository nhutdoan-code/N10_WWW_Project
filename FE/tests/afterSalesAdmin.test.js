import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeRequests,filterRequests,countPending,formatAdminDate } from '../src/utils/afterSalesAdmin.js';
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
