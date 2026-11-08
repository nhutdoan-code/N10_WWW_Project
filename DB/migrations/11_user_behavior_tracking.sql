-- Migration 11: Bang theo doi hanh vi nguoi dung (User Behavior Tracking)
-- Phuc vu thu thap hanh vi khach hang de phan tich so thich va tich hop Spring AI.
-- Script idempotent: CREATE TABLE IF NOT EXISTS dam bao an toan khi chay lai.

USE BanDienThoai;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS HanhViNguoiDung (
    maHanhVi VARCHAR(50) PRIMARY KEY,
    maKh VARCHAR(50) NULL,
    sessionId VARCHAR(100) NOT NULL,
    maSanPham VARCHAR(50) NULL,
    maPhienBan VARCHAR(50) NULL,
    loaiHanhVi ENUM('PRODUCT_VIEW','PRODUCT_CLICK','SEARCH','ADD_TO_CART','REMOVE_FROM_CART','PURCHASE','RECOMMENDATION_CLICK','PAGE_VIEW') NOT NULL,
    tuKhoa VARCHAR(255) NULL,
    metadata VARCHAR(1000) NULL,
    nguonSuKien VARCHAR(100) NULL,
    thoiGian DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_HanhViNguoiDung_KhachHang FOREIGN KEY (maKh) REFERENCES KhachHang(maKh) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT FK_HanhViNguoiDung_SanPham FOREIGN KEY (maSanPham) REFERENCES SanPham(maSanPham) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT FK_HanhViNguoiDung_PhienBan FOREIGN KEY (maPhienBan) REFERENCES PhienBanSanPham(maPhienBan) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX IDX_HanhVi_Kh_ThoiGian (maKh, thoiGian),
    INDEX IDX_HanhVi_Session_ThoiGian (sessionId, thoiGian),
    INDEX IDX_HanhVi_SanPham_Loai (maSanPham, loaiHanhVi),
    INDEX IDX_HanhVi_Loai_ThoiGian (loaiHanhVi, thoiGian),
    INDEX IDX_HanhVi_TuKhoa (tuKhoa)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
