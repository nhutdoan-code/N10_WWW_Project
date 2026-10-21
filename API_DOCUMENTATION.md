# PhoneNest – Website bán điện thoại (Nhóm 10)

## Cách chạy (MariaDB)

1. **CSDL**: chạy lần lượt `DB/01_schema.sql` rồi `DB/02_data.sql`
   (`mysql -u root -p < DB/01_schema.sql`, tương tự với file 02; hoặc mở trong HeidiSQL / DBeaver / Workbench).
   Nếu đã tạo DB từ bản schema cũ, hãy `DROP DATABASE BanDienThoai;` rồi chạy lại (schema đã thêm cột, xem bên dưới).
2. **Backend** (`BE/phonenest`, Java 21, port 8080): mặc định kết nối `jdbc:mariadb://localhost:3306/BanDienThoai`, user `root`, mật khẩu rỗng.
   Khác mặc định thì đặt biến môi trường `DB_USER`, `DB_PASS` (hoặc sửa `application.properties`), rồi `mvn spring-boot:run`.
3. **Frontend** (`FE`, port 5173): `npm install` → `npm run dev`. Vite tự chuyển `/api` sang `localhost:8080`.
4. **Tuỳ chọn**: `OPENAI_API_KEY` để bật trợ lý chat AI (không có thì trợ lý trả lời theo từ khoá). Email quên mật khẩu/xác nhận đơn được ghi ra log nếu chưa cấu hình SMTP (xem comment trong `application.properties`).

### Thay đổi so với schema gốc
- `LoaiSanPham.maLoaiCha` – danh mục cha/con (Tree Pattern).
- `DonHang.diaChiGiaoHang`, `DonHang.ngayCapNhat` – địa chỉ giao và thời điểm cập nhật trạng thái.

## Tài khoản mẫu

| Vai trò | Tài khoản | Mật khẩu |
|---|---|---|
| Guest | không cần đăng nhập (giỏ hàng lưu tạm ở trình duyệt) | – |
| Customer | `khach1@phonenest.vn`, `khach2@phonenest.vn` | `Khach@123` |
| Admin | `admin@phonenest.vn` | `Admin@123` |

Voucher mẫu: `NEST10`, `WELCOME5` (khách mua lần đầu), `SALE10TR` (đơn từ 10 triệu) cho đơn hàng; `FREESHIP`, `SHIP50` cho phí vận chuyển; `HETHAN20` (đã hết hạn, không hiện ở bước thanh toán).

## Phân quyền (Spring Security + JWT)

| Đường dẫn | Guest | Customer | Admin |
|---|:-:|:-:|:-:|
| `/api/auth/**`, `/api/search/**`, `GET /api/products/**`, `GET /api/categories` | ✔ | ✔ | ✔ |
| `/api/customer/**`, `/api/notifications/**` | ✘ | ✔ | ✘ |
| `/api/admin/**` | ✘ | ✘ | ✔ |

Vai trò suy ra từ bảng: có trong `QuanTriVien` → `ADMIN`, ngược lại → `CUSTOMER`. Gửi token qua `Authorization: Bearer <token>`. Chưa đăng nhập → `401`, sai vai trò → `403`. Lỗi trả về `{ "message": "..." }`.

## Tìm kiếm và trợ lý chat (Spring AI)
- `GET /api/search?q=` – tìm nhanh (dropdown, tối đa 5 kết quả).
- `POST /api/chat` – khung chat tròn ở góc phải màn hình (khách vãng lai cũng dùng được). Body `{message, history}`; BE đưa danh sách sản phẩm, voucher, phí vận chuyển trong CSDL vào prompt cho `ChatClient`, trả về `{reply, products}` (tối đa 3 máy gợi ý, hiển thị dạng thẻ). Chưa có key hoặc lỗi → trả lời theo từ khoá (ngân sách "x triệu", phí giao hàng, voucher). Ô tìm kiếm trên đầu trang chỉ còn tìm theo từ khoá.

