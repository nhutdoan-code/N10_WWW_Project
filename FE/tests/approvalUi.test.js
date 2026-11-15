// Giai doan 2B.7C: nhan trang thai Admin, gia hien thi cho khach, lam moi gio hang localStorage cu.
import test from 'node:test';
import assert from 'node:assert/strict';
import { approvalView, cardPricing } from '../src/utils/approvalUi.js';
import { refreshGuestItems } from '../src/utils/guestCart.js';
import { canProceedToCheckout } from '../src/utils/discontinuedProductRules.js';

const pv = (o) => ({ maPhienBan: 'PB1', trangThai: 'DANG_KINH_DOANH', soLuongTon: 5, giaBan: 1000, ...o });

test('C-UI1 APPROVED nhung dau van tay lech -> canh bao rieng, khong san sang ban', () => {
  const v = approvalView({ trangThaiPheDuyet: 'APPROVED', duocPhepBan: false, canhBaoPheDuyetLech: true });
  assert.equal(v.cls, 'approval-warn');
  assert.equal(v.sanSang, false);
  assert.equal(approvalView({ trangThaiPheDuyet: 'APPROVED', duocPhepBan: true }).sanSang, true);
  assert.equal(approvalView({ trangThaiPheDuyet: 'APPROVED' }).sanSang, false, 'thieu duocPhepBan -> khong san sang');
  assert.equal(approvalView({ trangThaiPheDuyet: 'PENDING_REVIEW' }).label, 'Chờ phê duyệt');
  assert.equal(approvalView({ trangThaiPheDuyet: 'BLOCKED' }).label, 'Đang khóa bán');
});

test('C-UI2 the san pham: tat ca chua duyet -> "Gia dang cap nhat", khong phai het hang', () => {
  const r = cardPricing({ trangThai: 'DANG_KINH_DOANH', phienBans: [pv({ trangThaiPheDuyet: 'PENDING_REVIEW', giaBan: 1990000 })] });
  assert.equal(r.allPending, true);
  assert.equal(r.hetHang, false);
  assert.equal(r.gia, null, 'khong lo gia seed');
});

test('C-UI3 the san pham: tron APPROVED + PENDING -> chi dung gia bien the da duyet', () => {
  const r = cardPricing({
    trangThai: 'DANG_KINH_DOANH',
    phienBans: [
      pv({ maPhienBan: 'A', trangThaiPheDuyet: 'APPROVED', duocPhepBan: true, giaBan: 5000000 }),
      pv({ maPhienBan: 'B', trangThaiPheDuyet: 'PENDING_REVIEW', giaBan: 1990000 }),
    ],
  });
  assert.equal(r.gia, 5000000);
  assert.equal(r.nhieuGia, false);
  assert.equal(r.allPending, false);
});

test('C-UI4 the san pham: tat ca BLOCKED -> khong kha dung; APPROVED het kho -> het hang', () => {
  assert.equal(cardPricing({ trangThai: 'DANG_KINH_DOANH', phienBans: [pv({ trangThaiPheDuyet: 'BLOCKED' })] }).allBlocked, true);
  const r = cardPricing({ trangThai: 'DANG_KINH_DOANH', phienBans: [pv({ trangThaiPheDuyet: 'APPROVED', duocPhepBan: true, soLuongTon: 0 })] });
  assert.equal(r.hetHang, true);
});

test('C-UI5 gio hang localStorage tu FE cu (thieu duocPhepBan) duoc xac minh lai tu Backend', async () => {
  const cu = [
    { sanPham: { maSanPham: 'SP1' }, phienBan: { maPhienBan: 'PB1', trangThaiPheDuyet: 'APPROVED', giaBan: 1 }, soLuong: 1 },
    { sanPham: { maSanPham: 'SP2' }, phienBan: { maPhienBan: 'PB2', trangThaiPheDuyet: 'APPROVED', giaBan: 1 }, soLuong: 2 },
    { sanPham: { maSanPham: 'SP3' }, phienBan: { maPhienBan: 'PB3' }, soLuong: 1 },
  ];
  const backend = {
    SP1: { maSanPham: 'SP1', trangThai: 'DANG_KINH_DOANH', phienBans: [pv({ maPhienBan: 'PB1', trangThaiPheDuyet: 'APPROVED', duocPhepBan: true, giaBan: 7000000 })] },
    SP2: { maSanPham: 'SP2', trangThai: 'DANG_KINH_DOANH', phienBans: [pv({ maPhienBan: 'PB2', trangThaiPheDuyet: 'PENDING_REVIEW', duocPhepBan: false })] },
  };
  const fetch = async (ma) => { if (!backend[ma]) throw new Error('404'); return backend[ma]; };
  const moi = await refreshGuestItems(cu, fetch);
  assert.equal(moi.length, 3, 'khong tu xoa dong nao - khach van tu xoa duoc');
  assert.equal(moi[0].phienBan.duocPhepBan, true);
  assert.equal(moi[0].phienBan.giaBan, 7000000, 'gia lay tu Backend, khong lay ban chup cu');
  assert.equal(moi[1].phienBan.duocPhepBan, false);
  assert.equal(moi[2].phienBan.duocPhepBan, false, 'khong xac minh duoc -> fail-closed');
  assert.equal(moi[2].khongXacMinh, true);
  assert.equal(canProceedToCheckout(moi, null), false);
  assert.equal(canProceedToCheckout([moi[0]], null), true);
});

// 2B.7D: backend khong tra truong gia cho bien the chua duoc phep ban -> FE khong bao gio hien "0 ₫"
import { formatGiaCongBo } from '../src/utils/format.js';

test('C-UI6 formatGiaCongBo: thieu gia -> "Gia dang cap nhat", khong phai 0', () => {
  assert.equal(formatGiaCongBo(undefined), 'Giá đang cập nhật');
  assert.equal(formatGiaCongBo(null), 'Giá đang cập nhật');
  assert.ok(formatGiaCongBo(3290000).includes('3.290.000'));
  assert.ok(!formatGiaCongBo(undefined).includes('0 ₫'));
});

test('C-UI7 the san pham: API khong tra giaBan cho bien the chua duyet -> khong hien gia', () => {
  const r = cardPricing({ trangThai: 'DANG_KINH_DOANH', phienBans: [pv({ trangThaiPheDuyet: 'PENDING_REVIEW', giaBan: undefined, duocPhepBan: false })] });
  assert.equal(r.gia, null);
  assert.equal(r.allPending, true);
});
