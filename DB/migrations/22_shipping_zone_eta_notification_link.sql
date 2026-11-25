-- MIGRATION 22: phí vận chuyển theo vùng địa lý (kho TP.HCM, 34 tỉnh/thành sau sáp nhập),
-- thời gian giao dự kiến theo số ngày, voucher vận chuyển theo nghiệp vụ mới, đường dẫn cho thông báo.
-- Chạy một lần trên database BanDienThoai (sau migration 21):
--   mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/22_shipping_zone_eta_notification_link.sql
-- ADD COLUMN nghiêm ngặt (không IF NOT EXISTS): chạy lại sẽ báo lỗi cột đã tồn tại.
--
-- Công thức (PhiVanChuyenService.tinh):
--   Phí ship = 0 nếu tạm tính >= mienPhiTu của tỉnh;
--            = phí vùng + phí khai giá + phụ phí COD nếu chưa đạt ngưỡng.
--   phí vùng = phiCoBan (phường) | phiCoBan + phuPhiXa (xã, ngoại thành) | phiCoBan + phuPhiDacKhu (đặc khu / đảo)
--   phí khai giá = 0,5% giá trị hàng, tối đa 50.000đ, với đơn từ 3.000.000đ
--   phụ phí COD = 15.000đ khi thanh toán COD cho đơn trên 10.000.000đ
--   Ngày giao dự kiến = ngày bàn giao (đơn sau 16:00 tính từ hôm sau) + soNgayGiaoToiThieu..soNgayGiaoToiDa;
--   đặc khu cộng thêm 1..2 ngày.
--
-- Vùng:  0 Nội tỉnh TP.HCM (gồm Bình Dương, BR-VT cũ)  15.000đ (xã +15.000đ, đặc khu +30.000đ)  trong ngày - 1 ngày
--        1 Lân cận & Miền Nam (7)                      30.000đ (đặc khu +15.000đ)                 1 - 2 ngày
--        2 Miền Trung & Tây Nguyên (10)                40.000đ (đặc khu +15.000đ)                 2 - 3 ngày
--        3 Miền Bắc (16)                               50.000đ (đặc khu +15.000đ)                 3 - 4 ngày
-- Freeship: vùng 0-1 từ 3.000.000đ, vùng 2-3 từ 5.000.000đ.

-- 1. Bảng phí vận chuyển: vùng, phụ phí theo loại địa bàn, số ngày giao.
ALTER TABLE PhiVanChuyen
    ADD COLUMN vung TINYINT NULL AFTER maTinh,
    ADD COLUMN phuPhiXa DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER phiCoBan,
    ADD COLUMN phuPhiDacKhu DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER phuPhiXa,
    ADD COLUMN soNgayGiaoToiThieu INT NULL AFTER mienPhiTu,
    ADD COLUMN soNgayGiaoToiDa INT NULL AFTER soNgayGiaoToiThieu,
    ADD CONSTRAINT CK_PhiVanChuyen_Vung CHECK (vung IS NULL OR vung BETWEEN 0 AND 3),
    ADD CONSTRAINT CK_PhiVanChuyen_SoNgay CHECK (soNgayGiaoToiThieu IS NULL OR soNgayGiaoToiDa IS NULL
        OR (soNgayGiaoToiThieu >= 0 AND soNgayGiaoToiThieu <= soNgayGiaoToiDa));

CREATE TEMPORARY TABLE tmp_m22_phi (
    maTinh VARCHAR(10) PRIMARY KEY,
    vung TINYINT NOT NULL,
    phiCoBan DECIMAL(15,2) NOT NULL,
    phuPhiXa DECIMAL(15,2) NOT NULL,
    phuPhiDacKhu DECIMAL(15,2) NOT NULL,
    mienPhiTu DECIMAL(15,2) NOT NULL,
    soNgayGiaoToiThieu INT NOT NULL,
    soNgayGiaoToiDa INT NOT NULL,
    thoiGianGiao VARCHAR(50) NOT NULL
);

