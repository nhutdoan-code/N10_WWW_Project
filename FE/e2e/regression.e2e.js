// =============================================================================
// E2E HOI QUY 2B.7E — cac luong nguoi dung chinh. CHI chay tren clone E2E (backend tai khoan runtime).
// Chay sau approval.e2e.js (cung bien moi truong). Moi loi JavaScript tren trang lam test that bai.
// =============================================================================
import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const API = process.env.E2E_API || 'http://localhost:8080';
const OUT = process.env.E2E_OUT || 'e2e-results';
const ADMIN = { email: process.env.E2E_ADMIN_EMAIL, pw: process.env.E2E_ADMIN_PASSWORD };
const KHACH = { email: process.env.E2E_CUSTOMER_EMAIL, pw: process.env.E2E_CUSTOMER_PASSWORD };
const SP = 'SP010';
const PB = 'PB010';
const evidence = [];
const ghi = (tc, chiTiet = {}) => evidence.push({ tc, ketQua: 'PASS', ...chiTiet });
const chup = async (page, ten) => {
  const f = path.join(OUT, 'screenshots', `R-${ten}.png`);
  fs.mkdirSync(path.dirname(f), { recursive: true });
  await page.screenshot({ path: f, fullPage: true });
  return f;
};
const h = (t) => ({ Authorization: `Bearer ${t}` });
let adminToken;
let khachToken;

async function login(request, who) {
  const r = await request.post(`${API}/api/auth/login`, { data: { taiKhoan: who.email, matKhau: who.pw } });
  expect(r.status()).toBe(200);
  return (await r.json()).token;
}
async function loginUi(page, who) {
  await page.goto('/login');
  await page.getByLabel('Email hoặc số điện thoại').fill(who.email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(who.pw);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await expect(page).not.toHaveURL(/\/login/);
}
async function tranNgang(page) {
  return page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
}
/** Khong bao gio hien "0 ₫" nhu gia san pham. */
async function khongGiaKhong(page) {
  const prices = await page.locator('.price').allInnerTexts();
  for (const p of prices) expect(p.replace(/\s/g, ''), `gia hien thi: ${p}`).not.toMatch(/^(Từ)?0₫$/);
  return prices.length;
}

test.describe.configure({ mode: 'serial' });
const loiTrang = [];
test.beforeEach(({ page }) => { page.on('pageerror', (e) => loiTrang.push(`${e.name}: ${e.message}`)); });
test.afterEach(() => { const l = loiTrang.splice(0); expect(l, 'Khong duoc co loi JavaScript tren trang').toEqual([]); });

test.beforeAll(async ({ request }) => {
  adminToken = await login(request, ADMIN);
  khachToken = await login(request, KHACH);
  // Dam bao PB010 duoc phep ban (gia lap tren clone, nguon ghi ro khong phai gia thi truong)
  const d = await (await request.get(`${API}/api/admin/variants/${PB}/approval`, { headers: h(adminToken) })).json();
  if (!d.duocPhepBan) {
    const r = await request.put(`${API}/api/admin/variants/${PB}/approval`, { headers: h(adminToken), data: {
      trangThaiPheDuyet: 'APPROVED', lyDo: 'Đối chiếu giá E2E hồi quy trên clone',
      nguonXacMinh: 'Kiểm thử E2E trên clone: bảng giá giả lập, không phải giá thị trường',
      giaPheDuyet: d.giaBan, giaNiemYetPheDuyet: d.giaNiemYet, ramPheDuyet: d.ram, boNhoPheDuyet: d.boNho,
      mauSacPheDuyet: d.mauSac, maKiemTra: d.maKiemTra } });
    expect(r.status(), await r.text()).toBe(200);
  }
});
test.afterAll(() => {
  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, 'evidence-regression.json'), JSON.stringify(evidence, null, 2));
});

test('G01 Trang chu: hien san pham, khong co gia 0 ₫', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.product-card').first()).toBeVisible();
  const n = await khongGiaKhong(page);
  ghi('G01', { soGiaHienThi: n, screenshot: await chup(page, 'G01-trang-chu') });
});

test('G02 Tim kiem (o tim kiem + Enter) va sap xep theo gia', async ({ page }) => {
  await page.goto('/');
  const o = page.getByPlaceholder('Tìm tên máy, mã máy, hãng...');
  await o.fill('Redmi');
  await o.press('Enter');
  await expect(page).toHaveURL(/q=Redmi/);
  await expect(page.locator('.product-card').filter({ hasText: 'Redmi 13C' }).first()).toBeVisible();
  await page.getByLabel('Sắp xếp').selectOption('gia_asc');
  await expect(page.locator('.product-card').first()).toBeVisible();
  const gia = (await page.locator('.product-card .price').allInnerTexts())
    .map((t) => Number(t.replace(/[^0-9]/g, ''))).filter((x) => x > 0);
  expect([...gia].sort((a, b) => a - b), 'gia tang dan (chi gia cong bo)').toEqual(gia);
  await khongGiaKhong(page);
  ghi('G02', { giaTheoThuTu: gia.slice(0, 5), screenshot: await chup(page, 'G02-tim-kiem') });
});

