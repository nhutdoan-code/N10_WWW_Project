import test from 'node:test';
import assert from 'node:assert/strict';
import {
  isProductDiscontinued,
  isVariantDiscontinued,
  isItemDiscontinued,
  hasDiscontinuedItems,
  isPurchasable,
  getDefaultSelectedVariant,
  canProceedToCheckout,
  isItemUnapproved,
  DISCONTINUED_MESSAGES,
} from '../src/utils/discontinuedProductRules.js';
import { productStatus } from '../src/utils/stock.js';

// MOCK DATA BASED ON REAL DATABASE (bandienthoai) & PHASE 2B REQUIREMENTS
// 1. Galaxy A56 5G (SP1793202488339002) - Active product
const galaxyA56 = {
  maSanPham: 'SP1793202488339002',
  tenMay: 'Samsung Galaxy A56 5G',
  trangThai: 'DANG_KINH_DOANH',
  giaBan: 9490000,
  soLuongTon: 45,
  phienBans: [
    {
      maPhienBan: 'PB1793202488339002',
      ram: 8,
      boNho: 128,
      mauSac: 'Đen',
      giaBan: 9490000,
      soLuongTon: 25,
      trangThai: 'DANG_KINH_DOANH',
      trangThaiPheDuyet: 'APPROVED', // 2B.7B: phải ghi rõ, thiếu trường = không bán
      duocPhepBan: true,
    },
    {
      maPhienBan: 'PB1793202488339003',
      ram: 8,
      boNho: 256,
      mauSac: 'Bạc',
      giaBan: 10490000,
      soLuongTon: 20,
      trangThai: 'DANG_KINH_DOANH',
      trangThaiPheDuyet: 'APPROVED', // 2B.7B: phải ghi rõ, thiếu trường = không bán
      duocPhepBan: true,
    },
  ],
};

// 2. vivo Y12s (SP500500) - Discontinued product per Decision 1B & 2B
const vivoY12s = {
  maSanPham: 'SP500500',
  tenMay: 'vivo Y12s',
  trangThai: 'NGUNG_KINH_DOANH',
  giaBan: 3290000,
  soLuongTon: 15,
  phienBans: [
    {
      maPhienBan: 'PB500500',
      ram: 3,
      boNho: 32,
      mauSac: 'Xanh băng tuyết',
      giaBan: 3290000,
      soLuongTon: 15,
      trangThai: 'NGUNG_KINH_DOANH',
    },
  ],
};

// 3. Active product with one discontinued variant
const iphone15Pro = {
  maSanPham: 'SP001',
  tenMay: 'iPhone 15 Pro',
  trangThai: 'DANG_KINH_DOANH',
  giaBan: 25990000,
  soLuongTon: 30,
  phienBans: [
    {
      maPhienBan: 'PB001_ACTIVE_128',
      ram: 8,
      boNho: 128,
      mauSac: 'Titan Tự Nhiên',
      giaBan: 25990000,
      soLuongTon: 20,
      trangThai: 'DANG_KINH_DOANH',
      trangThaiPheDuyet: 'APPROVED', // 2B.7B: phải ghi rõ, thiếu trường = không bán
      duocPhepBan: true,
    },
    {
      maPhienBan: 'PB001_DISCONTINUED_1TB',
      ram: 8,
      boNho: 1024,
      mauSac: 'Titan Xanh',
      giaBan: 35990000,
      soLuongTon: 10,
      trangThai: 'NGUNG_KINH_DOANH', // Phiên bản dung lượng cao đã ngừng bán
    },
  ],
};

// TEST CASE 1: Active product detail page - fully purchasable
test('TC1: Chi tiết sản phẩm đang kinh doanh - hiển thị đầy đủ, mua được', () => {
  assert.equal(isProductDiscontinued(galaxyA56), false);
  const selectedVariant = galaxyA56.phienBans[0];
  assert.equal(isVariantDiscontinued(selectedVariant), false);
  assert.equal(isPurchasable(galaxyA56, selectedVariant), true);
  assert.equal(productStatus(galaxyA56), 'CON_HANG');
});

