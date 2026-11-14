# Tính năng 5 – Hậu mãi (nhánh HuyHoang)

## Đã bổ sung

- **Hủy đơn:** tái sử dụng `PUT /api/customer/orders/{ma}/cancel`; cập nhật giao diện đúng các trạng thái được phép hủy (`CHO_XAC_NHAN`, `DA_XAC_NHAN`, `DANG_CHUAN_BI`). Không cho hủy sau khi bàn giao vận chuyển.
- **Mua lại:** tái sử dụng `POST /api/customer/orders/{ma}/reorder` để đưa sản phẩm của đơn vào giỏ hàng.
- **Đổi / trả hàng:** chọn từng phiên bản sản phẩm, số lượng, loại yêu cầu (`DOI_HANG`, `TRA_HANG_HOAN_TIEN`), lý do, ảnh. Giới hạn 7 ngày từ khi giao, chặn trùng sản phẩm đang/đã được xử lý.
- **Bảo hành:** theo từng phiên bản đã mua và số tháng bảo hành ghi trên chi tiết đơn. Chặn gửi khi hết hạn hoặc có yêu cầu đang xử lý cho cùng sản phẩm.
- **Khiếu nại:** tạo theo đơn hàng thuộc tài khoản, tránh gửi nhiều khiếu nại đồng thời cho cùng đơn.
- **Theo dõi:** giao diện `/returns` hiển thị tất cả loại yêu cầu, trạng thái, phản hồi và lịch sử riêng của bảo hành/khiếu nại.
- **Admin:** giao diện `/admin/returns` quản lý cả đổi trả, bảo hành, khiếu nại; trạng thái và ghi chú được cập nhật có kiểm tra chuyển trạng thái.

## Chuẩn bị database MariaDB

**Chạy đúng 1 lần** trên database `BanDienThoai` đã có các bảng của PhoneNest:

`DB/migrations/12_after_sales.sql`

SQL này bổ sung `YeuCauHauMai` và `LichSuHauMai`. Không xóa bảng cũ, không thay dữ liệu cũ. Ví dụ PowerShell tại thư mục gốc dự án, nếu đã cài `mariadb` vào PATH:

```powershell
cmd /c "mariadb -h 127.0.0.1 -P 3306 -u root -p BanDienThoai < DB\migrations\12_after_sales.sql"
```

Nếu sử dụng MySQL Workbench/DBeaver: chọn schema `BanDienThoai` và chạy nội dung file SQL trên. **Không chạy lại** SQL nếu hai bảng đã tồn tại. Nếu dùng `DB/phonenest_full.sql` để tạo DB mới, cần chạy migration 12 *sau* khi import xong.

## Chạy chương trình

Backend (Java 21, Maven, MariaDB):

```powershell
cd BE\phonenest
mvn spring-boot:run
```

Frontend (terminal khác, từ thư mục gốc):

```powershell
cd FE
npm install
npm run dev
```

Backend sử dụng cấu hình database trong `BE/phonenest/src/main/resources/application.properties` (mặc định `localhost:3306/BanDienThoai`). Các biến API AI/Gemini vẫn là của project gốc; patch hậu mãi không sửa phần AI.

## API quan trọng

| Vai trò | Endpoint | Công dụng |
| --- | --- | --- |
| CUSTOMER | `POST /api/customer/returns` | Gửi yêu cầu đổi/trả |
| CUSTOMER | `GET /api/customer/returns` | Danh sách đổi/trả của mình |
| CUSTOMER | `POST /api/customer/after-sales` | Gửi yêu cầu bảo hành/khiếu nại |
| CUSTOMER | `GET /api/customer/after-sales` | Theo dõi yêu cầu của mình |
| CUSTOMER | `GET /api/customer/after-sales/{ma}` | Chi tiết và lịch sử |
| ADMIN | `GET /api/admin/returns` | Danh sách đổi/trả |
| ADMIN | `PUT /api/admin/returns/{ma}/status?trangThai=DA_DUYET&ghiChuXuLy=...` | Xử lý đổi/trả |
| ADMIN | `GET /api/admin/after-sales` | Danh sách bảo hành/khiếu nại |
| ADMIN | `PUT /api/admin/after-sales/{ma}/status` | Cập nhật bảo hành/khiếu nại với JSON `{ "trangThai": "DANG_XU_LY", "ghiChuXuLy": "Đang kiểm tra" }` |

