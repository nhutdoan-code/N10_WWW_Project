# PhoneNest – Website bán điện thoại (Nhóm 10)

## Cách chạy (MariaDB)

1. **CSDL**: chạy lần lượt `DB/01_schema.sql`, `DB/02_data.sql` và `DB/04_user_tracking.sql`
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
- `POST /api/chat` – khung chat tròn ở góc phải màn hình, **chỉ hiện và chỉ gọi được khi đã đăng nhập** (CUSTOMER hoặc ADMIN; ẩn danh → 401). Body `{message, conversationId?, visitorId?}`; trả về `{reply, products, mode, conversationId}`. Lịch sử hội thoại lưu ở CSDL (`CuocHoiThoaiChat`, `TinNhanChat` – migration 21) theo tài khoản; không truyền `conversationId` → tạo hội thoại mới. `GET /api/chat/conversations` (hội thoại của tôi) · `GET /api/chat/conversations/{id}/messages` (404 nếu không thuộc tài khoản). Khách hàng có thể nhờ trợ lý thêm sản phẩm vào giỏ ("Thêm Redmi 13C màu đen vào giỏ hàng", `mode=CART_ADDED`; nhiều phiên bản → `CART_SELECT_VARIANT`), chỉ với biến thể được phép bán; Admin nhận `ADMIN_CART_DENIED`. Gemini được cấp công cụ tra cứu sản phẩm (`PhoneNestProductTools`); mọi số tiền trong câu trả lời AI vẫn phải thuộc giá công bố (ChatPriceGuard). Chưa có key hoặc lỗi → trả lời theo từ khoá.

## Chức năng theo file Word

| Nhóm | Chức năng | Nơi xử lý |
|---|---|---|
| Guest | Duyệt danh mục dạng cây, xem/lọc/sắp xếp (giá, đánh giá, bán chạy), chi tiết, đánh giá & hỏi đáp, sản phẩm liên quan, đăng ký | `/api/products`, `/api/categories`, `/api/auth/register` |
| Guest | Giỏ hàng tạm (trình duyệt), tự đồng bộ vào CSDL khi đăng nhập | `CartContext`, `POST /api/customer/cart/sync` |
| Customer | Đăng nhập/đăng xuất, quên & đặt lại mật khẩu, mã giới thiệu | `/api/auth/*` |
| Customer | Hồ sơ, đổi mật khẩu, điểm tích lũy & hạng thành viên | `/api/customer/profile` |
| Customer | Sổ địa chỉ (thêm/sửa/xóa/mặc định) | `/api/customer/addresses` |
| Customer | Giỏ hàng: thêm, tăng/giảm, xóa, xóa hết, kiểm tra tồn kho & trạng thái kinh doanh | `/api/customer/cart` |
| Customer | Đặt COD ngay; MoMo/VNPay chỉ tạo đơn sau khi cổng thanh toán xác nhận thành công; email xác nhận | `POST /api/customer/orders` (COD), `POST /api/customer/checkout/payment` (MoMo/VNPay) | (trừ kho khi tạo đơn)
| Customer | Lịch sử, chi tiết, theo dõi trạng thái, hủy (khi chờ thanh toán/chờ xác nhận), mua lại, hóa đơn in/PDF | `/api/customer/orders`, trang `/orders/:ma/invoice` |
| Customer | Đổi trả/hoàn tiền; theo dõi tiến trình bảo hành/khiếu nại; đánh giá (chỉ khi đã nhận hàng), hỏi đáp, wishlist, so sánh, thông báo | `/api/customer/returns`, `/after-sales`, `/reviews`, `/questions`, `/wishlist`, `/api/notifications` |
| Admin | Tìm kiếm tổng hợp, thống kê & báo cáo (xuất CSV) | `/api/admin/search`, `/api/admin/stats` |
| Admin | Loại sản phẩm (cây), sản phẩm, tồn kho (nhập kho/điều chỉnh/sắp hết) | `/api/admin/categories`, `/products`, `/inventory` |
| Admin | Đơn hàng (mới nhất trước, cập nhật trạng thái, sửa số lượng), tài khoản (khóa/xóa có điều kiện) | `/api/admin/orders`, `/accounts` |
| Admin | Khuyến mãi, xử lý đổi trả/bảo hành/khiếu nại, kiểm duyệt đánh giá, trả lời hỏi đáp | `/api/admin/promotions`, `/returns`, `/after-sales`, `/reviews`, `/questions` |

