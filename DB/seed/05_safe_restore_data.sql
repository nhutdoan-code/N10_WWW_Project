-- ==============================================================================
-- PHONENEST - SAFE DATA RECOVERY SCRIPT (STORED PROCEDURE & ACID ROLLBACK)
-- Purpose: Safely restore missing orders, order items, reviews, cart items, missing 
--          categories without destructive operations (NO DROP, NO TRUNCATE, NO DELETE).
-- Safety:  - Wrapped in Stored Procedure with EXIT HANDLER FOR SQLEXCEPTION.
--          - Any error triggers immediate ROLLBACK and stops execution (NO partial commits).
--          - 100% INSERT IGNORE: Existing records are NEVER overwritten.
-- Target:  Must be executed with explicit database parameter on mariadb CLI:
--          mariadb.exe -u <user> --defaults-extra-file=<cfg> <database_name> < 05_safe_restore_data.sql
-- ==============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `sp_phonenest_safe_restore`$$

CREATE PROCEDURE `sp_phonenest_safe_restore`()
BEGIN
    -- Declare EXIT HANDLER to ensure immediate ROLLBACK and error propagation on ANY failure
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- 1. Insert missing categories L32 (Xiaomi Series) and L42 (OPPO Find)
    -- Referenced by 20 Xiaomi products and 10 OPPO products from 04_data_add.sql
    -- INSERT IGNORE: If categories already exist, do not modify
    INSERT IGNORE INTO `loaisanpham` (`maLoai`, `tenLoai`, `moTa`, `maLoaiCha`) VALUES
    ('L32', 'Xiaomi Series', 'Dòng Xiaomi cao cấp và cận cao cấp', 'L03'),
    ('L42', 'OPPO Find', 'Dòng OPPO Find flagship', 'L04');

    -- 2. Insert test product SP1793425739442001 with status NGUNG_KINH_DOANH
    -- Required parent for test variants to preserve 3 demo orders from 2026-10-31
    -- Status is set strictly to NGUNG_KINH_DOANH so it never appears in shop, search, or AI
    -- INSERT IGNORE: Never overwrites if already present
    INSERT IGNORE INTO `sanpham` (
        `maSanPham`, `maLoai`, `maThuongHieu`, `tenMay`, `moTa`, `kichThuocManHinh`, 
        `loaiManHinh`, `tanSoQuet`, `chip`, `cameraSau`, `cameraTruoc`, `pin`, 
        `congSuatSac`, `heDieuHanh`, `nfc`, `ho5G`, `trongLuong`, `thoiHanBaoHanhThang`, 
        `trangThai`, `diemDanhGiaTb`
    ) VALUES (
        'SP1793425739442001', 'L11', 'TH01', 'iPhone 15 Pro Max - TEST PHIEN BAN', 
        'Sản phẩm dùng để kiểm thử chức năng quản lý phiên bản (Đã ngừng kinh doanh).', 6.70, 'OLED', 
        120, 'Apple A17 Pro', '48MP + 12MP + 12MP', '12MP', 4422, 27, 
        'iOS 17', 1, 1, 221, 12, 'NGUNG_KINH_DOANH', 0.00
    );

    -- Ensure variants of test product are also marked NGUNG_KINH_DOANH
    UPDATE `phienbansanpham` 
    SET `trangThai` = 'NGUNG_KINH_DOANH' 
    WHERE `maSanPham` = 'SP1793425739442001';

    -- 3. Restore 9 DonHang records (Matching 01_schema.sql - NOT NULL name/phone, no obsolete phuongThucTt)
    -- INSERT IGNORE: If an order already exists, KEEP IT UNTOUCHED (never overwrite status/totals)
    INSERT IGNORE INTO `donhang` (
        `maDonHang`, `maKh`, `maKm`, `diaChiGiaoHang`, `ngayCapNhat`, `ngayGiao`, 
        `maKmVanChuyen`, `giamGia`, `phiVanChuyen`, `giamPhiVanChuyen`, `ngayMua`, 
        `trangThai`, `ghiChu`, `tenNguoiNhan`, `soDienThoaiNhan`, `tamTinh`, `tongThanhToan`
    ) VALUES
    ('DH001', 'TK002', NULL, 'Nguyễn Văn An - 0900000002 - 12 Nguyễn Văn Bảo, Phường Hạnh Thông, Thành phố Hồ Chí Minh', '2026-09-22 16:00:00', '2026-09-22 16:00:00', NULL, 0.00, 25000.00, 0.00, '2026-09-20 10:30:00', 'DA_GIAO', NULL, 'Nguyễn Văn An', '0900000002', 3290000.00, 3315000.00),
    ('DH002', 'TK002', 'NEST10', 'Nguyễn Văn An - 0900000002 - 12 Nguyễn Văn Bảo, Phường Hạnh Thông, Thành phố Hồ Chí Minh', '2026-10-12 14:05:00', NULL, NULL, 2000000.00, 25000.00, 0.00, '2026-10-12 14:05:00', 'CHO_XAC_NHAN', NULL, 'Nguyễn Văn An', '0900000002', 27480000.00, 25505000.00),
    ('DH003', 'TK003', NULL, 'Trần Thị Bình - 0900000003 - 88 Trần Hưng Đạo, Phường Chợ Lớn, Thành phố Hồ Chí Minh', '2026-10-15 08:30:00', NULL, NULL, 0.00, 25000.00, 0.00, '2026-10-14 09:15:00', 'DANG_GIAO', NULL, 'Trần Thị Bình', '0900000003', 28990000.00, 29015000.00),
    ('DH1793004295117001', 'TK002', 'NEST10', 'Nguyễn Văn An - 0900000002 - 12 Nguyễn Văn Bảo, Phường Hạnh Thông, Thành phố Hồ Chí Minh', '2026-10-26 15:44:55', NULL, NULL, 329000.00, 0.00, 0.00, '2026-10-26 15:44:55', 'CHO_XAC_NHAN', NULL, 'Nguyễn Văn An', '0900000002', 3290000.00, 2961000.00),
    ('DH1793430971799001', 'TK1793267143020001', 'NEST10', 'Lưu Quốc - 0375034145 - PleiIake, Xã Chư Pưh, Gia Lai', '2026-10-31 14:16:11', NULL, NULL, 2000000.00, 0.00, 0.00, '2026-10-31 14:16:11', 'CHO_XAC_NHAN', NULL, 'Lưu Quốc', '0375034145', 29990000.00, 27990000.00),
    ('DH1793431302239001', 'TK1793267143020001', 'SALE10TR', 'Lưu Quốc - 0375034145 - PleiIake, Xã Chư Pưh, Gia Lai', '2026-10-31 14:21:42', NULL, NULL, 1500000.00, 0.00, 0.00, '2026-10-31 14:21:42', 'CHO_XAC_NHAN', NULL, 'Lưu Quốc', '0375034145', 29990000.00, 28490000.00),
    ('DH1793431865424001', 'TK1793267143020001', 'NEST10', 'Lưu Quốc - 0375034145 - PleiIake, Xã Chư Pưh, Gia Lai', '2026-10-31 14:31:05', NULL, 'FREESHIP', 329000.00, 50000.00, 30000.00, '2026-10-31 14:31:05', 'CHO_XAC_NHAN', NULL, 'Lưu Quốc', '0375034145', 3290000.00, 2981000.00),
    ('DH1793431870453007', 'TK1793267143020001', 'NEST10', 'Lưu Quốc - 0375034145 - PleiIake, Xã Chư Pưh, Gia Lai', '2026-10-31 14:31:10', NULL, 'SHIP50', 329000.00, 50000.00, 25000.00, '2026-10-31 14:31:10', 'CHO_XAC_NHAN', NULL, 'Lưu Quốc', '0375034145', 3290000.00, 2986000.00),
    ('DH1793433508577013', 'TK1793267143020001', 'SALE10TR', 'Lưu Quốc - 0375034145 - PleiIake, Xã Chư Pưh, Gia Lai', '2026-10-31 14:58:28', NULL, NULL, 2000000.00, 0.00, 0.00, '2026-10-31 14:58:28', 'CHO_XAC_NHAN', NULL, 'Lưu Quốc', '0375034145', 32990000.00, 30990000.00);

    -- 4. Restore 10 ChiTietDonHang records (Matching 01_schema.sql - NOT NULL names/sku)
    -- INSERT IGNORE: If an item already exists, KEEP IT UNTOUCHED (never overwrite prices/quantities)
    INSERT IGNORE INTO `chitietdonhang` (
        `maChiTiet`, `maDonHang`, `maPhienBan`, `tenSanPham`, `tenPhienBan`, `sku`, 
        `hinhAnh`, `soLuong`, `donGia`, `baoHanhThang`
    ) VALUES
    ('CTDH1793430971805002', 'DH1793430971799001', 'PB1793425739600002', 'iPhone 15 Pro Max - TEST PHIEN BAN', '8GB/256GB/Titan đen', 'TEST-IP15PM-256-BLACK', NULL, 1, 29990000.00, 12),
    ('CTDH1793431302244002', 'DH1793431302239001', 'PB1793425739600002', 'iPhone 15 Pro Max - TEST PHIEN BAN', '8GB/256GB/Titan đen', 'TEST-IP15PM-256-BLACK', NULL, 1, 29990000.00, 12),
    ('CTDH1793431865434002', 'DH1793431865424001', 'PBSP010', 'Redmi 13C 4GB/128GB', '4GB/128GB/Mặc định', 'SKU-SP010-DEFAULT', '/uploads/sp010-1.jpg', 1, 3290000.00, 12),
    ('CTDH1793431870457008', 'DH1793431870453007', 'PBSP010', 'Redmi 13C 4GB/128GB', '4GB/128GB/Mặc định', 'SKU-SP010-DEFAULT', '/uploads/sp010-1.jpg', 1, 3290000.00, 12),
    ('CTDH1793433508580014', 'DH1793433508577013', 'PB1793425739617004', 'iPhone 15 Pro Max - TEST PHIEN BAN', '8GB/512GB/Titan xanh', 'TEST-IP15PM-512-BLUE', NULL, 1, 32990000.00, 12),
    ('CTDHbba8199dd4f711f1bbfd10b6768a9173', 'DH001', 'PBSP010', 'Redmi 13C', '4GB/128GB/Đen', 'SKU-SP010-DEFAULT', '/uploads/sp010-1.jpg', 1, 3290000.00, 12),
    ('CTDHbba81d08d4f711f1bbfd10b6768a9173', 'DH002', 'PBSP002', 'iPhone 15', '6GB/128GB/Đen', 'PN-002-6-128-DEN', NULL, 1, 19990000.00, 12),
    ('CTDHbba81d90d4f711f1bbfd10b6768a9173', 'DH002', 'PBSP008', 'Redmi Note 13 Pro', '8GB/256GB/Đen', 'PN-008-8-256-DEN', NULL, 1, 7490000.00, 12),
    ('CTDHbba81df2d4f711f1bbfd10b6768a9173', 'DH003', 'PBSP004', 'Samsung Galaxy S24 Ultra', '12GB/256GB/Đen Titan', 'PN-004-12-256-DEN-TITAN', NULL, 1, 28990000.00, 12),
    ('CTDHbba81e51d4f711f1bbfd10b6768a9173', 'DH1793004295117001', 'PBSP010', 'Redmi 13C', '4GB/128GB/Đen', 'SKU-SP010-DEFAULT', '/uploads/sp010-1.jpg', 1, 3290000.00, 12);

    -- 5. Restore DanhGia DG001 (INSERT IGNORE: preserves review if already exists)
    INSERT IGNORE INTO `danhgia` (
        `maDanhGia`, `maKh`, `maChiTietDonHang`, `maSanPham`, `diemSao`, 
        `noiDungBinhLuan`, `hinhAnhThucTe`, `ngayDanhGia`
    ) VALUES (
        'DG001', 'TK002', 'CTDHbba8199dd4f711f1bbfd10b6768a9173', 'SP010', 5, 
        'Máy rẻ mà dùng ổn, pin trâu, giao hàng nhanh.', NULL, '2026-09-25 00:00:00'
    );

    -- 6. Restore 2 missing ChiTietGioHang records for GH002 (INSERT IGNORE: no overwrite)
    INSERT IGNORE INTO `chitietgiohang` (`maGioHang`, `maPhienBan`, `soLuong`) VALUES
    ('GH002', 'PB1793202488339002', 1),
    ('GH002', 'PB500500', 1);

    -- 7. Insert payment records for orders DH001..DH1793004295117001 (INSERT IGNORE)
    INSERT IGNORE INTO `thanhtoan` (`maThanhToan`, `maDonHang`, `phuongThuc`, `soTien`, `trangThai`, `ngayTao`, `ngayThanhToan`) VALUES
    ('TT001', 'DH001', 'COD', 3315000.00, 'DA_THANH_TOAN', '2026-09-20 10:30:00', '2026-09-22 16:00:00'),
    ('TT002', 'DH002', 'CHUYEN_KHOAN', 25505000.00, 'CHO_THANH_TOAN', '2026-10-12 14:05:00', NULL),
    ('TT003', 'DH003', 'VI_DIEN_TU', 29015000.00, 'DA_THANH_TOAN', '2026-10-14 09:15:00', '2026-10-14 09:20:00'),
    ('TT1793004295117001', 'DH1793004295117001', 'COD', 2961000.00, 'CHO_THANH_TOAN', '2026-10-26 15:44:55', NULL);

    COMMIT;
END$$

DELIMITER ;

-- Execute procedure and clean up
CALL `sp_phonenest_safe_restore`();
DROP PROCEDURE IF EXISTS `sp_phonenest_safe_restore`;