// TEST CASE 2: Discontinued product detail page - shows details, reference price, warning banner
test('TC2: Chi tiết sản phẩm ngừng kinh doanh - hiển thị thông tin, banner cảnh báo, giá tham khảo', () => {
  assert.equal(isProductDiscontinued(vivoY12s), true);
  assert.equal(productStatus(vivoY12s), 'NGUNG_KINH_DOANH');
  assert.equal(
    DISCONTINUED_MESSAGES.DETAIL_BANNER,
    'Sản phẩm đã ngừng kinh doanh. Bạn có thể tham khảo các dòng sản phẩm mới hơn.'
  );
  assert.equal(DISCONTINUED_MESSAGES.PRICE_NOTE, '(Giá tham khảo - Ngừng bán)');
  // Vẫn lấy được variant đầu tiên để hiển thị thông số và ảnh minh họa
  const defaultVar = getDefaultSelectedVariant(vivoY12s);
  assert.ok(defaultVar);
  assert.equal(defaultVar.maPhienBan, 'PB500500');
});

// TEST CASE 3: Discontinued product buy box - disabled purchase actions
test('TC3: Chi tiết sản phẩm ngừng kinh doanh - vô hiệu hóa nút mua ngay và thêm vào giỏ', () => {
  const variant = vivoY12s.phienBans[0];
  // Dù variant còn tồn kho = 15, isPurchasable PHẢI trả về false vì sp/pv đã ngừng kinh doanh
  assert.equal(variant.soLuongTon > 0, true);
  assert.equal(isPurchasable(vivoY12s, variant), false);
});

// TEST CASE 4: Active product with discontinued variant
test('TC4: Sản phẩm có biến thể ngừng kinh doanh - biến thể ngừng kinh doanh bị vô hiệu hóa', () => {
  const activeVar = iphone15Pro.phienBans[0];
  const discontinuedVar = iphone15Pro.phienBans[1];

  assert.equal(isPurchasable(iphone15Pro, activeVar), true);
  assert.equal(isVariantDiscontinued(discontinuedVar), true);
  assert.equal(isPurchasable(iphone15Pro, discontinuedVar), false);

  // Default selected variant không được rơi vào variant đã ngừng kinh doanh
  const selected = getDefaultSelectedVariant(iphone15Pro);
  assert.equal(selected.maPhienBan, activeVar.maPhienBan);
  assert.notEqual(selected.maPhienBan, discontinuedVar.maPhienBan);
});

// TEST CASE 5: Cart page - detect cart with discontinued items (e.g. GH002 with vivo Y12s)
test('TC5: Giỏ hàng có sản phẩm ngừng kinh doanh (GH002) - hiển thị nhãn và cảnh báo', () => {
  const cartGH002 = [
    {
      sanPham: galaxyA56,
      phienBan: galaxyA56.phienBans[0],
      soLuong: 1,
    },
    {
      sanPham: vivoY12s,
      phienBan: vivoY12s.phienBans[0],
      soLuong: 2,
    },
  ];

  assert.equal(isItemDiscontinued(cartGH002[0]), false);
  assert.equal(isItemDiscontinued(cartGH002[1]), true);
  assert.equal(hasDiscontinuedItems(cartGH002), true);
  assert.equal(
    DISCONTINUED_MESSAGES.CART_ITEM_WARNING,
    'Sản phẩm này đã ngừng kinh doanh và không thể thanh toán. Vui lòng xóa khỏi giỏ hàng.'
  );
});

// TEST CASE 6: Cart quantity control locked for discontinued item
test('TC6: Khóa/vô hiệu hóa chỉnh sửa số lượng của sản phẩm ngừng kinh doanh trong giỏ', () => {
  const discontinuedItem = {
    sanPham: vivoY12s,
    phienBan: vivoY12s.phienBans[0],
    soLuong: 1,
  };
  const isLocked = isItemDiscontinued(discontinuedItem);
  assert.equal(isLocked, true);
});