## Endpoint chi tiết

**Công khai**: `POST /api/auth/register | login | forgot-password | reset-password` · `POST /api/tracking/events` · `GET /api/products` (query `q, maLoai, min, max, sort` = `gia_asc | gia_desc | danh_gia | ban_chay`) · `GET /api/products/{ma}` · `/{ma}/related` · `/{ma}/reviews` · `/{ma}/questions` · `GET /api/categories` · `GET /api/search` (ChatBox `POST /api/chat` cần đăng nhập)

### Theo dõi hành vi và cá nhân hóa trợ lý
- Frontend ghi nhận `PAGE_VIEW`, `PRODUCT_VIEW`, `SEARCH`, `ADD_TO_CART` qua `POST /api/tracking/events`; body gồm `visitorId` (UUID), `type` và đúng trường theo loại (`pageType`, `productId` hoặc `searchTerm`). Endpoint công khai; tài khoản Customer được gắn từ JWT phía máy chủ, không nhận mã tài khoản từ client.
- Mỗi trình duyệt dùng một mã ẩn danh first-party. Chỉ lưu loại trang đã cho phép (không lưu URL/referrer), mã sản phẩm hoặc từ khóa tìm kiếm tối đa 120 ký tự; không thu thập IP/user-agent. Dữ liệu tự xóa sau 90 ngày (`TRACKING_RETENTION_DAYS`).
- `POST /api/chat` nhận thêm `visitorId` tùy chọn. Trợ lý dùng lượt xem/thêm giỏ và tìm kiếm gần đây (30 ngày, tối đa 100 sự kiện) để ưu tiên sản phẩm liên quan; lịch sử hội thoại không được ghi vào bảng tracking. Khách chưa có lịch sử vẫn nhận gợi ý hiện tại như cũ.
- Với CSDL đang chạy, chỉ cần thực thi `DB/04_user_tracking.sql`; không cần xóa hoặc tạo lại dữ liệu.

**Customer** (`/api/customer`): `cart` (GET, POST, PUT, DELETE, `/sync`, `/{ma}`) · `orders` (POST COD, GET, `/{ma}`, `PUT /{ma}/cancel`, `POST /{ma}/reorder`, legacy `POST /{ma}/momo`, `POST /{ma}/vnpay`, `GET /{ma}/payment-status`) · `checkout/payment` (POST bắt đầu trả trước, GET `/{sessionId}` xem trạng thái phiên) · `checkout/preview` · `addresses` (CRUD, `PUT /{ma}/default`) · `profile` (GET, PUT, `PUT /password`) · `returns` (POST, GET) · `after-sales` (POST, GET, `GET /{ma}`) · `reviews` (POST, `/mine`, `/order/{maDonHang}`) · `questions` (POST, GET) · `wishlist` (GET, `POST|DELETE /{ma}`) · `GET /api/notifications` (mỗi thông báo có `duongDan`: trang FE cần mở khi bấm, vd `/orders/{maDonHang}`, `/returns#{maYeuCau}`, `/products/{maSanPham}#hoi-dap`; null = không có trang chi tiết), `PUT /api/notifications/{ma}/read | read-all`

