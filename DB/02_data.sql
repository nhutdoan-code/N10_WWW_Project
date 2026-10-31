USE BanDienThoai;

START TRANSACTION;

-- DỮ LIỆU CHA BẮT BUỘC
INSERT INTO TaiKhoan
    (maTk, email, soDienThoai, matKhau, trangThaiHoatDong, loaiDangNhap)
VALUES
('TK001', 'admin@phonenest.com', '0900000001', '$2a$10$Nbn8/QR7ux8nGgghO9zidejt47a021r4wVcq5NqIsIM6/RMkIEOqy', TRUE, 'LOCAL'),
('TK002', 'an@phonenest.com',    '0900000002', '$2a$10$NZy7J28Ep9fHK2oEtkXs/Og8ulqg0yYt9/DckXW6GGG9Yl7ojyJaC', TRUE, 'LOCAL'),
('TK003', 'binh@phonenest.com',  '0900000003', '$2a$10$NZy7J28Ep9fHK2oEtkXs/Og8ulqg0yYt9/DckXW6GGG9Yl7ojyJaC', TRUE, 'LOCAL'),
('TK004', 'hoang@phonenest.com', '0900000004', '$2y$10$8IJfBgj3GZ19coGUGISj0.q2OO5nRN2LvQ29O4MENuHwKfuw2s/UG', TRUE, 'LOCAL');
INSERT INTO QuanTriVien (maQtv,hoTen,vaiTro,chucVu) VALUES ('TK001','Quản trị viên PhoneNest','ADMIN','Quản trị hệ thống');
INSERT INTO QuanTriVien (maQtv,hoTen,vaiTro,chucVu) VALUES ('TK004','Quản trị viên PhoneNest','ADMIN','Quản trị hệ thống');
INSERT INTO KhachHang (maKh,hoTen,diemTichLuy,hangThanhVien) VALUES
('TK002','Nguyễn Văn An',0,'THUONG'),('TK003','Trần Thị Bình',0,'THUONG');

INSERT INTO TinhThanh (maTinh,tenTinh) VALUES
('79','Thành phố Hồ Chí Minh'),('01','Thành phố Hà Nội'),('48','Thành phố Đà Nẵng');
INSERT INTO PhuongXa (maPhuong,tenPhuong,maTinh) VALUES
('26890','Phường Hạnh Thông','79'),('26743','Phường Sài Gòn','79'),('27343','Phường Chợ Lớn','79');

INSERT INTO LoaiSanPham (maLoai,tenLoai,moTa,maLoaiCha) VALUES
('L10','iPhone','Điện thoại Apple iPhone',NULL),('L20','Samsung Galaxy','Điện thoại Samsung Galaxy',NULL),
('L30','Xiaomi / Redmi / POCO','Điện thoại Xiaomi, Redmi và POCO',NULL),('L40','OPPO','Điện thoại OPPO',NULL);
INSERT INTO LoaiSanPham (maLoai,tenLoai,moTa,maLoaiCha) VALUES
('L11','iPhone Pro','Dòng iPhone Pro','L10'),('L12','iPhone tiêu chuẩn','Dòng iPhone tiêu chuẩn','L10'),
('L21','Galaxy S','Samsung Galaxy S Series','L20'),('L22','Galaxy Z / A','Samsung Galaxy Z và A Series','L20'),
('L31','Redmi / POCO','Redmi và POCO','L30'),('L32','Xiaomi Series','Xiaomi Series','L30'),
('L41','Reno / A','OPPO Reno và A Series','L40'),('L42','Find','OPPO Find Series','L40');

INSERT INTO ThuongHieu (maThuongHieu,tenThuongHieu,moTa,trangThaiHoatDong) VALUES
('TH01','Apple','Apple iPhone',TRUE),('TH02','Samsung','Samsung Galaxy',TRUE),
('TH03','Xiaomi','Xiaomi, Redmi và POCO',TRUE),('TH04','OPPO','OPPO',TRUE);