// TEST CASE 7: Discontinued item retains active delete action
test('TC7: Dòng sản phẩm ngừng kinh doanh vẫn cho phép xóa khỏi giỏ hàng', () => {
  let cart = [
    { sanPham: galaxyA56, phienBan: galaxyA56.phienBans[0], soLuong: 1 },
    { sanPham: vivoY12s, phienBan: vivoY12s.phienBans[0], soLuong: 1 },
  ];

  // Giả lập hành động xóa item ngừng kinh doanh
  const itemToRemove = cart[1];
  cart = cart.filter((i) => i.phienBan.maPhienBan !== itemToRemove.phienBan.maPhienBan);

  assert.equal(cart.length, 1);
  assert.equal(cart[0].sanPham.maSanPham, 'SP1793202488339002');
  assert.equal(hasDiscontinuedItems(cart), false);
});

// TEST CASE 8: Cart checkout button enabled/disabled transition
test('TC8: Giỏ hàng vô hiệu hóa nút thanh toán khi có sản phẩm ngừng KD và kích hoạt lại sau khi xóa', () => {
  const cartWithDiscontinued = [
    { sanPham: galaxyA56, phienBan: galaxyA56.phienBans[0], soLuong: 1 },
    { sanPham: vivoY12s, phienBan: vivoY12s.phienBans[0], soLuong: 1 },
  ];

  // Khi còn sản phẩm ngừng kinh doanh: nút thanh toán bị vô hiệu hóa
  assert.equal(canProceedToCheckout(cartWithDiscontinued, null), false);

  // Sau khi xóa sản phẩm ngừng kinh doanh:
  const cartClean = cartWithDiscontinued.filter((i) => !isItemDiscontinued(i));
  assert.equal(canProceedToCheckout(cartClean, null), true);
});

// TEST CASE 9: Checkout page blocks discontinued product orders and displays Vietnamese notice
test('TC9: Trang thanh toán chặn hoàn toàn việc đặt hàng khi giỏ còn sản phẩm ngừng kinh doanh', () => {
  const cartWithDiscontinued = [
    { sanPham: vivoY12s, phienBan: vivoY12s.phienBans[0], soLuong: 1 },
  ];
  const backendError = 'Sản phẩm vivo Y12s (SP500500) đã ngừng kinh doanh, không thể đặt hàng';

  // Preview API trả về lỗi 400 Bad Request
  assert.equal(canProceedToCheckout(cartWithDiscontinued, backendError), false);
  assert.equal(hasDiscontinuedItems(cartWithDiscontinued), true);
  assert.equal(
    DISCONTINUED_MESSAGES.CHECKOUT_BANNER,
    'Đơn hàng của bạn chứa sản phẩm đã ngừng kinh doanh. Vui lòng quay lại giỏ hàng để cập nhật.'
  );
});

// TEST CASE 10: Normal active product checkout flow remains unimpaired
test('TC10: Luồng thanh toán sản phẩm đang kinh doanh bình thường hoạt động chuẩn xác', () => {
  const normalCart = [
    { sanPham: galaxyA56, phienBan: galaxyA56.phienBans[0], soLuong: 1 },
  ];
  assert.equal(hasDiscontinuedItems(normalCart), false);
  assert.equal(canProceedToCheckout(normalCart, null), true);
});

// ==========================================
// PHASE 2B.6D: SALES APPROVAL STATUS TESTS
// ==========================================

const vivoY100_Seed = {
  maSanPham: 'SP500488',
  tenMay: 'vivo Y100 5G',
  trangThai: 'DANG_KINH_DOANH',
  giaBan: 1990000,
  soLuongTon: 15,
  phienBans: [
    {
      maPhienBan: 'PB500488',
      ram: 8,
      boNho: 128,
      mauSac: 'Đen kim cương',
      giaBan: 1990000,
      soLuongTon: 15,
      trangThai: 'DANG_KINH_DOANH',
      trangThaiPheDuyet: 'BLOCKED', // Bị khóa bán do giá seed 1.99M
    },
  ],
};

