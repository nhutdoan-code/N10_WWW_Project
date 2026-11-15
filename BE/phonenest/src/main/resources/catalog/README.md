# Ảnh gốc catalog (dữ liệu demo)

- Phục vụ tại `/catalog/<maSanPham>/<mau>.webp` (cấu hình ở `config/WebConfig`, public GET trong `SecurityConfig`,
  Vite proxy `/catalog`). Đóng gói cùng backend nên mọi thành viên clone repo đều có ảnh, không cần copy thủ công.
- Mỗi màu của mỗi sản phẩm đang bán có **1 ảnh** (mặt trước và mặt sau). `HinhAnhSanPham.mauSac` gắn ảnh với màu,
  nên khi khách chọn màu, trang chi tiết / giỏ hàng hiển thị đúng ảnh màu đó.
- Ảnh Admin tải lên sau này được lưu trong `uploads/` (`app.upload-dir`, không commit) với đường dẫn `/uploads/...`;
  thư mục này **không bị ghi đè hay xóa** khi Admin đổi ảnh (xóa ảnh trong form chỉ xóa bản ghi trong database).
- Ảnh chụp sản phẩm thật (mặt trước + mặt sau, nền trắng), **không** dùng ảnh quảng cáo có logo "Galaxy AI", người mẫu hay chữ khuyến mãi. Nguồn từng ảnh: `nguon-anh.csv` (ưu tiên Thế Giới Di Động, còn lại CellphoneS; lấy ngày 15/11/2026). Ảnh thuộc bản quyền của
  hãng / nhà bán lẻ, **chỉ dùng cho mục đích học tập và demo**, không dùng cho mục đích thương mại.
- Thêm ảnh gốc cho sản phẩm mới: đặt file vào `catalog/<maSanPham>/<mau-khong-dau>.webp` rồi thêm bản ghi
  `HinhAnhSanPham` với `duongDan = /catalog/...` và `mauSac` đúng tên màu của phiên bản.