**Admin** (`/api/admin`): `products`, `categories` (CRUD) · `inventory` (GET `?q=&loc=&page=0&size=10`, trả về `content`, `totalElements`, `totalPages`; `GET /stats`, `GET /history`, `GET /{ma}/history`, `POST /{ma}/import`, `POST /batch-import` nhận `{ items: [{ sku, soLuong }], ghiChu }`, `PUT /{ma}/adjust`) · `orders` (GET, `/{ma}`, `PUT /{ma}/status`, `PUT /{ma}/items/{maSanPham}`, `GET /{ma}/momo/refund-status`, `POST /{ma}/momo/refund`, `GET /{ma}/vnpay/refund-status`, `POST /{ma}/vnpay/refund`) · `checkout-payment-sessions/{sessionId}/refund` (PUT ghi nhận mã hoàn tiền ngoài hệ thống) · `accounts` (GET, `/{ma}`, PUT, DELETE, `PUT /{ma}/toggle-lock`) · `promotions` (CRUD) · `returns` (GET, `PUT /{ma}/status`) · `reviews` (GET, DELETE) · `questions` (GET, `PUT /{ma}/answer`, DELETE) · `stats`, `stats/export`, `search`

**Admin analytics:** `/admin/analytics/behavior` lọc theo `tuNgay`, `denNgay`, `loaiHanhVi`, `maKh`; `/admin/analytics/preferences/{maKh}` xem sở thích tổng hợp; `DELETE /admin/analytics/cleanup?soNgayLuuTru=90` xóa sự kiện cũ sau khi admin xác nhận. Giao diện tại `/admin/analytics`.

**Xử lý hoàn tiền hậu mãi:** sau khi nhận hàng trả, `POST /admin/returns/{ma}/restock` cộng lại đúng số lượng SKU, liên kết biến động kho với yêu cầu và ghi người thao tác. Với hình thức `MOMO`, gọi `POST /admin/returns/{ma}/refund` với `{ "hinhThuc": "MOMO" }`; backend gọi API refund MoMo trên giao dịch gốc và chỉ lưu đối soát khi MoMo trả thành công. Với hình thức `VNPAY`, gửi `{ "hinhThuc": "VNPAY" }`; backend gọi Refund API VNPay, hỗ trợ hoàn một phần/toàn phần, lưu mã yêu cầu để lần thử lại dùng đúng cùng mã khi kết quả còn đang xử lý, và chỉ ghi nhận hoàn tất khi VNPay xác nhận. Chọn `VNPAY` chỉ khi đơn được thanh toán qua VNPay. Với `CHUYEN_KHOAN`, `TIEN_MAT` hoặc `KHAC`, cửa hàng thực hiện hoàn tiền bên ngoài rồi gửi thêm `maGiaoDich` để ghi nhận. Mọi hình thức đều nhận thêm `soTien` (số nguyên VND, không vượt giá trị đủ điều kiện) hoặc `phanTram` cùng `lyDoDieuChinh` khi hoàn ít hơn; với MoMo/VNPay số tiền này được gửi lên cổng thanh toán, còn `thoiDiemHoan` chỉ áp dụng cho hình thức thủ công. Khi VNPay đang `DANG_HOAN_TIEN`, lần gửi lại giữ nguyên số tiền đã gửi. Đơn COD hoặc khách chọn "Chuyển khoản" chỉ được hoàn qua `CHUYEN_KHOAN`. Yêu cầu không thể hoàn tất nếu chưa ghi nhận nhập kho và giao dịch hoàn tiền.

**Hoàn giao dịch MoMo bị hủy/trùng:** đơn có thanh toán MoMo ở trạng thái `CAN_HOAN_TIEN` hiển thị trong danh sách Admin. Kiểm tra `GET /admin/orders/{ma}/momo/refund-status`, sau đó gọi `POST /admin/orders/{ma}/momo/refund`; backend gửi hoàn đủ số tiền của giao dịch đang chờ và đánh dấu `DA_HOAN_TIEN` chỉ khi MoMo xác nhận thành công. Nếu có nhiều giao dịch trùng, gọi lại để hoàn từng giao dịch.

