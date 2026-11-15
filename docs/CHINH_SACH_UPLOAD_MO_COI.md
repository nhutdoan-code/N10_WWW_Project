# Chính sách xử lý ảnh upload mồ côi (2B.7E)

**Trạng thái: chỉ triển khai KIỂM KÊ (dry-run). Chức năng xóa CHƯA được xây dựng và chưa xóa file nào.**

## 1. Nhận diện

`GET /api/admin/uploads/orphans?gioCho=24` (chỉ ADMIN) — `UploadOrphanAuditService`:

1. Chỉ quét bên trong thư mục `app.upload-dir` (đường dẫn thật sau khi phân giải). Không theo symlink;
   file nằm ngoài thư mục gốc bị bỏ qua và được đếm riêng.
2. "Được tham chiếu" = chuỗi `/uploads/<tên>` xuất hiện ở **bất kỳ cột văn bản nào** trong database: ảnh sản phẩm,
   ảnh trong **đơn hàng lịch sử** (`chitietdonhang.hinhAnh`), ảnh đổi trả, ảnh đánh giá, ảnh đại diện, dữ liệu
   trước/sau trong nhật ký audit, bản lưu phản hồi checkout. Không dùng danh sách cột cố định để không bỏ sót ảnh lịch sử.
3. File mới hơn **thời gian chờ** (mặc định 24 giờ, tối thiểu 1 giờ) không bao giờ là ứng viên — có thể vừa upload
   nhưng giao dịch lưu sản phẩm chưa hoàn tất hoặc đã thất bại và người dùng đang thử lại.
4. Chỉ file ảnh (`jpg, jpeg, png, webp, gif, svg`) mới có thể là ứng viên; file khác (README, cấu hình) chỉ được đếm.
5. Báo cáo có `maBaoCao`, thời điểm, `bamDanhSachUngVien` (SHA-256 của danh sách đường dẫn + kích thước) và log
   `KIEM KE UPLOAD (dry-run)` để kiểm tra lại. Báo cáo cũng nêu số tham chiếu trong DB **không có file** (ảnh bị thiếu).

## 2. Quy trình xóa đề xuất (CHƯA làm — cần phê duyệt)

1. Chạy kiểm kê, lưu báo cáo (mã + hash) và gửi cho người phê duyệt.
2. Người phê duyệt xem danh sách, loại trừ file cần giữ vì lý do nghiệp vụ (vd. bằng chứng, ảnh đã cách ly để điều tra).
3. Bước xóa (nếu được xây dựng sau này) phải: nhận `maBaoCao` + hash đã duyệt; **chạy lại kiểm kê** và chỉ tiếp tục nếu
   hash trùng; **di chuyển** file sang thư mục cách ly ngoài web root (không xóa ngay) kèm manifest; ghi audit
   `DELETE_ORPHAN_UPLOAD` có người thực hiện; chỉ xóa vĩnh viễn sau thời hạn lưu cách ly (vd. 30 ngày).
4. Không bao giờ xóa file đang được tham chiếu, file trong thời gian chờ, hoặc ngoài thư mục upload.

## 3. Kết quả kiểm kê thật (14/11/2026, dry-run)

Thư mục `uploads/` của repo (16 file) đối chiếu dữ liệu của clone diễn tập (bản chụp DB chính đã nâng cấp):
9 file được tham chiếu; 6 ứng viên `.webp` (cả 6 trùng SHA-256 với **6 ảnh PIL giả** đã nằm trong danh sách cách ly
2B.7B); `README.txt` (từ 2B.7E được loại khỏi ứng viên vì không phải ảnh); 294 tham chiếu trong DB không có file tương ứng.
Không file nào bị xóa (theo quyết định: chưa xóa ảnh PIL).
