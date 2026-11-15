// =============================================================================
// E2E Giai doan 2B.7C — quy trinh phe duyet ban hang (Admin + Khach hang).
// CHI chay voi Backend tro vao CLONE E2E (claude_2b7x_e2e_*). Xem FE/e2e/README.md.
// Bien moi truong: E2E_ADMIN_EMAIL, E2E_ADMIN_PASSWORD, E2E_CUSTOMER_EMAIL, E2E_CUSTOMER_PASSWORD,
//                  E2E_API (mac dinh http://localhost:8080), E2E_OUT (thu muc bang chung).
// Gia dung trong test la gia GIA LAP tren clone, khong phai gia thi truong.
// =============================================================================
import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const API = process.env.E2E_API || 'http://localhost:8080';
const OUT = process.env.E2E_OUT || 'e2e-results';
const ADMIN = { email: process.env.E2E_ADMIN_EMAIL, pw: process.env.E2E_ADMIN_PASSWORD };
const KHACH = { email: process.env.E2E_CUSTOMER_EMAIL, pw: process.env.E2E_CUSTOMER_PASSWORD };
const SP = 'SP010';            // Redmi 13C (clone)
const PB = 'PB010';            // 4/128 Xanh Navy — se duoc duyet trong test
const SP_BLOCKED = 'SP500476'; // Z Flip5 — BLOCKED gia seed
const SP_NGUNG = 'SP500001';   // iPhone 11 Pro — ngung kinh doanh
const NGUON = 'Kiểm thử E2E trên clone: bảng giá giả lập, không phải giá thị trường';
const DIA_CHI = 'DC001';

const evidence = [];
const ghi = (tc, ketQua, chiTiet = {}) => evidence.push({ tc, ketQua, ...chiTiet, luc: new Date().toISOString() });
const chup = async (page, ten) => {
  const f = path.join(OUT, 'screenshots', `${ten}.png`);
  fs.mkdirSync(path.dirname(f), { recursive: true });
  await page.screenshot({ path: f, fullPage: true });
  return f;
};

let adminToken;
let khachToken;

async function login(request, who) {
  const r = await request.post(`${API}/api/auth/login`, { data: { taiKhoan: who.email, matKhau: who.pw } });
  expect(r.status()).toBe(200);
  return (await r.json()).token;
}

const h = (t) => ({ Authorization: `Bearer ${t}` });

async function chiTiet(request, ma = PB) {
  const r = await request.get(`${API}/api/admin/variants/${ma}/approval`, { headers: h(adminToken) });
  expect(r.status()).toBe(200);
  return r.json();
}

/** Doi gia qua API form san pham (GET moi -> PUT kem ma kiem tra), dung dung luong Admin that. */
async function doiGia(request, giaMoi) {
  const sp = await (await request.get(`${API}/api/admin/products/${SP}`, { headers: h(adminToken) })).json();
  for (const v of sp.phienBans) if (v.maPhienBan === PB) v.giaBan = giaMoi;
  const r = await request.put(`${API}/api/admin/products/${SP}`, { headers: h(adminToken), data: sp });
  return r.status();
}

async function duyetApi(request, d) {
  return request.put(`${API}/api/admin/variants/${d.maPhienBan}/approval`, {
    headers: h(adminToken),
    data: {
      trangThaiPheDuyet: 'APPROVED', lyDo: 'Đối chiếu giá E2E trên clone', nguonXacMinh: NGUON,
      giaPheDuyet: d.giaBan, giaNiemYetPheDuyet: d.giaNiemYet, ramPheDuyet: d.ram, boNhoPheDuyet: d.boNho,
      mauSacPheDuyet: d.mauSac, maKiemTra: d.maKiemTra,
    },
  });
}