**Quy trình bảo hành / khiếu nại** (`PUT /api/admin/after-sales/{ma}/status`): bảo hành chuyển `CHO_TIEP_NHAN → DANG_KIEM_TRA → DANG_XU_LY → HOAN_TAT|TU_CHOI`; khiếu nại chuyển `CHO_TIEP_NHAN → DANG_XAC_MINH → DANG_XU_LY → HOAN_TAT|TU_CHOI`. Mỗi lần chuyển bước cần `ghiChuXuLy` để ghi lịch sử và cập nhật cho khách; hoàn tất cần thêm `ketQuaXuLy`. Customer API trả tiến trình, nội dung cập nhật và kết quả xử lý. Migration: `DB/migrations/25_after_sales_workflow.sql`.

## Hủy đơn hàng (UC013)
`PUT /api/customer/orders/{ma}/cancel` body `{ "lyDo": "...", "ghiChu": "..." }` (`lyDo` chọn từ danh sách 7 lý do phổ biến; `ghiChu` bắt buộc khi chọn "Lý do khác"). Chỉ hủy được khi đơn **Chờ thanh toán** (đơn legacy), **Chờ xác nhận** hoặc **Đang xử lý**; đơn Đang giao / Đã giao bị từ chối với thông báo "Đơn hàng đã được bàn giao cho đơn vị vận chuyển, bạn không thể hủy." Hệ thống kiểm tra lại trạng thái trong CSDL, đổi sang Đã hủy, lưu vào bảng `HuyDonHang` (lý do, ghi chú, ngày hủy), cộng lại tồn kho và gửi thông báo. Kho được trừ lúc tạo đơn sau thanh toán thành công (hoặc khi đặt COD), nên hủy đều hoàn kho.
Bảng `HuyDonHang` (lý do, ghi chú, ngày hủy) nằm sẵn trong `01_schema.sql`.

## Voucher và phí vận chuyển
- **Phí vận chuyển** (migration 22, `PhiVanChuyenService.tinh`): kho tại TP.HCM, 34 tỉnh/thành chia 4 vùng (`vung` 0 nội tỉnh TP.HCM · 1 lân cận & miền Nam · 2 miền Trung & Tây Nguyên · 3 miền Bắc), admin quản lý ở `/api/admin/shipping-fees` (`vung`, `phiCoBan`, `phuPhiXa`, `phuPhiDacKhu`, `mienPhiTu`, `soNgayGiaoToiThieu`, `soNgayGiaoToiDa`; `thoiGianGiao` tự sinh). Tỉnh chưa cấu hình dùng dòng mặc định (`maTinh = null`).
  - Tạm tính ≥ `mienPhiTu` → miễn toàn bộ phí. Ngược lại: **phí vùng** (`phiCoBan` cho phường; + `phuPhiXa` nếu địa chỉ là xã/ngoại thành; + `phuPhiDacKhu` nếu là đặc khu/huyện đảo — loại địa bàn suy ra từ tên trong `PhuongXa`) + **phí khai giá** 0,5% giá trị hàng, tối đa 50.000đ, với đơn từ 3.000.000đ + **phụ phí COD** 15.000đ khi thanh toán COD cho đơn trên 10.000.000đ.
  - Ngày giao dự kiến = ngày bàn giao (đơn sau 16:00 tính từ hôm sau) + số ngày tối thiểu..tối đa của tỉnh; đặc khu cộng thêm 1..2 ngày. Lưu trên đơn: `ngayGiaoDuKienTu`, `ngayGiaoDuKienDen`; `phiKhaiGia`, `phuPhiCod` là thành phần đã gồm trong `phiVanChuyen`.
