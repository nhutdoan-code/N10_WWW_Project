USE BanDienThoai;

-- Tài khoản mẫu: admin@phonenest.vn / Admin@123 ; khach1@phonenest.vn, khach2@phonenest.vn / Khach@123
INSERT INTO TaiKhoan (maTk, email, matKhau, ngayTao, trangThaiHoatDong, loaiDangNhap, soDienThoai) VALUES
('TK001','admin@phonenest.vn','$2b$10$0l8znp8sxNHh9V7672RbcOCdc72CibtEMrNNnuyffDioxt7Wg9pC2','2026-01-01',TRUE,'LOCAL','0900000001'),
('TK002','khach1@phonenest.vn','$2b$10$jpSueEFLK.zJJgFYkJAMFeRM/XuupC1VTdh.CARugNW6v1Dx.4ZRS','2026-02-10',TRUE,'LOCAL','0900000002'),
('TK003','khach2@phonenest.vn','$2b$10$jpSueEFLK.zJJgFYkJAMFeRM/XuupC1VTdh.CARugNW6v1Dx.4ZRS','2026-03-15',TRUE,'LOCAL','0900000003');

INSERT INTO QuanTriVien (maQtv, hoTen, vaiTro, chucVu) VALUES ('TK001','Quản trị viên PhoneNest','ADMIN','Quản lý cửa hàng');

INSERT INTO KhachHang (maKh, hoTen, ngaySinh, anhDaiDien, diemTichLuy, hangThanhVien, maGioiThieu) VALUES
('TK002','Nguyễn Văn An','2003-05-12',NULL,120,'Bạc',NULL),
('TK003','Trần Thị Bình','2002-09-30',NULL,0,'Đồng',NULL);

INSERT INTO GioHang (maGioHang, maKh, ngayTao) VALUES ('GH002','TK002','2026-02-10'),('GH003','TK003','2026-03-15');
INSERT INTO Wishlist (maDanhSach, maKh) VALUES ('WL002','TK002'),('WL003','TK003');

INSERT INTO LoaiSanPham (maLoai, tenLoai, moTa) VALUES
('L01','Apple','Điện thoại iPhone chính hãng'),
('L02','Samsung','Dòng Galaxy S, Z, A'),
('L03','Xiaomi','Xiaomi, Redmi, POCO'),
('L04','OPPO','Dòng Find, Reno, A');