Đổi/trả: `CHO_DUYET -> DA_DUYET / TU_CHOI -> DANG_XU_LY -> HOAN_TAT` (có thể hoàn tất từ `DA_DUYET`). Bảo hành/khiếu nại: `CHO_TIEP_NHAN -> DANG_XU_LY -> HOAN_TAT` hoặc từ chối khi đang chờ/đang xử lý. Admin phải cung cấp lý do nếu từ chối.

**Lưu ý:** `HOAN_TAT` đổi/trả chỉ ghi nhận hoàn tất xử lý, **không tự động thanh toán tiền hoàn qua MoMo/ngân hàng**. Phần hoàn tiền và nhập kho thực tế cần quy trình kiểm tra riêng trước khi triển khai thương mại.

## Demo nhanh

1. Đăng nhập tài khoản CUSTOMER có đơn đã `DA_GIAO`.
2. Vào **Đơn hàng > Chi tiết > Đổi / trả hàng** hoặc **Yêu cầu bảo hành**; điền mẫu tại `/returns`.
3. Đăng nhập ADMIN, vào **Hậu mãi**, duyệt và cập nhật ghi chú.
4. Đăng nhập lại CUSTOMER, vào **Hậu mãi / bảo hành** để xem trạng thái và lịch sử.
5. Kiểm tra trường hợp không thuộc đơn của mình, hết 7 ngày đổi trả, hết hạn bảo hành, gửi trùng yêu cầu, trạng thái không hợp lệ.

## Kiểm tra test

```powershell
cd BE\phonenest
mvn -Dtest=HauMaiServiceTest test
```

## Bổ sung sửa lỗi giao diện & test (patch 2)

- **Thanh toán:** sửa cách đọc danh sách phương thức thanh toán (`value`/`label`), hiển thị COD/chuyển khoản/MoMo đúng giá trị backend. Sửa hiển thị địa chỉ nhận hàng theo tên trường DTO thực tế. Chặn gửi đơn nếu thiếu địa chỉ hoặc phương thức hợp lệ. Nếu vẫn báo HTTP 400, kiểm tra `Network > /api/customer/orders > Response` để biết lỗi thực tế (voucher/tồn kho/địa chỉ...).
- **Mua lại:** từ chi tiết đơn hàng, nút "Mua lại sản phẩm" cho chọn chính xác một sản phẩm (1 chiếc), đi thẳng sang thanh toán theo phiên bản cũ mà không dùng giỏ hiện tại. Nút "Thêm cả đơn vào giỏ" riêng biệt, có xác nhận và sẽ giữ sản phẩm vốn đang trong giỏ.
- **Hậu mãi:** danh sách chọn đơn hiển thị tên máy, ngày mua, tổng tiền; có khung xem trước mã đơn/trạng thái/các dòng hàng trước khi gửi.
- **Ảnh minh chứng:** chọn file JPG/PNG/WEBP trên máy tối đa 5MB, xem trước ảnh; upload bằng API `POST /api/customer/after-sales/images`, tái sử dụng `LuuTruAnhService` với đường dẫn `/uploads/...`, lưu link trả về trong yêu cầu. Không cần nhập URL.

Các bước demo: vào `/orders` > chi tiết đơn > "Mua lại sản phẩm" > thanh toán kiểm tra chỉ một máy; quay lại `/returns` chọn đơn theo tên/giá; chọn ảnh file, gửi, admin kiểm tra hình và xử lý.