- **Voucher** (`KhuyenMai`) có hai loại: `DON_HANG` (giảm trên tạm tính) và `VAN_CHUYEN` (chỉ giảm **phí giao hàng theo vùng**, không giảm phí khai giá / phụ phí COD; không dùng được khi đơn đã được miễn phí vận chuyển — BE trả 400). Mỗi đơn dùng tối đa 1 voucher mỗi loại. Mã phải đang hoạt động, trong thời gian hiệu lực, đạt đơn tối thiểu, còn lượt (`soLuong`/`daDung`), không vượt giới hạn mỗi khách (`gioiHanMoiKhach`) và thỏa điều kiện khách mới (`chiKhachMoi`). Từ migration 23, `kieuGiam` = `SO_TIEN` (giảm tiền mặt cố định `soTienGiamCoDinh`, tiền giảm = min(số tiền, cơ sở); voucher đơn hàng phải có `donToiThieu` ≥ số tiền giảm) hoặc `PHAN_TRAM` (`phanTramGiam` + `soTienGiamToiDa` **bắt buộc**, tiền giảm = min(cơ sở × % / 100, trần)). Chưa đạt `donToiThieu` thì không dùng được (giảm 0). Kiểu giảm và mức giảm không đổi được sau khi tạo. `GET /checkout/preview` trả thêm `kieuGiam`, `soTienGiamCoDinh` cho từng voucher. Lượt chỉ tăng khi đặt hàng thành công trong transaction và được hoàn khi đơn bị hủy. Đổi trả sau khi giao không hoàn lượt; tiền hoàn của từng dòng đã phân bổ phần giảm giá theo tỷ lệ vào `soTienHoan`.
- **Giao diện trang chủ**: header 2 tầng + thanh danh mục, banner tự chuyển mỗi 5 giây (dừng khi rê chuột, có nút/dấu chấm điều hướng), 2 banner phụ, dải dịch vụ, mục Bán chạy / Giá tốt, rồi danh sách có lọc. Bộ lọc hãng/mức giá nằm trên URL (`/?loai=L01&gia=2`).
- **Ảnh mẫu**: dữ liệu mẫu trỏ tới `/uploads/sp001-1.jpg` ... `sp013-3.jpg` (3 góc mỗi máy: trước, sau, cạnh). Đặt ảnh thật vào thư mục `BE/phonenest/uploads/` (xem README.txt trong đó) hoặc tải lên qua trang Admin. Ảnh thiếu sẽ hiện khung "Chưa có ảnh".
- **Thống kê** (`GET /api/admin/stats?tu&den`): trả thêm `soConHang`, `doanhThuTheoNgay[{ngay, doanhThu, soDon}]` và `theoHang[{ten, doanhThu, daBan}]` (doanh thu đơn đã giao gộp theo hãng). FE vẽ biểu đồ bằng SVG: xu hướng (đổi Doanh thu/Số đơn, Ngày/Tháng, di chuột xem chi tiết), donut trạng thái đơn, cột theo hãng, bán chạy, tình trạng kho.
- **Trạng thái tồn kho**: sản phẩm còn hàng nhưng tồn ≤ 10 hiển thị "Sắp hết hàng" (tính ở FE, không lưu DB).
- **Thông số kỹ thuật**: mỗi sản phẩm có object `thongSo` (kichThuocManHinh, loaiManHinh, tanSoQuet, chip, ram, boNho, cameraSau, cameraTruoc, pin, congSuatSac, heDieuHanh, nfc, ho5G, trongLuong) lưu thành các cột riêng trong bảng `SanPham` (không còn chuỗi ngăn cách bằng `|`).
- **Ảnh sản phẩm**: admin chọn ảnh từ máy; FE gọi `POST /api/admin/uploads` (multipart, field `file`, JPG/PNG/WEBP ≤ 5MB) nhận `{url}` dạng `/uploads/<uuid>.png`, rồi gửi `hinhAnhs` + `anhChinh` khi lưu sản phẩm. File nằm ở thư mục `uploads/` cạnh nơi chạy BE (cấu hình `app.upload-dir`), phục vụ công khai tại `/uploads/**`.
- **Ảnh đánh giá**: khách hàng chọn ảnh từ thiết bị; FE gọi `POST /api/customer/reviews/images` (multipart, field `file`, JPG/PNG/WEBP ≤ 5MB), nhận `{url}` dạng `/uploads/<uuid>.png`, rồi lưu URL cùng đánh giá qua `POST /api/customer/reviews`. Chỉ tài khoản Customer đã đăng nhập mới được tải ảnh lên.
- **Mua ngay**: trang chi tiết sản phẩm (chỉ hiện khi đã đăng nhập Customer) chuyển sang `/checkout?mua={maSanPham}&sl={soLuong}`. `GET /checkout/preview` và endpoint checkout nhận thêm `maSanPham`, `soLuong` (tùy chọn); khi có thì chỉ xử lý sản phẩm đó, không đọc và không xóa giỏ hàng.
- `GET /api/customer/checkout/preview?maDiaChi=...[&phuongThucTt=COD|CHUYEN_KHOAN|VI_DIEN_TU]` trả về `items` (dòng hàng), tạm tính, phí giao hàng (`phiVanChuyen` = tổng), `vanChuyen` (chi tiết: `vung`, `tenVung`, `loaiDiaBan`, `phiVung`, `phiKhaiGia`, `phuPhiCod`, `mienPhi`, `soNgayToiThieu/ToiDa`, `ngayGiaoDuKienTu/Den`), thời gian giao dự kiến và danh sách voucher đang hiệu lực (kèm `apDungDuoc`, `lyDo` nếu chưa dùng được, `soTienGiam`). FE tự chọn sẵn voucher giảm nhiều nhất cho mỗi loại, khách có thể đổi hoặc bỏ chọn.
- `POST /api/customer/orders` (COD) và `POST /api/customer/checkout/payment` (MoMo/VNPay) nhận thêm `maKmVanChuyen`; BE kiểm tra lại toàn bộ điều kiện. Với checkout trả trước, dữ liệu và sản phẩm được snapshot trong phiên thanh toán; không tạo đơn, trừ kho hoặc xóa giỏ trước khi IPN thành công.

