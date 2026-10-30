-- PhoneNest - Task 5 Phase 2: Audit Log
-- Them bang NhatKyHoatDong vao database DANG CHAY, KHONG drop/recreate.
--
-- Chay thu cong:
--   "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -h 127.0.0.1 -P 3307 -u root -p < DB/03_audit_log.sql
--
-- Script idempotent: CREATE TABLE IF NOT EXISTS => chay lai nhieu lan khong loi
-- va khong dung toi du lieu cua bat ky bang nao khac.
-- Khong dung DROP / TRUNCATE / DELETE.

USE BanDienThoai;

CREATE TABLE IF NOT EXISTS NhatKyHoatDong (
    maNhatKy VARCHAR(50) PRIMARY KEY,
    maTk VARCHAR(50) NOT NULL,
    tenNguoiThucHien VARCHAR(150) NOT NULL,
    hanhDong VARCHAR(50) NOT NULL,
    loaiDoiTuong VARCHAR(50) NOT NULL,
    maDoiTuong VARCHAR(50),
    moTa VARCHAR(500),
    duLieuTruoc LONGTEXT,
    duLieuSau LONGTEXT,
    ipAddress VARCHAR(45),
    requestMethod VARCHAR(10),
    requestPath VARCHAR(255),
    thoiGian DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX IDX_NhatKyHoatDong_ThoiGian (thoiGian),
    INDEX IDX_NhatKyHoatDong_MaTk_ThoiGian (maTk, thoiGian),
    INDEX IDX_NhatKyHoatDong_LoaiDoiTuong_HanhDong (loaiDoiTuong, hanhDong)
);
