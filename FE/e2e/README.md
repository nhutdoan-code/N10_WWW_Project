# E2E PhoneNest (Playwright)

Kiểm thử trình duyệt cho quy trình phê duyệt bán hàng (Giai đoạn 2B.7C): `approval.e2e.js` (20 test, chạy tuần tự).

- **Chỉ** chạy với backend trỏ vào clone E2E (`phonenest_e2e_*` / `claude_2b7x_e2e_*`) trên localhost.
  `start-backend-on-clone.sh` từ chối mọi `DB_URL` khác.
- Tài khoản: mật khẩu E2E ngẫu nhiên đặt **trên clone** bằng `DB/tests/prepare_e2e_clone.sh`; truyền qua
  `E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD/E2E_CUSTOMER_EMAIL/E2E_CUSTOMER_PASSWORD`. Không commit mật khẩu.
- Dùng Chrome cài sẵn (`channel: 'chrome'`), không tải trình duyệt. Đổi bằng `E2E_CHANNEL=msedge`.
- Mọi lỗi JavaScript trên trang (`pageerror`) làm test thất bại.
- Bằng chứng: `E2E_OUT` (mặc định `FE/e2e-results/`, đã `.gitignore`): `evidence.json`, `results.json`, `screenshots/`.
- Bộ test giả định trạng thái của dump `clean_acceptance` (PB010 chờ duyệt). Mỗi lần chạy nên dùng clone mới.

Hướng dẫn đầy đủ: `docs/HUONG_DAN_CAU_HINH_LOCAL.md` mục 5.4.