async function loginUi(page, who) {
  await page.goto('/login');
  await page.getByLabel('Email hoặc số điện thoại').fill(who.email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(who.pw);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

async function moChiTiet(page, ma = PB) {
  await page.goto('/admin/approvals');
  await page.getByLabel('Tìm kiếm').fill(ma);
  const row = page.locator(`tr[data-ma="${ma}"]`);
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Chi tiết' }).click();
  await expect(page.getByTestId('approval-current')).toBeVisible();
}

async function dienForm(page, { nguon, lyDo }) {
  if (nguon !== undefined) await page.locator('form[data-testid^="approval-form"] textarea').first().fill(nguon);
  await page.locator('form[data-testid^="approval-form"] textarea').last().fill(lyDo);
  await page.locator('form[data-testid^="approval-form"] input[type="checkbox"]').check();
}

test.describe.configure({ mode: 'serial' });

// Moi loi JavaScript trong trang (vd. React crash) lam test that bai — khong chi kiem tra giao dien.
const loiTrang = [];
test.beforeEach(({ page }) => {
  page.on('pageerror', (e) => loiTrang.push(`${e.name}: ${e.message}`));
});
test.afterEach(() => {
  const loi = loiTrang.splice(0);
  expect(loi, 'Khong duoc co loi JavaScript tren trang').toEqual([]);
});

test.beforeAll(async ({ request }) => {
  expect(ADMIN.email && ADMIN.pw && KHACH.email && KHACH.pw, 'Thieu bien moi truong tai khoan E2E').toBeTruthy();
  adminToken = await login(request, ADMIN);
  khachToken = await login(request, KHACH);
});

test.afterAll(() => {
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, 'evidence.json'), JSON.stringify(evidence, null, 2));
});

// ------------------------------------------------------------------ lich su don hang (moc)
let donCuTruoc;
test('00 Moc: lich su don hang cua khach truoc khi chay', async ({ request }) => {
  const r = await request.get(`${API}/api/customer/orders`, { headers: h(khachToken) });
  expect(r.status()).toBe(200);
  donCuTruoc = (await r.json()).map((d) => ({ ma: d.maDonHang, tong: d.tongTien, tt: d.trangThai }));
  ghi('00', 'PASS', { soDonCu: donCuTruoc.length });
});

// ================================================================== ADMIN
test('01-02 Admin dang nhap va xem danh sach bien the', async ({ page }) => {
  await loginUi(page, ADMIN);
  await expect(page).toHaveURL(/\/admin/);
  await page.goto('/admin/approvals');
  await expect(page.getByRole('heading', { name: 'Phê duyệt bán hàng' })).toBeVisible();
  const rows = page.locator('[data-testid="approval-table"] tbody tr');
  await expect(rows.first()).toBeVisible();
  const n = await rows.count();
  ghi('01-02', 'PASS', { soDong: n, screenshot: await chup(page, '02-admin-danh-sach') });
});

test('03 Loc PENDING_REVIEW', async ({ page }) => {
  await loginUi(page, ADMIN);
  await page.goto('/admin/approvals');
  await page.getByLabel('Trạng thái phê duyệt').selectOption('PENDING_REVIEW');
  const badges = page.locator('[data-testid="approval-table"] tbody tr td:nth-child(8) .badge');
  await expect(badges.first()).toBeVisible();
  const texts = await badges.allInnerTexts();
  expect(texts.every((t) => t === 'Chờ phê duyệt')).toBeTruthy();
  await page.getByLabel('Tìm kiếm').fill(PB);
  await expect(page.locator(`tr[data-ma="${PB}"]`)).toBeVisible();
  ghi('03', 'PASS', { soDongPending: texts.length, screenshot: await chup(page, '03-loc-pending') });
});

test('04-05 Xem chi tiet; duyet thieu nguon bi tu choi (UI khoa nut + API 400)', async ({ page, request }) => {
  await loginUi(page, ADMIN);
  await moChiTiet(page);
  await expect(page.getByTestId('approval-current')).toContainText('4 GB');
  await expect(page.getByTestId('approval-current')).toContainText('Xanh Navy');
  await page.getByRole('button', { name: 'Phê duyệt…' }).click();
  await dienForm(page, { nguon: '', lyDo: 'Đã đối chiếu giá E2E trên clone' });
  await expect(page.getByRole('button', { name: 'Xác nhận' })).toBeDisabled();
  const d = await chiTiet(request);
  const r = await request.put(`${API}/api/admin/variants/${PB}/approval`, {
    headers: h(adminToken),
    data: { trangThaiPheDuyet: 'APPROVED', lyDo: 'Đã đối chiếu giá E2E trên clone', giaPheDuyet: d.giaBan,
      giaNiemYetPheDuyet: d.giaNiemYet, ramPheDuyet: d.ram, boNhoPheDuyet: d.boNho, mauSacPheDuyet: d.mauSac },
  });
  expect(r.status()).toBe(400);
  expect((await chiTiet(request)).trangThaiPheDuyet).toBe('PENDING_REVIEW');
  ghi('04-05', 'PASS', { httpThieuNguon: r.status(), thongBao: (await r.json()).message, screenshot: await chup(page, '05-thieu-nguon') });
});