INSERT INTO SanPham (maSanPham, maLoai, tenMay, giaBan, moTa, thongSoKyThuat, soLuongTon, trangThai, diemDanhGiaTb) VALUES
('SP001','L01','iPhone 15 Pro Max 256GB',29990000,'Khung titan, chip A17 Pro, camera zoom 5x.','Màn hình 6.7 inch OLED 120Hz | Chip A17 Pro | RAM 8GB | Bộ nhớ 256GB | Camera 48MP + 12MP + 12MP | Pin 4422mAh',15,'CON_HANG',4.8),
('SP002','L01','iPhone 15 128GB',19990000,'Dynamic Island, camera 48MP, cổng USB-C.','Màn hình 6.1 inch OLED | Chip A16 Bionic | RAM 6GB | Bộ nhớ 128GB | Camera 48MP + 12MP | Pin 3349mAh',25,'CON_HANG',4.7),
('SP003','L01','iPhone 13 128GB',12990000,'Giá tốt, hiệu năng vẫn mạnh mẽ.','Màn hình 6.1 inch OLED | Chip A15 Bionic | RAM 4GB | Bộ nhớ 128GB | Camera 12MP + 12MP | Pin 3240mAh',0,'HET_HANG',4.6),
('SP004','L02','Samsung Galaxy S24 Ultra 256GB',28990000,'Bút S Pen, Galaxy AI, camera 200MP.','Màn hình 6.8 inch AMOLED 120Hz | Snapdragon 8 Gen 3 | RAM 12GB | Bộ nhớ 256GB | Camera 200MP + 50MP + 12MP + 10MP | Pin 5000mAh',12,'CON_HANG',4.8),
('SP005','L02','Samsung Galaxy Z Flip6 256GB',25990000,'Điện thoại gập nhỏ gọn, thời trang.','Màn hình 6.7 inch AMOLED gập | Snapdragon 8 Gen 3 | RAM 12GB | Bộ nhớ 256GB | Camera 50MP + 12MP | Pin 4000mAh',8,'CON_HANG',4.5),
('SP006','L02','Samsung Galaxy A55 5G 128GB',9990000,'Thiết kế khung kim loại, pin bền.','Màn hình 6.6 inch Super AMOLED 120Hz | Exynos 1480 | RAM 8GB | Bộ nhớ 128GB | Camera 50MP + 12MP + 5MP | Pin 5000mAh',30,'CON_HANG',4.4),
('SP007','L03','Xiaomi 14 12GB/256GB',19990000,'Ống kính Leica, sạc nhanh 90W.','Màn hình 6.36 inch AMOLED 120Hz | Snapdragon 8 Gen 3 | RAM 12GB | Bộ nhớ 256GB | Camera 50MP Leica x3 | Pin 4610mAh',10,'CON_HANG',4.6),
('SP008','L03','Redmi Note 13 Pro 8GB/256GB',7490000,'Camera 200MP, màn hình AMOLED 120Hz.','Màn hình 6.67 inch AMOLED 120Hz | Snapdragon 7s Gen 2 | RAM 8GB | Bộ nhớ 256GB | Camera 200MP + 8MP + 2MP | Pin 5100mAh',40,'CON_HANG',4.5),
('SP009','L03','POCO F6 12GB/512GB',10990000,'Hiệu năng chơi game mạnh trong tầm giá.','Màn hình 6.67 inch AMOLED 120Hz | Snapdragon 8s Gen 3 | RAM 12GB | Bộ nhớ 512GB | Camera 50MP + 8MP | Pin 5000mAh',18,'CON_HANG',4.6),
('SP010','L03','Redmi 13C 4GB/128GB',3290000,'Điện thoại giá rẻ cho học sinh, sinh viên.','Màn hình 6.74 inch IPS 90Hz | Helio G85 | RAM 4GB | Bộ nhớ 128GB | Camera 50MP + 2MP | Pin 5000mAh',50,'CON_HANG',4.1),
('SP011','L04','OPPO Find X7 Ultra 16GB/512GB',23990000,'Hai camera tiềm vọng, hợp tác Hasselblad.','Màn hình 6.82 inch AMOLED 120Hz | Snapdragon 8 Gen 3 | RAM 16GB | Bộ nhớ 512GB | Camera 50MP x4 | Pin 5000mAh',6,'CON_HANG',4.7),
('SP012','L04','OPPO Reno12 F 8GB/256GB',8990000,'Thiết kế mỏng nhẹ, camera chân dung đẹp.','Màn hình 6.67 inch AMOLED 120Hz | Helio G100 | RAM 8GB | Bộ nhớ 256GB | Camera 50MP + 8MP + 2MP | Pin 5000mAh',22,'CON_HANG',4.3),
('SP013','L04','OPPO A3 6GB/128GB',4990000,'Bền bỉ, chống nước IP54.','Màn hình 6.67 inch IPS 90Hz | Snapdragon 6s Gen 1 | RAM 6GB | Bộ nhớ 128GB | Camera 50MP + 2MP | Pin 5100mAh',35,'CON_HANG',4.2);

INSERT INTO HinhAnhSanPham (maHinhAnh, maSanPham, duongDan, gocChup, anhChinh) VALUES
('HA0011','SP001','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=iPhone+15','Mặt trước',TRUE),
('HA0012','SP001','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=iPhone+15+-+Mat+sau','Mặt sau',FALSE),
('HA0021','SP002','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=iPhone+15','Mặt trước',TRUE),
('HA0022','SP002','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=iPhone+15+-+Mat+sau','Mặt sau',FALSE),
('HA0031','SP003','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=iPhone+13','Mặt trước',TRUE),
('HA0032','SP003','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=iPhone+13+-+Mat+sau','Mặt sau',FALSE),
('HA0041','SP004','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=Samsung+Galaxy','Mặt trước',TRUE),
('HA0042','SP004','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=Samsung+Galaxy+-+Mat+sau','Mặt sau',FALSE),
('HA0051','SP005','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=Samsung+Galaxy','Mặt trước',TRUE),
('HA0052','SP005','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=Samsung+Galaxy+-+Mat+sau','Mặt sau',FALSE),
('HA0061','SP006','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=Samsung+Galaxy','Mặt trước',TRUE),
('HA0062','SP006','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=Samsung+Galaxy+-+Mat+sau','Mặt sau',FALSE),
('HA0071','SP007','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=Xiaomi+14','Mặt trước',TRUE),
('HA0072','SP007','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=Xiaomi+14+-+Mat+sau','Mặt sau',FALSE),
('HA0081','SP008','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=Redmi+Note','Mặt trước',TRUE),
('HA0082','SP008','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=Redmi+Note+-+Mat+sau','Mặt sau',FALSE),
('HA0091','SP009','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=POCO+F6','Mặt trước',TRUE),
('HA0092','SP009','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=POCO+F6+-+Mat+sau','Mặt sau',FALSE),
('HA0101','SP010','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=Redmi+13C','Mặt trước',TRUE),
('HA0102','SP010','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=Redmi+13C+-+Mat+sau','Mặt sau',FALSE),
('HA0111','SP011','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=OPPO+Find','Mặt trước',TRUE),
('HA0112','SP011','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=OPPO+Find+-+Mat+sau','Mặt sau',FALSE),
('HA0121','SP012','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=OPPO+Reno12','Mặt trước',TRUE),
('HA0122','SP012','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=OPPO+Reno12+-+Mat+sau','Mặt sau',FALSE),
('HA0131','SP013','https://placehold.co/600x600/e8eefc/1e3a8a/png?text=OPPO+A3','Mặt trước',TRUE),
('HA0132','SP013','https://placehold.co/600x600/dbeafe/1e3a8a/png?text=OPPO+A3+-+Mat+sau','Mặt sau',FALSE);