const galaxyZFlip5_Seed = {
  maSanPham: 'SP500476',
  tenMay: 'Samsung Galaxy Z Flip5',
  trangThai: 'DANG_KINH_DOANH',
  giaBan: 1990000,
  soLuongTon: 10,
  phienBans: [
    {
      maPhienBan: 'PB500476',
      ram: 8,
      boNho: 256,
      mauSac: 'Xám Indie',
      giaBan: 1990000,
      soLuongTon: 10,
      trangThai: 'DANG_KINH_DOANH',
      trangThaiPheDuyet: 'BLOCKED', // Bị khóa bán do giá seed 1.99M
    },
  ],
};

const oppoReno11_Pending = {
  maSanPham: 'SP500495',
  tenMay: 'OPPO Reno11 F 5G',
  trangThai: 'DANG_KINH_DOANH',
  giaBan: 8990000,
  soLuongTon: 20,
  phienBans: [
    {
      maPhienBan: 'PB500495',
      ram: 8,
      boNho: 256,
      mauSac: 'Xanh dương',
      giaBan: 8990000,
      soLuongTon: 20,
      trangThai: 'DANG_KINH_DOANH',
      trangThaiPheDuyet: 'PENDING_REVIEW', // Đang chờ duyệt giá
    },
  ],
};

const galaxyA56_Approved = {
  maSanPham: 'SP1793202488339002',
  tenMay: 'Samsung Galaxy A56 5G',
  trangThai: 'DANG_KINH_DOANH',
  giaBan: 9490000,
  soLuongTon: 45,
  phienBans: [
    {
      maPhienBan: 'PB1793202488339002',
      ram: 8,
      boNho: 128,
      mauSac: 'Đen',
      giaBan: 9490000,
      soLuongTon: 25,
      trangThai: 'DANG_KINH_DOANH',
      trangThaiPheDuyet: 'APPROVED',
      duocPhepBan: true,
    },
    {
      maPhienBan: 'PB1793202488339003',
      ram: 8,
      boNho: 256,
      mauSac: 'Bạc',
      giaBan: 10490000,
      soLuongTon: 20,
      trangThai: 'DANG_KINH_DOANH',
      trangThaiPheDuyet: 'APPROVED',
      duocPhepBan: true,
    },
  ],
};

test('TC11: Biến thể APPROVED và còn tồn kho được phép mua', () => {
  const pv = galaxyA56_Approved.phienBans[0];
  assert.equal(isPurchasable(galaxyA56_Approved, pv), true);
});

test('TC12: Biến thể PENDING_REVIEW (OPPO Reno11 F) bị chặn mua dù còn tồn kho > 0', () => {
  const pv = oppoReno11_Pending.phienBans[0];
  assert.equal(pv.soLuongTon > 0, true);
  assert.equal(pv.trangThaiPheDuyet, 'PENDING_REVIEW');
  assert.equal(isPurchasable(oppoReno11_Pending, pv), false);
});

test('TC13: Biến thể BLOCKED (vivo Y100 5G & Galaxy Z Flip5 giá 1.99M) bị chặn mua tuyệt đối', () => {
  const pvVivo = vivoY100_Seed.phienBans[0];
  assert.equal(pvVivo.soLuongTon > 0, true);
  assert.equal(pvVivo.trangThaiPheDuyet, 'BLOCKED');
  assert.equal(isPurchasable(vivoY100_Seed, pvVivo), false);

  const pvFlip = galaxyZFlip5_Seed.phienBans[0];
  assert.equal(pvFlip.soLuongTon > 0, true);
  assert.equal(pvFlip.trangThaiPheDuyet, 'BLOCKED');
  assert.equal(isPurchasable(galaxyZFlip5_Seed, pvFlip), false);
});