test('06 Duyet du lieu hop le qua UI -> APPROVED, duoc phep ban', async ({ page, request }) => {
  await loginUi(page, ADMIN);
  await moChiTiet(page);
  await page.getByRole('button', { name: 'Phê duyệt…' }).click();
  await dienForm(page, { nguon: NGUON, lyDo: 'Đã đối chiếu giá E2E trên clone' });
  await page.getByRole('button', { name: 'Xác nhận' }).click();
  await expect(page.getByTestId('approval-current')).toContainText('Đã phê duyệt');
  const d = await chiTiet(request);
  expect(d.trangThaiPheDuyet).toBe('APPROVED');
  expect(d.duocPhepBan).toBe(true);
  ghi('06', 'PASS', { trangThai: d.trangThaiPheDuyet, duocPhepBan: d.duocPhepBan, screenshot: await chup(page, '06-da-duyet') });
});

test('07 Doi gia -> phe duyet mat hieu luc; hop thoai cu gui -> 409, khong tu gui lai', async ({ page, request }) => {
  const truoc = await chiTiet(request);
  const st = await doiGia(request, Number(truoc.giaBan) + 10000);
  expect(st).toBe(200);
  const sau = await chiTiet(request);
  expect(sau.trangThaiPheDuyet).toBe('PENDING_REVIEW');
  expect(sau.duocPhepBan).toBe(false);

  // Hop thoai mo voi du lieu A, Admin khac doi gia, gui -> 409 + nut tai lai
  await loginUi(page, ADMIN);
  await moChiTiet(page);
  await page.getByRole('button', { name: 'Phê duyệt…' }).click();
  await dienForm(page, { nguon: NGUON, lyDo: 'Đã đối chiếu giá E2E trên clone' });
  expect(await doiGia(request, Number(sau.giaBan) + 10000)).toBe(200);
  const reqs = [];
  page.on('request', (rq) => { if (rq.url().includes(`/variants/${PB}/approval`) && rq.method() === 'PUT') reqs.push(rq.url()); });
  await page.getByRole('button', { name: 'Xác nhận' }).click();
  await expect(page.getByTestId('approval-conflict')).toBeVisible();
  await page.waitForTimeout(1500);
  expect(reqs.length, 'khong tu gui lai yeu cau').toBe(1);
  expect((await chiTiet(request)).trangThaiPheDuyet).toBe('PENDING_REVIEW');
  const shot = await chup(page, '07-xung-dot-409');
  await page.getByRole('button', { name: 'Tải lại dữ liệu mới nhất' }).click();
  await expect(page.getByTestId('approval-conflict')).toHaveCount(0);
  ghi('07', 'PASS', { giaTruoc: truoc.giaBan, giaSau: sau.giaBan, trangThaiSauDoiGia: sau.trangThaiPheDuyet, soLanGuiPut: reqs.length, screenshot: shot });
});

test('08 Khoa bien the qua UI -> khong the mua (API 400)', async ({ page, request }) => {
  await loginUi(page, ADMIN);
  await moChiTiet(page);
  await page.getByRole('button', { name: 'Khóa bán…' }).click();
  await page.locator('form[data-testid="approval-form-block"] select').selectOption('QUYET_DINH_QUAN_TRI');
  await dienForm(page, { lyDo: 'Khóa tạm để kiểm thử E2E' });
  await page.getByRole('button', { name: 'Xác nhận' }).click();
  await expect(page.getByTestId('approval-current')).toContainText('Đang khóa bán');
  await expect(page.getByRole('button', { name: 'Phê duyệt…' })).toHaveCount(0);
  const r = await request.post(`${API}/api/customer/cart`, { headers: h(khachToken), data: { maPhienBan: PB, soLuong: 1 } });
  expect(r.status()).toBe(400);
  ghi('08', 'PASS', { httpThemGio: r.status(), thongBao: (await r.json()).message, screenshot: await chup(page, '08-khoa-ban') });
});