INSERT INTO ChiTietGioHang (maGioHang, maSanPham, soLuong) VALUES ('GH003','SP006',1);
INSERT INTO WishlistSanPham (maDanhSach, maSanPham) VALUES ('WL002','SP004');

INSERT INTO KhuyenMai (maKm, tenKm, loai, phanTramGiam, soTienGiamToiDa, donToiThieu, soLuong, chiKhachMoi, ngayBatDau, ngayKetThuc, dieuKienApDung) VALUES
('NEST10','Giảm 10% đơn hàng','DON_HANG',10,2000000,0,NULL,FALSE,'2026-01-01','2026-12-31','Áp dụng cho mọi đơn hàng'),
('WELCOME5','Chào bạn mới giảm 5%','DON_HANG',5,500000,0,NULL,TRUE,'2026-01-01','2026-12-31','Chỉ dành cho đơn hàng đầu tiên'),
('SALE10TR','Giảm 8% cho đơn từ 10 triệu','DON_HANG',8,1500000,10000000,100,FALSE,'2026-01-01','2026-12-31','Đơn hàng từ 10.000.000đ, số lượng có hạn'),
('FREESHIP','Miễn phí vận chuyển','VAN_CHUYEN',100,30000,500000,NULL,FALSE,'2026-01-01','2026-12-31','Đơn hàng từ 500.000đ, giảm tối đa 30.000đ phí giao hàng'),
('SHIP50','Giảm 50% phí vận chuyển','VAN_CHUYEN',50,25000,0,NULL,FALSE,'2026-01-01','2026-12-31','Áp dụng cho mọi đơn hàng'),
('HETHAN20','Khuyến mãi đã hết hạn (dùng để thử lỗi)','DON_HANG',20,3000000,0,NULL,FALSE,'2025-01-01','2025-12-31',NULL);

INSERT INTO PhiVanChuyen (maPhi, khuVuc, phiCoBan, mienPhiTu, thoiGianGiao) VALUES
('PVC01','TP. Hồ Chí Minh',25000,3000000,'1 - 2 ngày'),
('PVC02','Hà Nội',40000,5000000,'2 - 3 ngày'),
('PVC03','Đà Nẵng',40000,5000000,'2 - 3 ngày'),
('PVC99','Khác',50000,10000000,'3 - 5 ngày');

INSERT INTO DonHang (maDonHang, maKh, maKm, ngayMua, phuongThucTt, trangThai) VALUES
('DH001','TK002',NULL,'2026-09-20 10:30:00','COD','DA_GIAO'),
('DH002','TK002','NEST10','2026-10-12 14:05:00','CHUYEN_KHOAN','CHO_XAC_NHAN'),
('DH003','TK003',NULL,'2026-10-14 09:15:00','VI_DIEN_TU','DANG_GIAO');

INSERT INTO ChiTietDonHang (maDonHang, maSanPham, soLuong, donGia) VALUES
('DH001','SP010',1,3290000),
('DH002','SP002',1,19990000),
('DH002','SP008',1,7490000),
('DH003','SP004',1,28990000);