test('TC14: Giỏ hàng có biến thể PENDING_REVIEW hoặc BLOCKED bị chặn thanh toán', () => {
  const cartWithPending = [
    { sanPham: galaxyA56_Approved, phienBan: galaxyA56_Approved.phienBans[0], soLuong: 1 },
    { sanPham: oppoReno11_Pending, phienBan: oppoReno11_Pending.phienBans[0], soLuong: 1 },
  ];
  assert.equal(canProceedToCheckout(cartWithPending, null), false);

  const cartWithBlocked = [
    { sanPham: galaxyA56_Approved, phienBan: galaxyA56_Approved.phienBans[0], soLuong: 1 },
    { sanPham: vivoY100_Seed, phienBan: vivoY100_Seed.phienBans[0], soLuong: 1 },
  ];
  assert.equal(canProceedToCheckout(cartWithBlocked, null), false);

  // Sau khi xóa biến thể chưa duyệt, giỏ hàng hợp lệ để thanh toán
  const cleanCart = cartWithPending.filter((i) => i.phienBan.trangThaiPheDuyet === 'APPROVED');
  assert.equal(canProceedToCheckout(cleanCart, null), true);
});

test('TC15: getDefaultSelectedVariant ưu tiên chọn biến thể APPROVED còn hàng', () => {
  const mixedProduct = {
    maSanPham: 'SP_MIXED',
    tenMay: 'Mixed Phone',
    trangThai: 'DANG_KINH_DOANH',
    phienBans: [
      { maPhienBan: 'PB_PENDING', trangThai: 'DANG_KINH_DOANH', trangThaiPheDuyet: 'PENDING_REVIEW', soLuongTon: 10 },
      { maPhienBan: 'PB_APPROVED', trangThai: 'DANG_KINH_DOANH', trangThaiPheDuyet: 'APPROVED', duocPhepBan: true, soLuongTon: 5 },
    ],
  };
  const selected = getDefaultSelectedVariant(mixedProduct);
  assert.equal(selected.maPhienBan, 'PB_APPROVED');
});

// ---------------------------------------------------------------------------
// Giai đoạn 2B.7B — TC22: thiếu/không rõ trạng thái phê duyệt phải KHÓA mua (fail-closed)
// ---------------------------------------------------------------------------
test('TC22: Thiếu trạng thái phê duyệt hoặc duocPhepBan thì không được mua', () => {
  const sp = { maSanPham: 'SP_X', trangThai: 'DANG_KINH_DOANH' };
  const base = { maPhienBan: 'PB_X', trangThai: 'DANG_KINH_DOANH', soLuongTon: 10 };
  assert.equal(isPurchasable(sp, { ...base }), false, 'thiếu cả hai trường');
  assert.equal(isPurchasable(sp, { ...base, trangThaiPheDuyet: 'APPROVED' }), false, 'APPROVED nhưng thiếu duocPhepBan');
  assert.equal(isPurchasable(sp, { ...base, trangThaiPheDuyet: 'APPROVED', duocPhepBan: false }), false, 'phê duyệt cũ hết hiệu lực');
  assert.equal(isPurchasable(sp, { ...base, trangThaiPheDuyet: 'APPROVED', duocPhepBan: 'true' }), false, 'giá trị không phải boolean');
  assert.equal(isPurchasable(sp, { ...base, trangThaiPheDuyet: 'XYZ', duocPhepBan: true }), false, 'trạng thái lạ');
  assert.equal(isPurchasable(sp, { ...base, trangThaiPheDuyet: 'APPROVED', duocPhepBan: true }), true, 'hợp lệ');

  const itemThieu = { sanPham: sp, phienBan: { ...base } };
  assert.equal(isItemUnapproved(itemThieu), true);
  assert.equal(canProceedToCheckout([itemThieu], null), false);

  const chon = getDefaultSelectedVariant({
    ...sp,
    phienBans: [
      { ...base, maPhienBan: 'PB_UNKNOWN', soLuongTon: 50 },
      { ...base, maPhienBan: 'PB_OK', trangThaiPheDuyet: 'APPROVED', duocPhepBan: true, soLuongTon: 1 },
    ],
  });
  assert.equal(chon.maPhienBan, 'PB_OK');
});