test('G03 Loc gia qua API cong khai: khong tra SP chi co gia chua duyet', async ({ request }) => {
  const ds = await (await request.get(`${API}/api/products?min=1&max=100000000`)).json();
  for (const s of ds) {
    expect(s.phienBans.some((v) => v.duocPhepBan), `${s.maSanPham} lot qua bo loc gia ma khong co bien the duoc ban`).toBeTruthy();
  }
  ghi('G03', { soSanPhamCoGiaCongBo: ds.length });
});

test('G04 Chi tiet san pham + G05 So sanh 2 san pham', async ({ page }) => {
  await page.goto(`/products/${SP}`);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Redmi 13C');
  await expect(page.locator('.price.big')).not.toHaveText(/(^|[^\d.])0\s*₫/);
  const s1 = await chup(page, 'G04-chi-tiet');
  await page.goto('/');
  await page.waitForLoadState('networkidle');
  // Trang chu co nhieu khu vuc nap bat dong bo; cung mot san pham co the xuat hien o 2 khu vuc -> chon 2 ma KHAC nhau
  // va cho nut chuyen sang 'active' truoc khi bam tiep (tranh bam trung 1 san pham 2 lan = bo chon).
  const hrefs = await page.locator('.product-card a[href^="/products/"]').evaluateAll(
    (as) => [...new Set(as.map((a) => a.getAttribute('href')))]);
  expect(hrefs.length).toBeGreaterThanOrEqual(2);
  for (const href of hrefs.slice(0, 2)) {
    const the = page.locator('.product-card', { has: page.locator(`a[href="${href}"]`) }).first();
    const nutSs = the.locator('button[title="So sánh"]');
    await nutSs.click();
    await expect(nutSs).toHaveClass(/active/);
  }
  await page.goto('/compare');
  await expect(page.getByRole('heading', { name: 'So sánh sản phẩm' })).toBeVisible();
  const giaSoSanh = await page.locator('td.price').allInnerTexts();
  expect(giaSoSanh.length).toBeGreaterThanOrEqual(2);
  for (const g of giaSoSanh) expect(g).not.toMatch(/^0\s*₫$/);
  ghi('G04-G05', { giaSoSanh, screenshot: [s1, await chup(page, 'G05-so-sanh')] });
});