-- Danh mục cha - con (Tree Pattern): hãng -> dòng máy
INSERT INTO LoaiSanPham (maLoai, tenLoai, moTa, maLoaiCha) VALUES
('L11','iPhone 15 Series','iPhone 15, 15 Pro, 15 Pro Max','L01'),
('L12','iPhone 13 Series','iPhone 13 và các đời cũ','L01'),
('L21','Galaxy S','Dòng cao cấp Galaxy S','L02'),
('L22','Galaxy Z / A','Dòng gập Z và tầm trung A','L02'),
('L31','Redmi / POCO','Dòng giá rẻ và hiệu năng','L03'),
('L41','Reno / A','Dòng tầm trung Reno và A','L04');

UPDATE SanPham SET maLoai='L11' WHERE maSanPham IN ('SP001','SP002');
UPDATE SanPham SET maLoai='L12' WHERE maSanPham = 'SP003';
UPDATE SanPham SET maLoai='L21' WHERE maSanPham = 'SP004';
UPDATE SanPham SET maLoai='L22' WHERE maSanPham IN ('SP005','SP006');
UPDATE SanPham SET maLoai='L31' WHERE maSanPham IN ('SP008','SP009','SP010');
UPDATE SanPham SET maLoai='L41' WHERE maSanPham IN ('SP012','SP013');

-- Sổ địa chỉ giao hàng
INSERT INTO DiaChi (maDiaChi, maKh, tenNguoiNhan, soDienThoaiNhan, diaChiNhanHang, tinhThanhPho, macDinh) VALUES
('DC001','TK002','Nguyễn Văn An','0900000002','12 Nguyễn Văn Bảo, Phường 4, Quận Gò Vấp','TP. Hồ Chí Minh',TRUE),
('DC002','TK002','Nguyễn Văn An (công ty)','0900000002','45 Lê Lợi, Quận 1','TP. Hồ Chí Minh',FALSE),
('DC003','TK003','Trần Thị Bình','0900000003','88 Trần Hưng Đạo, Quận 5','TP. Hồ Chí Minh',TRUE);

-- Phí vận chuyển và giảm giá của các đơn mẫu
UPDATE DonHang SET phiVanChuyen=25000 WHERE maDonHang IN ('DH001','DH003');
UPDATE DonHang SET phiVanChuyen=25000, giamGia=2000000 WHERE maDonHang='DH002';

-- Địa chỉ và thời điểm cập nhật cho các đơn mẫu
UPDATE DonHang SET diaChiGiaoHang='Nguyễn Văn An - 0900000002 - 12 Nguyễn Văn Bảo, Phường 4, Quận Gò Vấp, TP. Hồ Chí Minh', ngayCapNhat='2026-09-22 16:00:00' WHERE maDonHang='DH001';
UPDATE DonHang SET diaChiGiaoHang='Nguyễn Văn An - 0900000002 - 12 Nguyễn Văn Bảo, Phường 4, Quận Gò Vấp, TP. Hồ Chí Minh', ngayCapNhat='2026-10-12 14:05:00' WHERE maDonHang='DH002';
UPDATE DonHang SET diaChiGiaoHang='Trần Thị Bình - 0900000003 - 88 Trần Hưng Đạo, Quận 5, TP. Hồ Chí Minh', ngayCapNhat='2026-10-15 08:30:00' WHERE maDonHang='DH003';

-- Đánh giá (khách đã nhận hàng DH001), hỏi đáp, thông báo, đổi trả
INSERT INTO DanhGia (maDanhGia, maKh, maSanPham, diemSao, noiDungBinhLuan, hinhAnhThucTe, ngayDanhGia) VALUES
('DG001','TK002','SP010',5,'Máy rẻ mà dùng ổn, pin trâu, giao hàng nhanh.',NULL,'2026-09-25');
UPDATE SanPham SET diemDanhGiaTb = 5.0 WHERE maSanPham = 'SP010';

INSERT INTO HoiDap (maCauHoi, maKh, maSanPham, noiDungCauHoi, noiDungTraLoi, ngayHoi, ngayTraLoi) VALUES
('HD001','TK002','SP004','Máy có hỗ trợ sạc nhanh 45W không?','Có, Galaxy S24 Ultra hỗ trợ sạc nhanh 45W (cần củ sạc riêng).','2026-10-01','2026-10-02'),
('HD002','TK003','SP002','Máy có bản màu hồng không ạ?',NULL,'2026-10-14',NULL);

INSERT INTO ThongBao (maThongBao, maTk, noiDung, daDoc, ngayTao) VALUES
('TB001','TK002','Đơn hàng DH001 đã được giao thành công. Cảm ơn bạn đã mua sắm tại PhoneNest!',FALSE,'2026-09-22 16:00:00'),
('TB002','TK003','Đơn hàng DH003 đang được giao đến bạn.',FALSE,'2026-10-15 08:30:00');
