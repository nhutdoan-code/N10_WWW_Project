# Kế hoạch chuyển sang tài khoản DB ít quyền nhất và thay secret đã lộ (2B.7E)

**Trạng thái: KẾ HOẠCH — CHƯA THỰC THI.** Không đổi mật khẩu, không viết lại lịch sử Git, không đổi cấu hình của nhóm.
Tài liệu không chứa giá trị secret.

## 1. Hiện trạng dùng `root`

| Nơi | Hiện tại | Đích |
|---|---|---|
| Backend runtime (`application.properties`) | `DB_USERNAME` mặc định `root` | `pn_app` (chỉ DML) — đặt qua biến môi trường / `application-local.properties`; sau khi nhóm chuyển xong mới bỏ mặc định `root` |
| Integration test (`application-test.properties`) | `PHONENEST_IT_DB_USER` mặc định `root`; `ItDatabaseGuard` chặn sai database và **cảnh báo** khi dùng root | `pn_it_runtime` (chỉ DML trên `phonenest_it_*`) |
| E2E (`FE/e2e/start-backend-on-clone.sh`) | **Đã từ chối root** (2B.7D) | `pn_it_runtime` |
| Migration (`DB/migrations/*.sql`, hướng dẫn `DB/README.md`) | Hướng dẫn chạy bằng `-u root` | `pn_migrate` (khóa ngoài cửa sổ bảo trì) |
| Script tạo clone / test migration (`DB/tests/*.sh`) | `PN_DB_USER` mặc định `root` (tạo database + nạp dump cần CREATE/DROP) | `pn_it_provision` (toàn quyền chỉ trên `phonenest_it_*`, `phonenest_e2e_*`) |
| Kiểm toán / AI agent | Phiên `READ ONLY` bằng root | `pn_readonly` (SELECT) |

Đã kiểm chứng trên clone (2B.7D–2B.7E): toàn bộ test backend, E2E và diễn tập nâng cấp chạy được với
`pn7d_rt` (runtime) + `pn7d_mig` (migration); runtime bị từ chối DDL; không tài khoản thử nghiệm nào chạm được `bandienthoai`.
SQL đề xuất: `DB/admin/create_restricted_accounts_DRAFT.sql`.

## 2. Secret đã lộ

| Secret | Nơi lộ | Còn hiệu lực? | Mức |
|---|---|---|---|
| Mật khẩu `root` MariaDB đang dùng | Lịch sử Git (đã đẩy lên remote, 5 commit), `stash@{0}`, 3 file `scratch/*.py` (bị .gitignore) | **Có** | Cao nhất |
| JWT secret mặc định cũ | Lịch sử Git (1 commit), 2 file `scratch/*.py` | Code hiện tại không còn dùng; còn hiệu lực ở máy nào vẫn đặt giá trị cũ | Cao |
| Khóa Gemini / MoMo | Không phát hiện trong Git | — | — |

## 3. Kế hoạch chuyển đổi (thứ tự an toàn)

| Bước | Thời điểm đề xuất | Việc | Người làm | Đường lùi |
|---|---|---|---|---|
| 0 | Trước T−3 ngày | Thống nhất lịch; mỗi thành viên kiểm tra máy mình đang dùng tài khoản/mật khẩu nào (không gửi giá trị qua chat) | Cả nhóm | — |
| 1 | T−2 | Sao lưu `mysql.global_priv` + dump `bandienthoai`; tạo `pn_app`, `pn_migrate` (khóa), `pn_readonly`, `pn_it_*` bằng SQL nháp | Người quản trị DB | `DROP USER` các tài khoản mới (không ảnh hưởng root) |
| 2 | T−2 → T | Từng thành viên đặt `DB_USERNAME=pn_app`, `DB_PASSWORD=<mới>` trong `application-local.properties`; chạy ứng dụng + `mvn test` (IT với `pn_it_runtime`) | Từng thành viên | Quay lại `root` tạm thời (mật khẩu root **chưa** đổi ở bước này) |
| 3 | T (sau khi cả nhóm xác nhận bước 2) | **Đổi mật khẩu root**; lưu trong trình quản lý mật khẩu của nhóm | Người quản trị DB | Mật khẩu root mới được ghi trong trình quản lý trước khi đổi; nếu mất kết nối: đăng nhập máy chủ DB cục bộ để đặt lại |
| 4 | T | Sinh `JWT_SECRET` mới cho từng môi trường (mỗi máy một giá trị) | Từng thành viên / người phụ trách demo | Không cần lùi — chỉ làm phiên đăng nhập cũ hết hạn |
| 5 | T+1 | Bỏ mặc định `root` trong `application.properties` / `application-test.properties` (bắt buộc khai báo tài khoản) — **cần phê duyệt riêng** | Trưởng nhóm | Khôi phục dòng mặc định |
| 6 | T+1 | Sửa 3+2 file `scratch/*.py` đọc mật khẩu từ biến môi trường (hoặc xóa khi được phép) | Người sở hữu file | — |
| 7 | Tùy chọn | Viết lại lịch sử Git (git filter-repo) để xóa giá trị cũ | Trưởng nhóm, cả nhóm đồng ý | Mọi bản clone phải clone lại; dù viết lại vẫn **phải** đổi secret (bước 3–4) |

### Ảnh hưởng tới từng thành viên

- Phải tạo/cập nhật `BE/phonenest/application-local.properties` (đã `.gitignore`) với `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY`.
- Database dev riêng phải có M15/M16 (ứng dụng tự dừng nếu thiếu — `SCHEMA_CHECK FAILED`).
- Sau bước 4: tất cả phải đăng nhập lại trên giao diện.
- Công cụ GUI (HeidiSQL, DBeaver, IntelliJ Database) đang lưu mật khẩu root phải cập nhật sau bước 3.
- Ai còn dùng file `scratch/*.py` phải đặt mật khẩu qua biến môi trường.

### Tiêu chí hoàn tất

- `SHOW GRANTS` khớp bản nháp; ứng dụng của mọi thành viên chạy bằng `pn_app`; `mvn test` (IT) chạy bằng `pn_it_runtime`.
- Mật khẩu root cũ không còn đăng nhập được (kiểm tra bằng một lần đăng nhập thử thất bại — không ghi mật khẩu vào log).
- Quét secret (script 2B.7D) trên repo + scratch không còn giá trị **đang hiệu lực**.