test('09 Mo khoa -> chi ve PENDING_REVIEW', async ({ page, request }) => {
  await loginUi(page, ADMIN);
  await moChiTiet(page);
  await page.getByRole('button', { name: 'Mở khóa…' }).click();
  await dienForm(page, { nguon: NGUON, lyDo: 'Đã kiểm tra lại, mở khóa E2E' });
  await page.getByRole('button', { name: 'Xác nhận' }).click();
  await expect(page.getByTestId('approval-current')).toContainText('Chờ phê duyệt');
  const d = await chiTiet(request);
  expect(d.trangThaiPheDuyet).toBe('PENDING_REVIEW');
  expect(d.duocPhepBan).toBe(false);
  ghi('09', 'PASS', { trangThai: d.trangThaiPheDuyet, screenshot: await chup(page, '09-mo-khoa') });
});

test('10 Duyet lai -> kiem tra dieu kien (thieu xac nhan bi khoa nut) roi APPROVED', async ({ page, request }) => {
  await loginUi(page, ADMIN);
  await moChiTiet(page);
  await page.getByRole('button', { name: 'Phê duyệt…' }).click();
  await page.locator('form[data-testid="approval-form-approve"] textarea').first().fill(NGUON);
  await page.locator('form[data-testid="approval-form-approve"] textarea').last().fill('Duyệt lại sau mở khóa E2E');
  await expect(page.getByRole('button', { name: 'Xác nhận' })).toBeDisabled();
  await page.locator('form[data-testid="approval-form-approve"] input[type="checkbox"]').check();
  await page.getByRole('button', { name: 'Xác nhận' }).click();
  await expect(page.getByTestId('approval-current')).toContainText('Đã phê duyệt');
  const d = await chiTiet(request);
  expect(d.duocPhepBan).toBe(true);
  ghi('10', 'PASS', { trangThai: d.trangThaiPheDuyet, giaBan: d.giaBan });
});

test('11 Lich su audit theo bien the', async ({ page, request }) => {
  await loginUi(page, ADMIN);
  await moChiTiet(page);
  const hist = page.getByTestId('approval-history');
  for (const t of ['Phê duyệt', 'Tự động hủy phê duyệt (dữ liệu đổi)', 'Khóa bán', 'Mở khóa']) {
    await expect(hist).toContainText(t);
  }
  await expect(hist).toContainText(NGUON);
  await expect(hist).toContainText('4/128GB Xanh Navy');
  await expect(hist).not.toContainText('?/?');
  const r = await request.get(`${API}/api/admin/variants/${PB}/approval-history`, { headers: h(adminToken) });
  const ds = await r.json();
  ghi('11', 'PASS', { soBanGhi: ds.length, hanhDong: ds.map((x) => x.hanhDong), screenshot: await chup(page, '11-lich-su') });
});

test('12 CUSTOMER khong truy cap duoc man hinh/API Admin', async ({ page, request }) => {
  await loginUi(page, KHACH);
  await page.goto('/admin/approvals');
  await expect(page).not.toHaveURL(/\/admin/);
  const s1 = (await request.get(`${API}/api/admin/variants`, { headers: h(khachToken) })).status();
  const s2 = (await request.get(`${API}/api/admin/variants/${PB}/approval-history`, { headers: h(khachToken) })).status();
  const s3 = (await request.get(`${API}/api/admin/variants`)).status();
  const pub = await (await request.get(`${API}/api/products/${SP}`)).json();
  const lo = JSON.stringify(pub);
  expect(s1).toBe(403);
  expect(s2).toBe(403);
  expect(s3).toBe(401);
  for (const k of ['nguonXacMinh', 'nguoiPheDuyet', 'lyDoPheDuyet', 'dauVanTayPheDuyet', 'maKiemTra']) {
    expect(lo.includes(k), `API cong khai khong duoc chua ${k}`).toBeFalsy();
  }
  ghi('12', 'PASS', { urlSauKhiVaoAdmin: page.url(), customerList: s1, customerHistory: s2, anonymous: s3 });
});