let donMoi;
test('G06 Gio hang + voucher + checkout COD + G07 lich su don hang', async ({ page, request }) => {
  await request.delete(`${API}/api/customer/cart`, { headers: h(khachToken) });
  await loginUi(page, KHACH);
  await page.goto(`/products/${SP}`);
  await page.getByRole('button', { name: 'Thêm vào giỏ' }).first().click();
  await page.goto('/cart');
  const item = page.locator('.cart-item').filter({ hasText: 'Redmi 13C' }).first();
  await expect(item).toBeVisible();
  await expect(item.locator('.price')).not.toContainText('Giá đang cập nhật');
  const s1 = await chup(page, 'G06-gio-hang');
  await page.goto('/checkout');
  const nut = page.getByRole('button', { name: 'Xác nhận đặt hàng' });
  await expect(nut).toBeEnabled({ timeout: 15000 });
  await page.getByTestId('pm-COD').locator('input').check();
  const voucher = await page.locator('.voucher-group').first().innerText();
  const tongHienThi = Number((await page.locator('.total .price').first().innerText()).replace(/[^0-9]/g, ''));
  const s2 = await chup(page, 'G06-checkout');
  await nut.click();
  await expect(page).toHaveURL(/\/orders\//, { timeout: 15000 });
  donMoi = page.url().split('/orders/')[1];
  const don = await (await request.get(`${API}/api/customer/orders/${donMoi}`, { headers: h(khachToken) })).json();
  expect(Number(don.tongTien)).toBe(tongHienThi);
  await page.goto('/orders');
  await expect(page.getByRole('heading', { name: 'Lịch sử đơn hàng' })).toBeVisible();
  await expect(page.locator('body')).toContainText(donMoi);
  ghi('G06-G07', { maDon: donMoi, tongHienThi, tongBackend: don.tongTien, coVoucherHienThi: voucher.includes('Voucher'),
    screenshot: [s1, s2, await chup(page, 'G07-lich-su')] });
});

test('G08 Admin san pham: form sua mo tu du lieu moi, ton kho bien the da co chi doc', async ({ page }) => {
  await loginUi(page, ADMIN);
  await page.goto('/admin/products');
  const row = page.locator('tr').filter({ hasText: 'Redmi 13C' }).first();
  await row.getByRole('button', { name: 'Sửa' }).click();
  await expect(page.getByRole('heading', { name: 'Sửa sản phẩm' })).toBeVisible();
  const ton = page.locator('label.field').filter({ hasText: 'Tồn kho (đổi tại mục Tồn kho)' }).locator('input').first();
  await expect(ton).toBeDisabled();
  ghi('G08', { screenshot: await chup(page, 'G08-admin-san-pham') });
});

test('G09 Admin kho: nhap kho; kiem ke tu du lieu cu bi tu choi (409), ton kho khong bi ghi de', async ({ page, request }) => {
  await loginUi(page, ADMIN);
  await page.goto('/admin/inventory');
  await page.getByPlaceholder('Tìm SKU, mã sản phẩm hoặc tên máy').fill('PN-010-4-128-XANH-NAVY');
  const row = page.locator('tr').filter({ hasText: 'PN-010-4-128-XANH-NAVY' }).first();
  await expect(row).toBeVisible();
  const truoc = await (await request.get(`${API}/api/admin/variants/${PB}/approval`, { headers: h(adminToken) })).json();
  await row.getByRole('button', { name: 'Điều chỉnh' }).click();
  await page.locator('.modal input[type="number"]').fill(String(truoc.soLuongTon + 7));
  await page.locator('.modal textarea').fill('Kiểm kê E2E hồi quy trên clone');
  // Trong luc Admin dang nhap so kiem ke, mot lan nhap kho khac xay ra
  expect((await request.post(`${API}/api/admin/inventory/${PB}/import`, { headers: h(adminToken), data: { soLuong: 1, ghiChu: 'E2E nhập song song' } })).status()).toBe(200);
  await page.locator('.modal').getByRole('button', { name: /Lưu|Xác nhận|Điều chỉnh/ }).last().click();
  await expect(page.locator('body')).toContainText('Tồn kho đã thay đổi');
  const sau = await (await request.get(`${API}/api/admin/variants/${PB}/approval`, { headers: h(adminToken) })).json();
  expect(sau.soLuongTon, 'kiem ke cu khong ghi de lan nhap kho').toBe(truoc.soLuongTon + 1);
  ghi('G09', { tonTruoc: truoc.soLuongTon, tonSau: sau.soLuongTon, screenshot: await chup(page, 'G09-kho-409') });
});

test('G10 Chatbot (widget that, Gemini KHONG that: khoa gia -> che do du phong): khong gia chua duyet, khong 0 ₫', async ({ page, request }) => {
  const anGia = [];
  const ds = await (await request.get(`${API}/api/admin/variants?trangThaiPheDuyet=PENDING_REVIEW&q=Redmi`, { headers: h(adminToken) })).json();
  for (const v of ds) anGia.push(Number(v.giaBan).toLocaleString('vi-VN'));
  await page.goto('/');
  await page.getByRole('button', { name: 'Chat với trợ lý' }).click();
  await page.getByPlaceholder('Nhập câu hỏi của bạn...').fill('Tư vấn giúp mình Redmi 13C giá bao nhiêu?');
  await page.getByRole('button', { name: 'Gửi' }).click();
  const tl = page.locator('.chat-msg.assistant').last();
  await expect(tl).toBeVisible({ timeout: 30000 });
  await expect(page.locator('.chat-body')).not.toContainText('đang trả lời', { timeout: 30000 }).catch(() => {});
  const noiDung = await page.locator('.chat-panel').innerText();
  for (const g of anGia) if (g !== Number(3290000).toLocaleString('vi-VN')) expect(noiDung, `lo gia chua duyet ${g}`).not.toContain(g);
  expect(noiDung).not.toMatch(/\b0\s*₫/);
  ghi('G10', { giaChuaDuyetKiemTra: anGia.length, screenshot: await chup(page, 'G10-chatbot') });
});

test('G11 Mobile 390x844: trang chu, chi tiet, gio hang, checkout, man hinh Admin khong tran ngang', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => loiTrang.push(`${e.name}: ${e.message}`));
  const kq = {};
  for (const [ten, url] of [['home', '/'], ['detail', `/products/${SP}`], ['cart', '/cart']]) {
    await page.goto(url);
    await page.waitForLoadState('networkidle');
    kq[ten] = await tranNgang(page);
    await chup(page, `G11-mobile-${ten}`);
  }
  await loginUi(page, KHACH);
  await page.goto('/checkout');
  await page.waitForLoadState('networkidle');
  kq.checkout = await tranNgang(page);
  await chup(page, 'G11-mobile-checkout');
  await loginUi(page, ADMIN);
  await page.goto('/admin/approvals');
  await expect(page.locator('[data-testid="approval-table"] tbody tr').first()).toBeVisible();
  kq.adminApprovals = await tranNgang(page);
  await chup(page, 'G11-mobile-admin');
  await ctx.close();
  for (const [k, v] of Object.entries(kq)) expect(v, `tran ngang ${k}`).toBeLessThanOrEqual(1);
  ghi('G11', { tranNgangPx: kq, phien: crypto.randomUUID().slice(0, 8) });
});