-- Giá bán trong seed là DEMO PRICE cho đồ án, không phải giá thị trường real-time.
INSERT INTO SanPham
(maSanPham,maLoai,maThuongHieu,tenMay,moTa,kichThuocManHinh,loaiManHinh,tanSoQuet,chip,cameraSau,cameraTruoc,pin,congSuatSac,heDieuHanh,nfc,ho5G,trongLuong,thoiHanBaoHanhThang,trangThai,diemDanhGiaTb) VALUES
('SP001','L11','TH01','iPhone 15 Pro Max','iPhone 15 Pro Max khung Titan, chip A17 Pro, màn hình 6.7 inch 120Hz, camera chính 48MP.',6.70,'OLED',120,'Apple A17 Pro','48 MP + 12 MP + 12 MP','12',4441,27,'iOS',TRUE,TRUE,221,12,'DANG_KINH_DOANH',4.8),
('SP002','L12','TH01','iPhone 15','iPhone 15 với Dynamic Island, chip A16 Bionic, màn hình 6.1 inch, camera 48MP.',6.10,'OLED',60,'Apple A16 Bionic','48 MP + 12 MP','12',3349,20,'iOS',TRUE,TRUE,171,12,'DANG_KINH_DOANH',4.7),
('SP003','L12','TH01','iPhone 13','iPhone 13 chip A15 Bionic, màn hình 6.1 inch, camera kép 12MP, hỗ trợ 5G.',6.10,'OLED',60,'Apple A15 Bionic','12 MP + 12 MP','12',3240,20,'iOS',TRUE,TRUE,174,12,'DANG_KINH_DOANH',4.6),
('SP004','L21','TH02','Samsung Galaxy S24 Ultra','Galaxy S24 Ultra khung Titan, chip Snapdragon 8 Gen 3, camera 200MP, màn hình 6.8 inch 120Hz.',6.80,'Dynamic AMOLED 2X',120,'Snapdragon 8 Gen 3','200 MP + 50 MP + 12 MP + 10 MP','12',5000,45,'Android',TRUE,TRUE,232,12,'DANG_KINH_DOANH',4.8),
('SP005','L21','TH02','Samsung Galaxy S24+','Galaxy S24+ chip Exynos 2400, màn hình 6.7 inch 120Hz, camera 50MP, pin 4900mAh.',6.70,'Dynamic AMOLED 2X',120,'Exynos 2400','50 MP + 12 MP + 10 MP','12',4900,45,'Android',TRUE,TRUE,196,12,'DANG_KINH_DOANH',4.6),
('SP006','L22','TH02','Samsung Galaxy A55 5G','Galaxy A55 5G chip Exynos 1480, màn hình 6.6 inch 120Hz, camera 50MP chống rung OIS.',6.60,'Super AMOLED',120,'Exynos 1480','50 MP + 12 MP + 5 MP','32',5000,25,'Android',TRUE,TRUE,213,12,'DANG_KINH_DOANH',4.5),
('SP007','L32','TH03','Xiaomi 14','Xiaomi 14 chip Snapdragon 8 Gen 3, ống kính Leica, màn hình 6.36 inch 120Hz.',6.36,'AMOLED',120,'Snapdragon 8 Gen 3','50 MP + 50 MP + 50 MP','32',4610,90,'Android',TRUE,TRUE,193,12,'DANG_KINH_DOANH',4.6),
('SP008','L31','TH03','Redmi Note 13 Pro','Redmi Note 13 Pro camera 200MP OIS, màn hình AMOLED 6.67 inch 120Hz, sạc nhanh 67W.',6.67,'AMOLED',120,'MediaTek Helio G99 Ultra','200 MP + 8 MP + 2 MP','16',5000,67,'Android',TRUE,FALSE,188,12,'DANG_KINH_DOANH',4.4),
('SP009','L31','TH03','POCO X6 Pro 5G','POCO X6 Pro 5G chip Dimensity 8300 Ultra, màn hình AMOLED 120Hz, sạc nhanh 67W.',6.67,'AMOLED',120,'Dimensity 8300 Ultra','64 MP + 8 MP + 2 MP','16',5000,67,'Android',TRUE,TRUE,186,12,'DANG_KINH_DOANH',4.5),
('SP010','L31','TH03','Redmi 13C','Redmi 13C pin 5000mAh, màn hình 6.74 inch 90Hz, camera 50MP, giá rẻ.',6.74,'IPS LCD',90,'MediaTek Helio G85','50 MP + 2 MP','8',5000,18,'Android',FALSE,FALSE,192,12,'DANG_KINH_DOANH',0),
('SP011','L42','TH04','OPPO Find X7 Ultra','OPPO Find X7 Ultra camera Hasselblad 4 ống kính 50MP, chip Snapdragon 8 Gen 3, sạc 100W.',6.82,'AMOLED',120,'Snapdragon 8 Gen 3','50 MP + 50 MP + 50 MP + 50 MP','32',5000,100,'Android',TRUE,TRUE,221,12,'DANG_KINH_DOANH',4.7),
('SP012','L41','TH04','OPPO Reno11 F 5G','OPPO Reno11 F 5G camera 64MP, màn hình AMOLED 6.7 inch 120Hz, sạc nhanh 67W.',6.70,'AMOLED',120,'Dimensity 7050','64 MP + 8 MP + 2 MP','32',5000,67,'Android',TRUE,TRUE,177,12,'DANG_KINH_DOANH',4.5),
('SP013','L41','TH04','OPPO A3','OPPO A3 màn hình 6.67 inch 90Hz, pin 5100mAh, sạc nhanh 45W.',6.67,'IPS LCD',90,'Snapdragon 6s Gen 1','50 MP + 2 MP','5',5100,45,'Android',TRUE,TRUE,186,12,'DANG_KINH_DOANH',4.2),
('SP1793202488261001','L22','TH02','Samsung Galaxy A56 5G','Samsung Galaxy A56 5G chip Exynos 1580, màn hình 6.7 inch 120Hz, camera 50MP OIS, pin 5000mAh sạc 45W.',6.70,'Super AMOLED',120,'Exynos 1580','50 MP + 12 MP + 5 MP','12',5000,45,'Android 15, One UI 7',TRUE,TRUE,198,12,'DANG_KINH_DOANH',0);