// ================================================================== KHACH HANG
let spPending;
test('13 San pham PENDING: "Gia dang cap nhat", "Tam ngung nhan don", khong phai het hang', async ({ page, request }) => {
  const ds = await (await request.get(`${API}/api/products`)).json();
  spPending = ds.find((s) => s.trangThai === 'DANG_KINH_DOANH' && s.phienBans?.length
    && s.phienBans.filter((v) => v.trangThai === 'DANG_KINH_DOANH').every((v) => v.trangThaiPheDuyet === 'PENDING_REVIEW')
    && s.phienBans.some((v) => v.soLuongTon > 0));
  expect(spPending, 'can mot san pham toan bo PENDING con ton').toBeTruthy();
  await page.goto(`/products/${spPending.maSanPham}`);
  await expect(page.locator('.price.big')).toContainText('Giá đang cập nhật');
  await expect(page.locator('.price.big')).toContainText('Tạm ngừng nhận đơn');
  await expect(page.locator('.price.big')).not.toContainText('₫');
  await expect(page.getByRole('button', { name: 'Thêm vào giỏ' }).first()).toBeDisabled();
  await expect(page.locator('body')).not.toContainText('Hết hàng');
  // 2B.7D: API cong khai khong tra truong gia cho bien the chua duoc phep ban
  const pub = await (await request.get(`${API}/api/products/${spPending.maSanPham}`)).json();
  for (const v of pub.phienBans) {
    if (!v.duocPhepBan) expect('giaBan' in v, `${v.maPhienBan} khong duoc co giaBan`).toBeFalsy();
  }
  expect('giaBan' in pub, 'SP khong co bien the duoc ban -> khong co giaBan').toBeFalsy();
  ghi('13', 'PASS', { maSanPham: spPending.maSanPham, jsonKhongCoGia: true, screenshot: await chup(page, '13-pending') });
});

test('14 San pham BLOCKED: khong kha dung, khong mua duoc', async ({ page }) => {
  await page.goto(`/products/${SP_BLOCKED}`);
  await expect(page.locator('.price.big')).toContainText('Không khả dụng');
  await expect(page.getByRole('button', { name: 'Thêm vào giỏ' }).first()).toBeDisabled();
  ghi('14', 'PASS', { screenshot: await chup(page, '14-blocked') });
});

test('15-16 Them gio / mua ngay bien the chua duyet: nut khoa + API 400', async ({ page, request }) => {
  const pv = spPending.phienBans.find((v) => v.soLuongTon > 0);
  await loginUi(page, KHACH);
  await page.goto(`/products/${spPending.maSanPham}`);
  await expect(page.getByRole('button', { name: 'Thêm vào giỏ' }).first()).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Mua ngay' }).first()).toBeDisabled();
  const add = await request.post(`${API}/api/customer/cart`, { headers: h(khachToken), data: { maPhienBan: pv.maPhienBan, soLuong: 1 } });
  const buy = await request.get(`${API}/api/customer/checkout/preview`, { headers: h(khachToken), params: { maDiaChi: DIA_CHI, maSanPham: pv.maPhienBan, soLuong: 1 } });
  expect(add.status()).toBe(400);
  expect(buy.status()).toBe(400);
  ghi('15-16', 'PASS', { bienThe: pv.maPhienBan, httpThemGio: add.status(), httpMuaNgayPreview: buy.status() });
});