## Thanh toán MoMo
- `POST /api/customer/checkout/payment` nhận header `Idempotency-Key` (UUID) và body checkout; với thanh toán một phần giỏ có thể truyền `chon=PB...` lặp lại trong query. Response `{ "sessionId", "status", "payUrl", "orderId" }`. `GET /api/customer/checkout/payment/{sessionId}` chỉ cho phép chủ phiên tra trạng thái; phiên chưa tạo đơn sẽ có `orderId: null`.
- Chọn `phuongThucTt: "VI_DIEN_TU"` và gọi `POST /api/customer/checkout/payment` (header `Idempotency-Key`, body checkout thông thường). Response gồm `sessionId`, `status`, `payUrl`, `orderId`; chuyển khách sang `payUrl`. Khi MoMo gửi IPN thành công, backend kiểm tra lại giá, voucher và tồn kho rồi mới tạo đơn ở `CHO_XAC_NHAN`.
- `GET /api/customer/checkout/payment/{sessionId}` trả trạng thái phiên (`CHO_THANH_TOAN`, `DA_TAO_DON`, `THAT_BAI`, `CAN_HOAN_TIEN`, `DA_HOAN_TIEN`) và `orderId` khi có. MoMo return chỉ điều hướng về `/orders?checkoutPayment={sessionId}`; FE tra trạng thái phiên, không tin redirect để kết luận đã trả tiền.
- Checkout không giữ tồn kho trong lúc khách thanh toán; nếu sau khi thanh toán thành công không còn hàng hoặc giá/khuyến mãi thay đổi, đơn không được tạo và giao dịch được chuyển sang xử lý hoàn tiền. Giỏ hàng chỉ bị trừ sản phẩm đã mua sau khi tạo đơn thành công.
- Nếu checkout VNPay không thể tạo đơn sau khi đã thu tiền, Admin nhận thông báo cần hoàn thủ công. Sau khi hoàn tiền thực tế qua ngân hàng, ghi nhận mã giao dịch qua `PUT /api/admin/checkout-payment-sessions/{sessionId}/refund` với body `{ "maGiaoDichHoanTien": "..." }`; thao tác chỉ nhận phiên đang `CAN_HOAN_TIEN` và được ghi nhật ký Admin.
- `POST /api/customer/orders/{ma}/momo` và `GET /api/customer/orders/{ma}/payment-status` chỉ còn phục vụ các đơn thanh toán cũ cần thanh toán lại/đối soát.
- MoMo gọi `POST /api/payment/momo/ipn` (không yêu cầu JWT). Backend chỉ cập nhật trạng thái sau khi xác thực chữ ký và đối chiếu số tiền với đơn; kết quả redirect trên trình duyệt không phải xác nhận thanh toán.
- Cấu hình sandbox qua `MOMO_ENDPOINT`, `MOMO_REFUND_ENDPOINT`, `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`, `MOMO_REDIRECT_URL` và `MOMO_IPN_URL`. Không đưa khóa vào FE hoặc Git. IPN phải là HTTPS công khai có thể truy cập từ MoMo; xem [hướng dẫn cấu hình local](./docs/HUONG_DAN_CAU_HINH_LOCAL.md).