INSERT INTO tmp_m22_phi VALUES
-- Vùng 0
('79',0,15000,15000,30000,3000000,0,1,'Trong ngày - 1 ngày'),
-- Vùng 1
('75',1,30000,0,15000,3000000,1,2,'1 - 2 ngày'),
('80',1,30000,0,15000,3000000,1,2,'1 - 2 ngày'),
('82',1,30000,0,15000,3000000,1,2,'1 - 2 ngày'),
('86',1,30000,0,15000,3000000,1,2,'1 - 2 ngày'),
('91',1,30000,0,15000,3000000,1,2,'1 - 2 ngày'),
('92',1,30000,0,15000,3000000,1,2,'1 - 2 ngày'),
('96',1,30000,0,15000,3000000,1,2,'1 - 2 ngày'),
-- Vùng 2
('40',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
('42',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
('44',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
('46',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
('48',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
('51',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
('52',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
('56',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
('66',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
('68',2,40000,0,15000,5000000,2,3,'2 - 3 ngày'),
-- Vùng 3
('01',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('04',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('08',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('11',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('12',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('14',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('15',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('19',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('20',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('22',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('24',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('25',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('31',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('33',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('37',3,50000,0,15000,5000000,3,4,'3 - 4 ngày'),
('38',3,50000,0,15000,5000000,3,4,'3 - 4 ngày');

-- Cập nhật tỉnh đã có bảng phí, thêm tỉnh chưa có (mã PVCT<maTinh>). Chỉ áp dụng cho tỉnh tồn tại trong TinhThanh.
UPDATE PhiVanChuyen p JOIN tmp_m22_phi t ON t.maTinh = p.maTinh
SET p.vung = t.vung, p.phiCoBan = t.phiCoBan, p.phuPhiXa = t.phuPhiXa, p.phuPhiDacKhu = t.phuPhiDacKhu,
    p.mienPhiTu = t.mienPhiTu, p.soNgayGiaoToiThieu = t.soNgayGiaoToiThieu, p.soNgayGiaoToiDa = t.soNgayGiaoToiDa,
    p.thoiGianGiao = t.thoiGianGiao;

INSERT INTO PhiVanChuyen (maPhi, maTinh, vung, phiCoBan, phuPhiXa, phuPhiDacKhu, mienPhiTu,
                          soNgayGiaoToiThieu, soNgayGiaoToiDa, thoiGianGiao)
SELECT CONCAT('PVCT', t.maTinh), t.maTinh, t.vung, t.phiCoBan, t.phuPhiXa, t.phuPhiDacKhu, t.mienPhiTu,
       t.soNgayGiaoToiThieu, t.soNgayGiaoToiDa, t.thoiGianGiao
FROM tmp_m22_phi t
JOIN TinhThanh tt ON tt.maTinh = t.maTinh
WHERE NOT EXISTS (SELECT 1 FROM PhiVanChuyen p WHERE p.maTinh = t.maTinh);

-- Mức mặc định (maTinh NULL) cho địa chỉ ngoài danh sách.
UPDATE PhiVanChuyen
SET vung = NULL, phiCoBan = 50000, phuPhiXa = 0, phuPhiDacKhu = 15000, mienPhiTu = 5000000,
    soNgayGiaoToiThieu = 3, soNgayGiaoToiDa = 5, thoiGianGiao = '3 - 5 ngày'
WHERE maTinh IS NULL;

INSERT INTO PhiVanChuyen (maPhi, maTinh, vung, phiCoBan, phuPhiXa, phuPhiDacKhu, mienPhiTu,
                          soNgayGiaoToiThieu, soNgayGiaoToiDa, thoiGianGiao)
SELECT 'PVC99', NULL, NULL, 50000, 0, 15000, 5000000, 3, 5, '3 - 5 ngày'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM PhiVanChuyen WHERE maTinh IS NULL)
  AND NOT EXISTS (SELECT 1 FROM PhiVanChuyen WHERE maPhi = 'PVC99');

DROP TEMPORARY TABLE tmp_m22_phi;

-- 2. Voucher vận chuyển: chỉ giảm phí giao hàng theo vùng (không gồm phí khai giá / COD),
--    chỉ có tác dụng với đơn chưa đạt ngưỡng freeship. Trần 50.000đ = phí vùng cao nhất (miền Bắc).
UPDATE KhuyenMai
SET soTienGiamToiDa = 50000, donToiThieu = 1000000,
    dieuKienApDung = 'Đơn từ 1.000.000đ chưa được miễn phí vận chuyển: miễn phí giao hàng theo vùng, tối đa 50.000đ (không gồm phí khai giá, phụ phí COD)'
WHERE maKm = 'FREESHIP';

UPDATE KhuyenMai
SET soTienGiamToiDa = 25000, donToiThieu = 0,
    dieuKienApDung = 'Giảm 50% phí giao hàng theo vùng, tối đa 25.000đ (không gồm phí khai giá, phụ phí COD). Không áp dụng cho đơn đã được miễn phí vận chuyển'
WHERE maKm = 'SHIP50';

-- 3. Đơn hàng: thành phần phí (đã gồm trong phiVanChuyen) và khoảng ngày giao dự kiến.
ALTER TABLE DonHang
    ADD COLUMN phiKhaiGia DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER giamPhiVanChuyen,
    ADD COLUMN phuPhiCod DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER phiKhaiGia,
    ADD COLUMN ngayGiaoDuKienTu DATE NULL AFTER ngayGiao,
    ADD COLUMN ngayGiaoDuKienDen DATE NULL AFTER ngayGiaoDuKienTu;

-- 4. Thông báo: đường dẫn trang liên quan, bấm vào thông báo sẽ mở trang đó.
ALTER TABLE ThongBao ADD COLUMN duongDan VARCHAR(255) NULL AFTER noiDung;

-- Gán đường dẫn cho thông báo cũ của khách: yêu cầu đổi trả / bảo hành trước, sau đó tới đơn hàng.
UPDATE ThongBao SET duongDan = '/returns'
WHERE duongDan IS NULL AND noiDung LIKE 'Yêu cầu %'
  AND maTk IN (SELECT maKh FROM KhachHang);

UPDATE ThongBao SET duongDan = CONCAT('/orders/', REGEXP_SUBSTR(noiDung, 'DH[0-9]+'))
WHERE duongDan IS NULL AND noiDung REGEXP 'DH[0-9]+'
  AND maTk IN (SELECT maKh FROM KhachHang);