test('17 Gio hang cu: localStorage FE cu (thieu duocPhepBan) va gio server bi thu hoi', async ({ page, request }) => {
  const pv = spPending.phienBans.find((v) => v.soLuongTon > 0);
  // a) Khach vang lai: anh chup cu ghi APPROVED, gia seed, khong co duocPhepBan
  await page.goto('/');
  await page.evaluate(([sp, v]) => {
    localStorage.setItem('phonenest_guest_cart', JSON.stringify([{
      sanPham: { maSanPham: sp.maSanPham, tenMay: sp.tenMay, phienBans: [] },
      phienBan: { maPhienBan: v.maPhienBan, ram: v.ram, boNho: v.boNho, mauSac: v.mauSac, giaBan: 1234567,
        soLuongTon: 9, trangThai: 'DANG_KINH_DOANH', trangThaiPheDuyet: 'APPROVED' },
      soLuong: 1,
    }]));
  }, [spPending, pv]);
  await page.goto('/cart');
  await expect(page.locator('.cart-item')).toContainText('Giá đang cập nhật');
  await expect(page.locator('body')).not.toContainText('1.234.567');
  const shotA = await chup(page, '17a-gio-cu-localstorage');
  await page.locator('.cart-item').getByRole('button', { name: 'Xóa' }).click();
  await expect(page.locator('.cart-item')).toHaveCount(0);

  // b) Gio server co bien the da duyet, sau do bi thu hoi -> checkout bi chan
  await request.delete(`${API}/api/customer/cart`, { headers: h(khachToken) });
  expect((await request.post(`${API}/api/customer/cart`, { headers: h(khachToken), data: { maPhienBan: PB, soLuong: 1 } })).status()).toBe(200);
  const d = await chiTiet(request);
  const thuHoi = await request.put(`${API}/api/admin/variants/${PB}/approval`, { headers: h(adminToken),
    data: { trangThaiPheDuyet: 'PENDING_REVIEW', lyDo: 'Thu hồi tạm để kiểm thử giỏ cũ', maKiemTra: d.maKiemTra } });
  expect(thuHoi.status()).toBe(200);
  await loginUi(page, KHACH);
  await page.goto('/checkout');
  await expect(page.getByRole('button', { name: 'Xác nhận đặt hàng' })).toBeDisabled();
  const preview = await request.get(`${API}/api/customer/checkout/preview`, { headers: h(khachToken), params: { maDiaChi: DIA_CHI } });
  expect(preview.status()).toBe(400);
  const shotB = await chup(page, '17b-checkout-gio-bi-thu-hoi');
  // duyet lai (gia lap tren clone) de chuan bi TC19, va xoa gio
  expect((await duyetApi(request, await chiTiet(request))).status()).toBe(200);
  await request.delete(`${API}/api/customer/cart`, { headers: h(khachToken) });
  ghi('17', 'PASS', { httpPreviewGioBiThuHoi: preview.status(), screenshot: [shotA, shotB] });
});

test('18 Goi truc tiep API dat hang voi bien the chua duyet -> 400, khong tao don', async ({ request }) => {
  const pv = spPending.phienBans.find((v) => v.soLuongTon > 0);
  const truoc = (await (await request.get(`${API}/api/customer/orders`, { headers: h(khachToken) })).json()).length;
  const r = await request.post(`${API}/api/customer/orders`, {
    headers: { ...h(khachToken), 'Idempotency-Key': crypto.randomUUID() },
    data: { phuongThucTt: 'COD', maDiaChi: DIA_CHI, maSanPham: pv.maPhienBan, soLuong: 1 },
  });
  expect(r.status()).toBe(400);
  const msg = (await r.json()).message;
  expect(msg, 'phai bi chan boi kiem tra phe duyet, khong phai loi khac').toMatch(/chờ phê duyệt|tạm ngừng nhận đơn/);
  const sau = (await (await request.get(`${API}/api/customer/orders`, { headers: h(khachToken) })).json()).length;
  expect(sau).toBe(truoc);
  ghi('18', 'PASS', { http: r.status(), thongBao: msg, soDonTruoc: truoc, soDonSau: sau });
});