## Thanh toán ngân hàng qua VNPay
- Checkout nhận `phuongThucTt: "CHUYEN_KHOAN"`; FE gọi `POST /api/customer/checkout/payment` (header `Idempotency-Key`) để nhận `{ sessionId, status, payUrl, orderId }` rồi chuyển khách sang VNPay. Đơn chỉ được tạo ở `CHO_XAC_NHAN` sau IPN thành công; COD vẫn được tạo ngay ở `CHO_XAC_NHAN`.
- `GET /api/payment/vnpay/ipn` phải được cấu hình trong merchant portal VNPay và truy cập được công khai qua HTTPS. Backend chỉ đánh dấu đã thanh toán khi chữ ký HMAC-SHA512, mã merchant, số tiền, trạng thái giao dịch và trạng thái đơn đều hợp lệ. URL return của trình duyệt chỉ điều hướng người dùng và không được dùng làm bằng chứng đã thanh toán.
- `GET /api/customer/checkout/payment/{sessionId}` trả trạng thái phiên để FE cập nhật sau return/IPN. IPN thất bại không tạo đơn; đơn trả trước chưa thanh toán vì vậy không xuất hiện trong danh sách/tìm kiếm đơn Admin.
- `POST /api/customer/orders/{ma}/vnpay` và `GET /api/customer/orders/{ma}/payment-status` chỉ còn phục vụ các đơn thanh toán cũ. Cấu hình sandbox `VNPAY_ENDPOINT`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL` và `VNPAY_FRONTEND_RETURN_URL`. Đăng ký tài khoản test tại [VNPay developer registration](https://pay.vnpaytest.vn/dev-registration). Môi trường local cần HTTPS tunnel cho IPN; chạy migration `DB/migrations/26_prepaid_checkout_sessions.sql` sau migration 25 trước khi deploy.
- Với đơn VNPay cần hoàn toàn bộ do hủy/trùng giao dịch, Admin mở chi tiết đơn và xác nhận gửi yêu cầu hoàn. Backend gọi VNPay Refund API tại `VNPAY_REFUND_ENDPOINT`, kiểm tra chữ ký phản hồi và chỉ đánh dấu `DA_HOAN_TIEN` khi phản hồi xác nhận trạng thái hoàn tất. Mã trạng thái VNPay đang xử lý được lưu là `DANG_HOAN_TIEN`; nếu kết quả mạng không rõ, gửi lại cùng mã yêu cầu để tránh tạo yêu cầu hoàn trùng. Cấu hình thêm `VNPAY_REFUND_ENDPOINT` (sandbox: `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`) và `VNPAY_SERVER_IP`; cần bật quyền Refund API cho merchant. Trước khi deploy, chạy migration 27 sau migration 26 và migration 28 cho hoàn tiền hậu mãi.

## Địa chỉ giao hàng (tỉnh / phường sau sáp nhập)
- Danh mục theo Nghị quyết 202/2025/QH15: 34 tỉnh/thành và 3.321 phường/xã/đặc khu, nằm trong `02_data.sql` (bảng `TinhThanh`, `PhuongXa`).
- `GET /api/locations/provinces` và `GET /api/locations/provinces/{maTinh}/wards` trả về danh sách `{ma, ten}` cho ô chọn.
- Địa chỉ lưu `maTinh`, `maPhuong` và chỉ cho nhập tay phần "số nhà, tên đường" (`diaChiNhanHang`). BE kiểm tra phường thuộc đúng tỉnh.
- Khách thêm / sửa địa chỉ ngay ở bước thanh toán; đổi địa chỉ sẽ tính lại phí vận chuyển theo tỉnh (bảng `PhiVanChuyen`, dòng `maTinh` rỗng là mức mặc định).

## Đánh giá sản phẩm
Mỗi đánh giá gắn với một dòng sản phẩm của một đơn hàng cụ thể (`DanhGia.maDonHang` + `maSanPham`, khóa ngoại tới `ChiTietDonHang`, duy nhất theo cặp này). BE kiểm tra lần lượt:
1. Đơn hàng thuộc về khách đang đăng nhập.
2. Đơn ở trạng thái **Đã giao** (thời điểm giao lưu ở `DonHang.ngayGiao`).
3. Sản phẩm có trong đơn đó.
4. Đơn không có yêu cầu đổi trả / hoàn tiền đang xử lý hoặc đã duyệt (bị từ chối thì vẫn được đánh giá).
5. Còn trong 30 ngày kể từ khi nhận hàng.
6. Chưa đánh giá sản phẩm này trong đơn (có ràng buộc UNIQUE ở CSDL chống gửi trùng).
Dữ liệu: 1 - 5 sao, nhận xét tối đa 1000 ký tự (bắt buộc từ 10 ký tự nếu chấm 1 - 2 sao), link ảnh phải là http(s). Khách đánh giá ở trang chi tiết đơn hàng; `GET /api/customer/reviews/order/{ma}` trả về trạng thái từng sản phẩm (được đánh giá / lý do chưa được / đánh giá đã gửi).

---

## Bổ sung (giao diện mới, 19/11/2026)

### GET `/api/search/popular?limit=12` (công khai)
Từ khóa được tìm nhiều và sản phẩm được xem nhiều trong 30 ngày, tổng hợp từ bảng `HanhViNguoiDung`. Không chứa thông tin cá nhân. Dùng cho cột "Được tìm nhiều trong 30 ngày" ở menu "Tất cả điện thoại".
```json
{ "soNgay": 30,
  "tuKhoa": [{ "tuKhoa": "iphone 16", "luot": 9 }],
  "sanPhamXemNhieu": [{ "maSanPham": "SP500498", "luot": 13 }] }
```

### GET `/api/customer/checkout/preview-selected?maDiaChi=...&chon=PB1&chon=PB2[&phuongThucTt=COD]` (CUSTOMER)
Xem trước thanh toán chỉ với các dòng giỏ hàng được chọn. Không thay đổi dữ liệu (transaction luôn rollback).

### POST `/api/customer/orders/selected?chon=PB1&chon=PB2` (CUSTOMER)
Header `Idempotency-Key`, body giống `POST /api/customer/orders`. Endpoint này chỉ tạo đơn COD ngay; các dòng chưa chọn được giữ lại trong giỏ. Dùng lại toàn bộ luồng đặt hàng hiện có (khóa tồn kho, voucher, phê duyệt bán, idempotency) trong cùng một transaction, lỗi ở bất kỳ bước nào sẽ rollback cả giỏ hàng. Với MoMo/VNPay, dùng `POST /api/customer/checkout/payment?chon=PB1&chon=PB2`; đơn chỉ được tạo sau IPN thành công.
- 400: chưa chọn sản phẩm / dùng cùng "Mua ngay".
- 409: dòng được chọn không còn trong giỏ (giỏ đã thay đổi).
- Gửi lại cùng `Idempotency-Key` sau khi thành công: trả lại đơn đã tạo.