INSERT INTO GioHang (maGioHang,maKh) VALUES ('GH002','TK002'),('GH003','TK003');
INSERT INTO Wishlist (maDanhSach,maKh) VALUES ('WL002','TK002'),('WL003','TK003');

-- Dữ liệu variant mẫu: mỗi record là một SKU bán được (RAM + bộ nhớ + màu).
-- Giá cùng cấu hình RAM/bộ nhớ thì bằng nhau; giá là DEMO PRICE.
INSERT INTO PhienBanSanPham (maPhienBan,maSanPham,sku,ram,boNho,mauSac,giaBan,giaNiemYet,soLuongTon,trangThai) VALUES
('PB001','SP001','PN-001-8-256-TITAN-TU-NHIEN',8,256,'Titan Tự Nhiên',29990000,30990000,10,'DANG_KINH_DOANH'),
('PB001B','SP001','PN-001-8-256-TITAN-DEN',8,256,'Titan Đen',29990000,30990000,5,'DANG_KINH_DOANH'),
('PB001D','SP001','PN-001-8-512-TITAN-TRANG',8,512,'Titan Trắng',33990000,34990000,0,'DANG_KINH_DOANH'),
('PB001C','SP001','PN-001-8-1024-TITAN-XANH',8,1024,'Titan Xanh',37990000,38990000,3,'DANG_KINH_DOANH'),
('PB002','SP002','PN-002-6-128-DEN',6,128,'Đen',19990000,20990000,15,'DANG_KINH_DOANH'),
('PB002B','SP002','PN-002-6-128-HONG',6,128,'Hồng',19990000,20990000,10,'DANG_KINH_DOANH'),
('PB002C','SP002','PN-002-6-256-XANH-DUONG',6,256,'Xanh Dương',21990000,22990000,8,'DANG_KINH_DOANH'),
('PB003','SP003','PN-003-4-128-DO',4,128,'Đỏ',12990000,13990000,0,'DANG_KINH_DOANH'),
('PB003B','SP003','PN-003-4-128-TRANG',4,128,'Trắng',12990000,13990000,0,'DANG_KINH_DOANH'),
('PB004','SP004','PN-004-12-256-DEN-TITAN',12,256,'Đen Titan',28990000,29990000,7,'DANG_KINH_DOANH'),
('PB004B','SP004','PN-004-12-512-XAM-TITAN',12,512,'Xám Titan',30990000,31990000,5,'DANG_KINH_DOANH'),
('PB005','SP005','PN-005-12-256-XANH-JADE',12,256,'Xanh Jade',25990000,26990000,4,'DANG_KINH_DOANH'),
('PB005B','SP005','PN-005-12-256-TIM-COBALT',12,256,'Tím Cobalt',25990000,26990000,4,'DANG_KINH_DOANH'),
('PB006','SP006','PN-006-8-128-XANH-BANG',8,128,'Xanh Băng',9990000,10990000,15,'DANG_KINH_DOANH'),
('PB006B','SP006','PN-006-8-256-VANG-CHANH',8,256,'Vàng Chanh',10990000,11990000,15,'DANG_KINH_DOANH'),
('PB007','SP007','PN-007-12-256-TRANG',12,256,'Trắng',19990000,20990000,6,'DANG_KINH_DOANH'),
('PB007B','SP007','PN-007-12-256-DEN',12,256,'Đen',19990000,20990000,4,'DANG_KINH_DOANH'),
('PB008','SP008','PN-008-8-256-DEN',8,256,'Đen',7490000,7990000,20,'DANG_KINH_DOANH'),
('PB008B','SP008','PN-008-8-256-XANH-DUONG',8,256,'Xanh Dương',7490000,7990000,20,'DANG_KINH_DOANH'),
('PB009','SP009','PN-009-12-512-DEN',12,512,'Đen',10990000,11990000,9,'DANG_KINH_DOANH'),
('PB009B','SP009','PN-009-12-512-XANH',12,512,'Xanh',10990000,11990000,9,'DANG_KINH_DOANH'),
('PB010','SP010','PN-010-4-128-XANH-NAVY',4,128,'Xanh Navy',3290000,3690000,25,'DANG_KINH_DOANH'),
('PB010B','SP010','PN-010-4-128-DEN-MIDNIGHT',4,128,'Đen Midnight',3290000,3690000,25,'DANG_KINH_DOANH'),
('PB010F','SP010','PN-010-8-256-TRANG',8,256,'Trắng',4290000,4690000,6,'DANG_KINH_DOANH'),
('PB011','SP011','PN-011-16-512-DEN',16,512,'Đen',23990000,24990000,3,'DANG_KINH_DOANH'),
('PB011B','SP011','PN-011-16-512-XANH-DUONG',16,512,'Xanh Dương',23990000,24990000,2,'DANG_KINH_DOANH'),
('PB012','SP012','PN-012-8-256-XANH-DUONG',8,256,'Xanh Dương',8990000,9490000,11,'DANG_KINH_DOANH'),
('PB012B','SP012','PN-012-8-256-TIM-SAN-HO',8,256,'Tím San Hô',8990000,9490000,11,'DANG_KINH_DOANH'),
('PB013','SP013','PN-013-6-128-DEN',6,128,'Đen',4990000,5490000,18,'DANG_KINH_DOANH'),
('PB013B','SP013','PN-013-6-128-XANH-DUONG',6,128,'Xanh Dương',4990000,5490000,17,'DANG_KINH_DOANH'),
('PB013C','SP013','PN-013-8-256-DEN',8,256,'Đen',5490000,5990000,10,'DANG_KINH_DOANH'),
('PB1793202488339002','SP1793202488261001','PN-1793202488261001-8-128-DEN',8,128,'Đen',9990000,9990000,10,'DANG_KINH_DOANH'),
('PB1793204752658080','SP1793202488261001','PN-1793202488261001-8-256-DEN',8,256,'Đen',10990000,10990000,5,'DANG_KINH_DOANH'),
('PB1793202488375003','SP1793202488261001','PN-1793202488261001-8-256-XAM',8,256,'Xám',10990000,10990000,13,'DANG_KINH_DOANH');
-- 13 sản phẩm seed không có ảnh gốc trong repo nên dùng chung placeholder
-- (/uploads/product-placeholder.svg), KHÔNG phải ảnh thật và không hotlink.
-- Galaxy A56 có 8 ảnh thật đã upload, file nằm trong BE/phonenest/uploads và
-- được whitelist trong .gitignore để seed reproducible.
INSERT INTO HinhAnhSanPham (maHinhAnh,maSanPham,duongDan,gocChup,anhChinh) VALUES
('HA0011','SP001','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0021','SP002','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0031','SP003','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0041','SP004','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0051','SP005','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0061','SP006','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0071','SP007','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0081','SP008','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0091','SP009','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0101','SP010','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0111','SP011','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0121','SP012','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA0131','SP013','/uploads/product-placeholder.svg','Fallback',TRUE),
('HA1793204752678081','SP1793202488261001','/uploads/d04df2f0-2635-413d-af63-7751a9f18603.webp',NULL,TRUE),
('HA1793204752679082','SP1793202488261001','/uploads/372dbc48-2222-4ce0-b636-78b48bfa3bbc.webp',NULL,FALSE),
('HA1793204752681083','SP1793202488261001','/uploads/d5e222c2-4ce0-4a11-8f01-9fee30fbab01.webp',NULL,FALSE),
('HA1793204752682084','SP1793202488261001','/uploads/50597215-8e0c-463e-95cc-482e18ec4fcc.webp',NULL,FALSE),
('HA1793204752683085','SP1793202488261001','/uploads/2729f436-67d2-4eac-a514-da057d086bc8.webp',NULL,FALSE),
('HA1793204752684086','SP1793202488261001','/uploads/1bd541ef-0fbd-40ae-a0e2-8869470ed948.webp',NULL,FALSE),
('HA1793204752689087','SP1793202488261001','/uploads/3a8cf13b-36cd-4660-be78-b41a8e7f751e.webp',NULL,FALSE),
('HA1793204752690088','SP1793202488261001','/uploads/26b54904-ccdc-402c-be95-d35263d2b515.webp',NULL,FALSE);
INSERT INTO ChiTietGioHang (maGioHang,maPhienBan,soLuong) VALUES ('GH002','PB1793202488339002',1),('GH003','PB006',1);
INSERT INTO WishlistSanPham (maDanhSach,maSanPham) VALUES ('WL002','SP004');
INSERT INTO KhuyenMai (maKm, tenKm, loai, phanTramGiam, soTienGiamToiDa, donToiThieu, soLuong, chiKhachMoi, gioiHanMoiKhach, ngayBatDau, ngayKetThuc, dieuKienApDung) VALUES
('NEST10','Giảm 10% đơn hàng','DON_HANG',10,2000000,0,NULL,FALSE,1,'2026-01-01','2026-12-31','Áp dụng cho mọi đơn hàng'),
('WELCOME5','Chào bạn mới giảm 5%','DON_HANG',5,500000,0,NULL,TRUE,1,'2026-01-01','2026-12-31','Chỉ dành cho đơn hàng đầu tiên'),
('SALE10TR','Giảm 8% cho đơn từ 10 triệu','DON_HANG',8,1500000,10000000,100,FALSE,1,'2026-01-01','2026-12-31','Đơn hàng từ 10.000.000đ, số lượng có hạn'),
('FREESHIP','Miễn phí vận chuyển','VAN_CHUYEN',100,30000,500000,NULL,FALSE,1,'2026-01-01','2026-12-31','Đơn hàng từ 500.000đ, giảm tối đa 30.000đ phí giao hàng'),
('SHIP50','Giảm 50% phí vận chuyển','VAN_CHUYEN',50,25000,0,NULL,FALSE,1,'2026-01-01','2026-12-31','Áp dụng cho mọi đơn hàng'),
('HETHAN20','Khuyến mãi đã hết hạn (dùng để thử lỗi)','DON_HANG',20,3000000,0,NULL,FALSE,1,'2025-01-01','2025-12-31',NULL);
INSERT INTO PhiVanChuyen (maPhi, maTinh, phiCoBan, mienPhiTu, thoiGianGiao) VALUES
('PVC01','79',25000,3000000,'1 - 2 ngày'),
('PVC02','01',40000,5000000,'2 - 3 ngày'),
('PVC03','48',40000,5000000,'2 - 3 ngày'),
('PVC99',NULL,50000,10000000,'3 - 5 ngày');
INSERT INTO DiaChi (maDiaChi,maKh,tenNguoiNhan,soDienThoaiNhan,diaChiNhanHang,maTinh,maPhuong,macDinh) VALUES
('DC001','TK002','Nguyễn Văn An','0900000002','12 Nguyễn Văn Bảo','79','26890',TRUE),
('DC002','TK002','Nguyễn Văn An (công ty)','0900000002','45 Lê Lợi','79','26743',FALSE),
('DC003','TK003','Trần Thị Bình','0900000003','88 Trần Hưng Đạo','79','27343',TRUE);
INSERT INTO DonHang (maDonHang,maKh,maKm,tenNguoiNhan,soDienThoaiNhan,diaChiGiaoHang,tamTinh,giamGia,phiVanChuyen,giamPhiVanChuyen,tongThanhToan,ngayMua,ngayCapNhat,ngayGiao,trangThai) VALUES
('DH001','TK002',NULL,'Nguyễn Văn An','0900000002','12 Nguyễn Văn Bảo, Phường Hạnh Thông, Thành phố Hồ Chí Minh',3290000,0,25000,0,3315000,'2026-09-20 10:30:00','2026-09-22 16:00:00','2026-09-22 16:00:00','DA_GIAO'),
('DH002','TK002','NEST10','Nguyễn Văn An','0900000002','12 Nguyễn Văn Bảo, Phường Hạnh Thông, Thành phố Hồ Chí Minh',27480000,2000000,25000,0,25505000,'2026-10-12 14:05:00','2026-10-12 14:05:00',NULL,'CHO_XAC_NHAN'),
('DH003','TK003',NULL,'Trần Thị Bình','0900000003','88 Trần Hưng Đạo, Phường Chợ Lớn, Thành phố Hồ Chí Minh',28990000,0,25000,0,29015000,'2026-10-14 09:15:00','2026-10-15 08:30:00',NULL,'DANG_GIAO');
INSERT INTO ChiTietDonHang (maChiTiet,maDonHang,maPhienBan,tenSanPham,tenPhienBan,sku,hinhAnh,soLuong,donGia,baoHanhThang) VALUES
('CTDH001','DH001','PB010','Redmi 13C','4GB / 128GB / Xanh Navy','PN-010-4-128-XANH-NAVY','/uploads/product-placeholder.svg',1,3290000,12),
('CTDH002','DH002','PB002','iPhone 15','6GB / 128GB / Đen','PN-002-6-128-DEN','/uploads/product-placeholder.svg',1,19990000,12),
('CTDH003','DH002','PB008','Redmi Note 13 Pro','8GB / 256GB / Đen','PN-008-8-256-DEN','/uploads/product-placeholder.svg',1,7490000,12),
('CTDH004','DH003','PB004','Samsung Galaxy S24 Ultra','12GB / 256GB / Đen Titan','PN-004-12-256-DEN-TITAN','/uploads/product-placeholder.svg',1,28990000,12);
INSERT INTO LichSuTrangThaiDonHang (maLichSu,maDonHang,trangThai,thoiGian,ghiChu,nguoiCapNhat) VALUES
('LS001','DH001','CHO_XAC_NHAN','2026-09-20 10:30:00','Đơn hàng được tạo','TK002'),('LS002','DH001','DA_XAC_NHAN','2026-09-20 11:00:00','Đã xác nhận đơn','TK001'),('LS003','DH001','DANG_CHUAN_BI','2026-09-20 13:00:00','Đang chuẩn bị hàng','TK001'),('LS004','DH001','DANG_GIAO','2026-09-21 08:00:00','Đã bàn giao đơn vị vận chuyển','TK001'),('LS005','DH001','DA_GIAO','2026-09-22 16:00:00','Giao hàng thành công','TK001'),
('LS006','DH002','CHO_XAC_NHAN','2026-10-12 14:05:00','Đơn hàng được tạo','TK002'),
('LS007','DH003','CHO_XAC_NHAN','2026-10-14 09:15:00','Đơn hàng được tạo','TK003'),('LS008','DH003','DA_XAC_NHAN','2026-10-14 10:00:00','Đã xác nhận đơn','TK001'),('LS009','DH003','DANG_CHUAN_BI','2026-10-14 14:00:00','Đang chuẩn bị hàng','TK001'),('LS010','DH003','DANG_GIAO','2026-10-15 08:30:00','Đã bàn giao đơn vị vận chuyển','TK001');
INSERT INTO ThanhToan (maThanhToan,maDonHang,phuongThuc,soTien,trangThai,maGiaoDich,ngayTao,ngayThanhToan) VALUES
('TT001','DH001','COD',3315000,'DA_THANH_TOAN',NULL,'2026-09-20 10:30:00','2026-09-22 16:00:00'),
('TT002','DH002','CHUYEN_KHOAN',25505000,'CHO_THANH_TOAN',NULL,'2026-10-12 14:05:00',NULL),
('TT003','DH003','VI_DIEN_TU',29015000,'DA_THANH_TOAN','GD-DH003','2026-10-14 09:15:00','2026-10-14 09:16:00');
INSERT INTO SuDungKhuyenMai (maSuDung,maKm,maKh,maDonHang,ngaySuDung) VALUES ('SDKM001','NEST10','TK002','DH002','2026-10-12 14:05:00');
INSERT INTO BienDongKho (maBienDong,maPhienBan,loaiBienDong,soLuong,tonTruoc,tonSau,maDonHang,thoiGian,ghiChu) VALUES
('BDK001','PB010','BAN_HANG',-1,26,25,'DH001','2026-09-20 10:30:00','Xuất kho cho đơn DH001'),
('BDK002','PB002','BAN_HANG',-1,16,15,'DH002','2026-10-12 14:05:00','Xuất kho cho đơn DH002'),
('BDK003','PB008','BAN_HANG',-1,21,20,'DH002','2026-10-12 14:05:00','Xuất kho cho đơn DH002'),
('BDK004','PB004','BAN_HANG',-1,8,7,'DH003','2026-10-14 09:15:00','Xuất kho cho đơn DH003');
INSERT INTO DanhGia (maDanhGia,maKh,maChiTietDonHang,maSanPham,diemSao,noiDungBinhLuan,hinhAnhThucTe,ngayDanhGia) VALUES ('DG001','TK002','CTDH001','SP010',5,'Máy rẻ mà dùng ổn, pin trâu, giao hàng nhanh.',NULL,'2026-09-25 10:00:00');
UPDATE SanPham SET diemDanhGiaTb=5.0 WHERE maSanPham='SP010';
INSERT INTO HoiDap (maCauHoi,maKh,maSanPham,noiDungCauHoi,noiDungTraLoi,ngayHoi,ngayTraLoi) VALUES
('HD001','TK002','SP004','Máy có hỗ trợ sạc nhanh 45W không?','Có, Galaxy S24 Ultra hỗ trợ sạc nhanh 45W (cần củ sạc riêng).','2026-10-01','2026-10-02'),
('HD002','TK003','SP002','Máy có bản màu hồng không ạ?',NULL,'2026-10-14',NULL);
INSERT INTO ThongBao (maThongBao,maTk,tieuDe,noiDung,loai,daDoc,ngayTao) VALUES
('TB001','TK002','Đơn hàng đã giao','Đơn hàng DH001 đã được giao thành công. Cảm ơn bạn đã mua sắm tại PhoneNest!','DON_HANG',FALSE,'2026-09-22 16:00:00'),
('TB002','TK003','Đơn hàng đang giao','Đơn hàng DH003 đang được giao đến bạn.','DON_HANG',FALSE,'2026-10-15 08:30:00');

COMMIT;