## Chức năng theo file Word

| Nhóm | Chức năng | Nơi xử lý |
|---|---|---|
| Guest | Duyệt danh mục dạng cây, xem/lọc/sắp xếp (giá, đánh giá, bán chạy), chi tiết, đánh giá & hỏi đáp, sản phẩm liên quan, đăng ký | `/api/products`, `/api/categories`, `/api/auth/register` |
| Guest | Giỏ hàng tạm (trình duyệt), tự đồng bộ vào CSDL khi đăng nhập | `CartContext`, `POST /api/customer/cart/sync` |
| Customer | Đăng nhập/đăng xuất, quên & đặt lại mật khẩu, mã giới thiệu | `/api/auth/*` |
| Customer | Hồ sơ, đổi mật khẩu, điểm tích lũy & hạng thành viên | `/api/customer/profile` |
| Customer | Sổ địa chỉ (thêm/sửa/xóa/mặc định) | `/api/customer/addresses` |
| Customer | Giỏ hàng: thêm, tăng/giảm, xóa, xóa hết, kiểm tra tồn kho & trạng thái kinh doanh | `/api/customer/cart` |
| Customer | Đặt hàng: chọn địa chỉ, mã khuyến mãi, COD/chuyển khoản/ví; email xác nhận | `POST /api/customer/orders` | (trừ kho ngay khi đặt)
| Customer | Lịch sử, chi tiết, theo dõi trạng thái, hủy (khi chờ xác nhận), mua lại, hóa đơn in/PDF | `/api/customer/orders`, trang `/orders/:ma/invoice` |
| Customer | Đổi trả/hoàn tiền, đánh giá (chỉ khi đã nhận hàng), hỏi đáp, wishlist, so sánh, thông báo | `/api/customer/returns`, `/reviews`, `/questions`, `/wishlist`, `/api/notifications` |
| Admin | Tìm kiếm tổng hợp, thống kê & báo cáo (xuất CSV) | `/api/admin/search`, `/api/admin/stats` |
| Admin | Loại sản phẩm (cây), sản phẩm, tồn kho (nhập kho/điều chỉnh/sắp hết) | `/api/admin/categories`, `/products`, `/inventory` |
| Admin | Đơn hàng (mới nhất trước, cập nhật trạng thái, sửa số lượng), tài khoản (khóa/xóa có điều kiện) | `/api/admin/orders`, `/accounts` |
| Admin | Khuyến mãi, xử lý đổi trả, kiểm duyệt đánh giá, trả lời hỏi đáp | `/api/admin/promotions`, `/returns`, `/reviews`, `/questions` |

## Endpoint chi tiết

**Công khai**: `POST /api/auth/register | login | forgot-password | reset-password` · `GET /api/products` (query `q, maLoai, min, max, sort` = `gia_asc | gia_desc | danh_gia | ban_chay`) · `GET /api/products/{ma}` · `/{ma}/related` · `/{ma}/reviews` · `/{ma}/questions` · `GET /api/categories` · `GET /api/search` · `POST /api/chat`

**Customer** (`/api/customer`): `cart` (GET, POST, PUT, DELETE, `/sync`, `/{ma}`) · `orders` (POST, GET, `/{ma}`, `PUT /{ma}/cancel`, `POST /{ma}/reorder`) · `promotions/{ma}` · `addresses` (CRUD, `PUT /{ma}/default`) · `profile` (GET, PUT, `PUT /password`) · `returns` (POST, GET) · `reviews` (POST, `/mine`, `/eligible/{ma}`) · `questions` (POST, GET) · `wishlist` (GET, `POST|DELETE /{ma}`) · `GET /api/notifications`, `PUT /api/notifications/{ma}/read | read-all`