let donMoi;
test('19-20 Mua bien the APPROVED hop le qua UI; ton kho giam dung, don hang duoc tao', async ({ page, request }) => {
  const truoc = await chiTiet(request);
  expect(truoc.duocPhepBan).toBe(true);
  await loginUi(page, KHACH);
  await page.goto(`/products/${SP}`);
  await expect(page.locator('.price.big')).not.toContainText('Giá đang cập nhật');
  await page.getByRole('button', { name: 'Mua ngay' }).first().click();
  await expect(page).toHaveURL(/\/checkout/);
  const nut = page.getByRole('button', { name: 'Xác nhận đặt hàng' });
  await expect(nut).toBeEnabled({ timeout: 15000 });
  // 2B.7D: nhan phuong thuc thanh toan phai hien va chon duoc (truoc day rong, chon -> undefined)
  await expect(page.getByTestId('pm-COD')).toContainText('Thanh toán khi nhận hàng (COD)');
  await page.getByTestId('pm-CHUYEN_KHOAN').locator('input').check();
  await page.getByTestId('pm-COD').locator('input').check();
  await expect(page.getByTestId('pm-COD').locator('input')).toBeChecked();
  const tongHienThi = Number((await page.locator('.summary .total .price, .total .price').first().innerText()).replace(/[^0-9]/g, ''));
  const shotCk = await chup(page, '19-checkout');
  await nut.click();
  await expect(page).toHaveURL(/\/orders\//, { timeout: 15000 });
  donMoi = page.url().split('/orders/')[1];
  const sau = await chiTiet(request);
  expect(sau.soLuongTon).toBe(truoc.soLuongTon - 1);
  const don = await (await request.get(`${API}/api/customer/orders/${donMoi}`, { headers: h(khachToken) })).json();
  expect(Number(don.tongTien), 'tong tien hien thi o checkout = tong tien backend luu').toBe(tongHienThi);
  ghi('19-20', 'PASS', { maDon: donMoi, tonTruoc: truoc.soLuongTon, tonSau: sau.soLuongTon, tongTien: don.tongTien, tongHienThi,
    trangThaiDon: don.trangThai, screenshot: [shotCk, await chup(page, '20-don-moi')] });
});

test('21 San pham ngung kinh doanh: khong mua duoc', async ({ page, request }) => {
  await page.goto(`/products/${SP_NGUNG}`);
  const body = page.locator('body');
  await expect(body).toContainText('ngừng kinh doanh');
  const add = page.getByRole('button', { name: 'Thêm vào giỏ' }).first();
  if (await add.count()) await expect(add).toBeDisabled();
  const sp = await (await request.get(`${API}/api/products/${SP_NGUNG}`)).json();
  const pv = sp.phienBans?.[0];
  const r = await request.post(`${API}/api/customer/cart`, { headers: h(khachToken), data: { maPhienBan: pv.maPhienBan, soLuong: 1 } });
  expect(r.status()).toBe(400);
  ghi('21', 'PASS', { httpThemGio: r.status(), screenshot: await chup(page, '21-ngung-kinh-doanh') });
});

test('22 Giao dien dien thoai (390x844): san pham PENDING, gio hang, man hinh Admin', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  const page = await ctx.newPage();
  await page.goto(`/products/${spPending.maSanPham}`);
  await expect(page.locator('.price.big')).toContainText('Giá đang cập nhật');
  const tran = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  const s1 = await chup(page, '22a-mobile-pending');
  await loginUi(page, ADMIN);
  await page.goto('/admin/approvals');
  await expect(page.getByRole('heading', { name: 'Phê duyệt bán hàng' })).toBeVisible();
  await expect(page.locator('[data-testid="approval-table"] tbody tr').first()).toBeVisible();
  const tranAdmin = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  const s2 = await chup(page, '22b-mobile-admin');
  await ctx.close();
  ghi('22', tran <= 1 ? 'PASS' : 'PASS_CO_GHI_CHU', { trangSanPhamTranNgangPx: tran, adminTranNgangPx: tranAdmin, screenshot: [s1, s2] });
});

test('99 Lich su don hang cu khong doi', async ({ request }) => {
  const ds = await (await request.get(`${API}/api/customer/orders`, { headers: h(khachToken) })).json();
  const theoMa = new Map(ds.map((d) => [d.maDonHang, d]));
  for (const c of donCuTruoc) {
    const d = theoMa.get(c.ma);
    expect(d, `don ${c.ma} van ton tai`).toBeTruthy();
    expect(Number(d.tongTien)).toBe(Number(c.tong));
    expect(d.trangThai).toBe(c.tt);
  }
  ghi('99', 'PASS', { soDonCuKiemTra: donCuTruoc.length, soDonHienTai: ds.length, donMoi });
});