**Admin** (`/api/admin`): `products`, `categories` (CRUD) · `inventory` (GET, `POST /{ma}/import`, `PUT /{ma}`) · `orders` (GET, `/{ma}`, `PUT /{ma}/status`, `PUT /{ma}/items/{maSanPham}`) · `accounts` (GET, `/{ma}`, PUT, DELETE, `PUT /{ma}/toggle-lock`) · `promotions` (CRUD) · `returns` (GET, `PUT /{ma}/status`) · `reviews` (GET, DELETE) · `questions` (GET, `PUT /{ma}/answer`, DELETE) · `stats`, `stats/export`, `search`

## Hủy đơn hàng (UC013)
`PUT /api/customer/orders/{ma}/cancel` body `{ "lyDo": "...", "ghiChu": "..." }` (`lyDo` chọn từ danh sách 7 lý do phổ biến; `ghiChu` bắt buộc khi chọn "Lý do khác"). Chỉ hủy được khi đơn **Chờ xác nhận** hoặc **Đang xử lý**; đơn Đang giao / Đã giao bị từ chối với thông báo "Đơn hàng đã được bàn giao cho đơn vị vận chuyển, bạn không thể hủy." Hệ thống kiểm tra lại trạng thái trong CSDL, đổi sang Đã hủy, lưu vào bảng `HuyDonHang` (lý do, ghi chú, ngày hủy), cộng lại tồn kho và gửi thông báo. Kho được trừ lúc đặt hàng nên hủy (khách hoặc admin) đều hoàn kho.
Bảng `HuyDonHang` (lý do, ghi chú, ngày hủy) nằm sẵn trong `01_schema.sql`.

## Voucher và phí vận chuyển
- **Phí vận chuyển** tính theo tỉnh/thành của địa chỉ giao hàng (bảng `PhiVanChuyen`, admin quản lý ở `/api/admin/shipping-fees`). Địa chỉ không khớp khu vực nào thì dùng dòng "Khác". Đơn đạt mức `mienPhiTu` của khu vực thì miễn phí giao hàng.
- **Voucher** (`KhuyenMai`) có hai loại: `DON_HANG` (giảm trên tạm tính) và `VAN_CHUYEN` (giảm trên phí giao hàng). Mỗi đơn dùng tối đa 1 voucher mỗi loại. Có thêm đơn tối thiểu, số lượng giới hạn (`soLuong`/`daDung`), chỉ dành cho khách mua lần đầu (`chiKhachMoi`). Hủy đơn sẽ hoàn lại lượt dùng.
- `GET /api/customer/checkout/preview?maDiaChi=...` trả về tạm tính, phí giao hàng, thời gian giao dự kiến và danh sách voucher đang hiệu lực (kèm `apDungDuoc`, `lyDo` nếu chưa dùng được, `soTienGiam`). FE tự chọn sẵn voucher giảm nhiều nhất cho mỗi loại, khách có thể đổi hoặc bỏ chọn.
- `POST /api/customer/orders` nhận thêm `maKmVanChuyen`; BE kiểm tra lại toàn bộ điều kiện và lưu `giamGia`, `phiVanChuyen`, `giamPhiVanChuyen` vào đơn.

## Địa chỉ giao hàng (tỉnh / phường sau sáp nhập)
- Danh mục theo Nghị quyết 202/2025/QH15: 34 tỉnh/thành và 3.321 phường/xã/đặc khu, nằm trong `02_data.sql` (bảng `TinhThanh`, `PhuongXa`).
- `GET /api/locations/provinces` và `GET /api/locations/provinces/{maTinh}/wards` trả về danh sách `{ma, ten}` cho ô chọn.
- Địa chỉ lưu `maTinh`, `maPhuong` và chỉ cho nhập tay phần "số nhà, tên đường" (`diaChiNhanHang`). BE kiểm tra phường thuộc đúng tỉnh.
- Khách thêm / sửa địa chỉ ngay ở bước thanh toán; đổi địa chỉ sẽ tính lại phí vận chuyển theo tỉnh (bảng `PhiVanChuyen`, dòng `maTinh` rỗng là mức mặc định).
