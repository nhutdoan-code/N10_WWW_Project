-- PhoneNest - SCHEMA hiện tại (tách từ Phone.sql)
-- Chạy file này trước để tạo database và cấu trúc bảng.

-- --------------------------------------------------------
-- Host:                         127.0.0.1
-- Server version:               12.2.2-MariaDB - MariaDB Server
-- Server OS:                    Win64
-- HeidiSQL Version:             12.14.0.7165
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- Dumping database structure for bandienthoai
CREATE DATABASE IF NOT EXISTS `bandienthoai` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */;
USE `bandienthoai`;

-- Dumping structure for table bandienthoai.biendongkho
CREATE TABLE IF NOT EXISTS `biendongkho` (
                                             `maBienDong` varchar(50) NOT NULL,
    `maPhienBan` varchar(50) NOT NULL,
    `loaiBienDong` varchar(50) DEFAULT NULL,
    `soLuong` int(11) NOT NULL,
    `tonTruoc` int(11) NOT NULL,
    `tonSau` int(11) NOT NULL,
    `maDonHang` varchar(50) DEFAULT NULL,
    `thoiGian` datetime DEFAULT NULL,
    `ghiChu` varchar(255) DEFAULT NULL,
    PRIMARY KEY (`maBienDong`),
    KEY `idx_bdk_phienban` (`maPhienBan`),
    KEY `idx_bdk_donhang` (`maDonHang`),
    CONSTRAINT `fk_bdk_donhang` FOREIGN KEY (`maDonHang`) REFERENCES `donhang` (`maDonHang`),
    CONSTRAINT `fk_bdk_phienban` FOREIGN KEY (`maPhienBan`) REFERENCES `phienbansanpham` (`maPhienBan`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.chitietdonhang
CREATE TABLE IF NOT EXISTS `chitietdonhang` (
                                                `maDonHang` varchar(50) NOT NULL,
    `maSanPham` varchar(50) NOT NULL,
    `soLuong` int(11) NOT NULL,
    `donGia` decimal(15,2) NOT NULL,
    `maChiTiet` varchar(50) NOT NULL,
    `maPhienBan` varchar(50) NOT NULL,
    `tenSanPham` varchar(255) DEFAULT NULL,
    `tenPhienBan` varchar(255) DEFAULT NULL,
    `sku` varchar(255) DEFAULT NULL,
    `hinhAnh` varchar(255) DEFAULT NULL,
    `baoHanhThang` int(11) NOT NULL DEFAULT 12,
    PRIMARY KEY (`maChiTiet`),
    UNIQUE KEY `uk_ctdh_donhang_sanpham` (`maDonHang`,`maSanPham`),
    UNIQUE KEY `uk_ctdh_donhang_phienban` (`maDonHang`,`maPhienBan`),
    KEY `FK_ChiTietDonHang_SanPham` (`maSanPham`),
    CONSTRAINT `FK_ChiTietDonHang_DonHang` FOREIGN KEY (`maDonHang`) REFERENCES `donhang` (`maDonHang`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK_ChiTietDonHang_SanPham` FOREIGN KEY (`maSanPham`) REFERENCES `sanpham` (`maSanPham`) ON UPDATE CASCADE,
    CONSTRAINT `CK_ChiTietDonHang_SoLuong` CHECK (`soLuong` > 0),
    CONSTRAINT `CK_ChiTietDonHang_DonGia` CHECK (`donGia` >= 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.chitietgiohang
CREATE TABLE IF NOT EXISTS `chitietgiohang` (
                                                `maGioHang` varchar(50) NOT NULL,
    `maPhienBan` varchar(50) NOT NULL,
    `soLuong` int(11) NOT NULL DEFAULT 1,
    PRIMARY KEY (`maGioHang`,`maPhienBan`),
    KEY `IDX_ChiTietGioHang_GioHang` (`maGioHang`),
    KEY `IDX_ChiTietGioHang_PhienBan` (`maPhienBan`),
    CONSTRAINT `FK_ChiTietGioHang_GioHang` FOREIGN KEY (`maGioHang`) REFERENCES `giohang` (`maGioHang`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK_ChiTietGioHang_PhienBan` FOREIGN KEY (`maPhienBan`) REFERENCES `phienbansanpham` (`maPhienBan`) ON UPDATE CASCADE,
    CONSTRAINT `CK_ChiTietGioHang_SoLuong` CHECK (`soLuong` > 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.danhgia
CREATE TABLE IF NOT EXISTS `danhgia` (
                                         `maDanhGia` varchar(50) NOT NULL,
    `maKh` varchar(50) NOT NULL,
    `maDonHang` varchar(50) NOT NULL,
    `maSanPham` varchar(50) NOT NULL,
    `diemSao` int(11) NOT NULL,
    `noiDungBinhLuan` text DEFAULT NULL,
    `hinhAnhThucTe` varchar(500) DEFAULT NULL,
    `ngayDanhGia` date NOT NULL,
    PRIMARY KEY (`maDanhGia`),
    UNIQUE KEY `UQ_DanhGia_DonHang_SanPham` (`maDonHang`,`maSanPham`),
    KEY `FK_DanhGia_KhachHang` (`maKh`),
    KEY `FK_DanhGia_SanPham` (`maSanPham`),
    CONSTRAINT `FK_DanhGia_ChiTietDonHang` FOREIGN KEY (`maDonHang`, `maSanPham`) REFERENCES `chitietdonhang` (`maDonHang`, `maSanPham`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK_DanhGia_KhachHang` FOREIGN KEY (`maKh`) REFERENCES `khachhang` (`maKh`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK_DanhGia_SanPham` FOREIGN KEY (`maSanPham`) REFERENCES `sanpham` (`maSanPham`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `CK_DanhGia_DiemSao` CHECK (`diemSao` between 1 and 5)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.diachi
CREATE TABLE IF NOT EXISTS `diachi` (
                                        `maDiaChi` varchar(50) NOT NULL,
    `maKh` varchar(50) NOT NULL,
    `tenNguoiNhan` varchar(100) NOT NULL,
    `soDienThoaiNhan` varchar(20) NOT NULL,
    `diaChiNhanHang` varchar(255) NOT NULL,
    `maTinh` varchar(10) NOT NULL,
    `maPhuong` varchar(10) NOT NULL,
    `macDinh` tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`maDiaChi`),
    KEY `FK_DiaChi_KhachHang` (`maKh`),
    KEY `FK_DiaChi_TinhThanh` (`maTinh`),
    KEY `FK_DiaChi_PhuongXa` (`maPhuong`),
    CONSTRAINT `FK_DiaChi_KhachHang` FOREIGN KEY (`maKh`) REFERENCES `khachhang` (`maKh`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK_DiaChi_PhuongXa` FOREIGN KEY (`maPhuong`) REFERENCES `phuongxa` (`maPhuong`) ON UPDATE CASCADE,
    CONSTRAINT `FK_DiaChi_TinhThanh` FOREIGN KEY (`maTinh`) REFERENCES `tinhthanh` (`maTinh`) ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.donhang
CREATE TABLE IF NOT EXISTS `donhang` (
                                         `maDonHang` varchar(50) NOT NULL,
    `maKh` varchar(50) NOT NULL,
    `maKm` varchar(50) DEFAULT NULL,
    `diaChiGiaoHang` varchar(500) DEFAULT NULL,
    `ngayCapNhat` datetime DEFAULT NULL,
    `ngayGiao` datetime DEFAULT NULL,
    `maKmVanChuyen` varchar(50) DEFAULT NULL,
    `giamGia` decimal(15,2) NOT NULL DEFAULT 0.00,
    `phiVanChuyen` decimal(15,2) NOT NULL DEFAULT 0.00,
    `giamPhiVanChuyen` decimal(15,2) NOT NULL DEFAULT 0.00,
    `ngayMua` datetime NOT NULL DEFAULT current_timestamp(),
    `phuongThucTt` enum('COD','CHUYEN_KHOAN','VI_DIEN_TU') NOT NULL,
    `trangThai` enum('CHO_XAC_NHAN','DANG_XU_LY','DANG_GIAO','DA_GIAO','DA_HUY') NOT NULL DEFAULT 'CHO_XAC_NHAN',
    `ghiChu` text DEFAULT NULL,
    `tenNguoiNhan` varchar(255) DEFAULT NULL,
    `soDienThoaiNhan` varchar(255) DEFAULT NULL,
    `tamTinh` decimal(15,2) NOT NULL DEFAULT 0.00,
    `tongThanhToan` decimal(15,2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (`maDonHang`),
    KEY `FK_DonHang_KhachHang` (`maKh`),
    KEY `FK_DonHang_KhuyenMai` (`maKm`),
    KEY `FK_DonHang_KhuyenMaiVanChuyen` (`maKmVanChuyen`),
    CONSTRAINT `FK_DonHang_KhachHang` FOREIGN KEY (`maKh`) REFERENCES `khachhang` (`maKh`) ON UPDATE CASCADE,
    CONSTRAINT `FK_DonHang_KhuyenMai` FOREIGN KEY (`maKm`) REFERENCES `khuyenmai` (`maKm`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `FK_DonHang_KhuyenMaiVanChuyen` FOREIGN KEY (`maKmVanChuyen`) REFERENCES `khuyenmai` (`maKm`) ON DELETE SET NULL ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.giohang
CREATE TABLE IF NOT EXISTS `giohang` (
                                         `maGioHang` varchar(50) NOT NULL,
    `maKh` varchar(50) NOT NULL,
    `ngayTao` datetime NOT NULL,
    `ngayCapNhat` datetime DEFAULT NULL,
    PRIMARY KEY (`maGioHang`),
    UNIQUE KEY `maKh` (`maKh`),
    CONSTRAINT `FK_GioHang_KhachHang` FOREIGN KEY (`maKh`) REFERENCES `khachhang` (`maKh`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.hinhanhsanpham
CREATE TABLE IF NOT EXISTS `hinhanhsanpham` (
                                                `maHinhAnh` varchar(50) NOT NULL,
    `maSanPham` varchar(50) NOT NULL,
    `duongDan` varchar(500) NOT NULL,
    `gocChup` varchar(100) DEFAULT NULL,
    `anhChinh` tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`maHinhAnh`),
    KEY `FK_HinhAnhSanPham_SanPham` (`maSanPham`),
    CONSTRAINT `FK_HinhAnhSanPham_SanPham` FOREIGN KEY (`maSanPham`) REFERENCES `sanpham` (`maSanPham`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.hoidap
CREATE TABLE IF NOT EXISTS `hoidap` (
                                        `maCauHoi` varchar(50) NOT NULL,
    `maKh` varchar(50) NOT NULL,
    `maSanPham` varchar(50) NOT NULL,
    `noiDungCauHoi` text NOT NULL,
    `noiDungTraLoi` text DEFAULT NULL,
    `ngayHoi` date NOT NULL,
    `ngayTraLoi` date DEFAULT NULL,
    PRIMARY KEY (`maCauHoi`),
    KEY `FK_HoiDap_KhachHang` (`maKh`),
    KEY `FK_HoiDap_SanPham` (`maSanPham`),
    CONSTRAINT `FK_HoiDap_KhachHang` FOREIGN KEY (`maKh`) REFERENCES `khachhang` (`maKh`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK_HoiDap_SanPham` FOREIGN KEY (`maSanPham`) REFERENCES `sanpham` (`maSanPham`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.huydonhang
CREATE TABLE IF NOT EXISTS `huydonhang` (
                                            `maDonHang` varchar(50) NOT NULL,
    `lyDo` varchar(100) NOT NULL,
    `ghiChu` varchar(255) DEFAULT NULL,
    `ngayHuy` datetime NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`maDonHang`),
    CONSTRAINT `FK_HuyDonHang_DonHang` FOREIGN KEY (`maDonHang`) REFERENCES `donhang` (`maDonHang`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.khachhang
CREATE TABLE IF NOT EXISTS `khachhang` (
                                           `maKh` varchar(50) NOT NULL,
    `hoTen` varchar(100) NOT NULL,
    `ngaySinh` date DEFAULT NULL,
    `anhDaiDien` varchar(500) DEFAULT NULL,
    `diemTichLuy` int(11) NOT NULL DEFAULT 0,
    `hangThanhVien` varchar(50) DEFAULT NULL,
    `maGioiThieu` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`maKh`),
    CONSTRAINT `FK_KhachHang_TaiKhoan` FOREIGN KEY (`maKh`) REFERENCES `taikhoan` (`maTk`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.khuyenmai
CREATE TABLE IF NOT EXISTS `khuyenmai` (
                                           `maKm` varchar(50) NOT NULL,
    `tenKm` varchar(150) NOT NULL,
    `phanTramGiam` decimal(5,2) NOT NULL,
    `soTienGiamToiDa` decimal(15,2) DEFAULT NULL,
    `ngayBatDau` date NOT NULL,
    `ngayKetThuc` date NOT NULL,
    `dieuKienApDung` text DEFAULT NULL,
    `loai` enum('DON_HANG','VAN_CHUYEN') NOT NULL DEFAULT 'DON_HANG',
    `donToiThieu` decimal(15,2) NOT NULL DEFAULT 0.00,
    `soLuong` int(11) DEFAULT NULL,
    `daDung` int(11) NOT NULL DEFAULT 0,
    `chiKhachMoi` tinyint(1) NOT NULL DEFAULT 0,
    `gioiHanMoiKhach` int(11) NOT NULL DEFAULT 1,
    `trangThaiHoatDong` tinyint(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`maKm`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.lichsutrangthaidonhang
CREATE TABLE IF NOT EXISTS `lichsutrangthaidonhang` (
                                                        `maLichSu` varchar(50) NOT NULL,
    `maDonHang` varchar(50) NOT NULL,
    `trangThai` varchar(50) DEFAULT NULL,
    `thoiGian` datetime DEFAULT NULL,
    `ghiChu` varchar(255) DEFAULT NULL,
    `nguoiCapNhat` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`maLichSu`),
    KEY `idx_lsttdh_donhang` (`maDonHang`),
    KEY `idx_lsttdh_nguoicapnhat` (`nguoiCapNhat`),
    CONSTRAINT `fk_lsttdh_donhang` FOREIGN KEY (`maDonHang`) REFERENCES `donhang` (`maDonHang`),
    CONSTRAINT `fk_lsttdh_taikhoan` FOREIGN KEY (`nguoiCapNhat`) REFERENCES `taikhoan` (`maTk`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.loaisanpham
CREATE TABLE IF NOT EXISTS `loaisanpham` (
                                             `maLoai` varchar(50) NOT NULL,
    `tenLoai` varchar(100) NOT NULL,
    `moTa` text DEFAULT NULL,
    `maLoaiCha` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`maLoai`),
    KEY `FK_LoaiSanPham_LoaiCha` (`maLoaiCha`),
    CONSTRAINT `FK_LoaiSanPham_LoaiCha` FOREIGN KEY (`maLoaiCha`) REFERENCES `loaisanpham` (`maLoai`) ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.password_reset_otp
CREATE TABLE IF NOT EXISTS `password_reset_otp` (
                                                    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `maTk` varchar(50) NOT NULL,
    `otpHash` varchar(255) NOT NULL,
    `createdAt` datetime NOT NULL,
    `otpExpiresAt` datetime NOT NULL,
    `attempts` int(11) NOT NULL DEFAULT 0,
    `otpVerified` tinyint(1) NOT NULL DEFAULT 0,
    `resetTokenHash` varchar(255) DEFAULT NULL,
    `resetExpiresAt` datetime DEFAULT NULL,
    `used` tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_reset_otp_account` (`maTk`),
    KEY `idx_reset_otp_token` (`resetTokenHash`),
    CONSTRAINT `fk_reset_otp_taikhoan` FOREIGN KEY (`maTk`) REFERENCES `taikhoan` (`maTk`) ON DELETE CASCADE
    ) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.phienbansanpham
CREATE TABLE IF NOT EXISTS `phienbansanpham` (
                                                 `maPhienBan` varchar(50) NOT NULL,
    `maSanPham` varchar(50) NOT NULL,
    `sku` varchar(100) NOT NULL,
    `ram` int(11) NOT NULL DEFAULT 0,
    `boNho` int(11) NOT NULL DEFAULT 0,
    `mauSac` varchar(50) NOT NULL,
    `giaBan` decimal(15,2) NOT NULL,
    `giaNiemYet` decimal(15,2) DEFAULT NULL,
    `soLuongTon` int(11) NOT NULL DEFAULT 0,
    `trangThai` enum('CON_HANG','HET_HANG','NGUNG_KINH_DOANH') NOT NULL DEFAULT 'CON_HANG',
    PRIMARY KEY (`maPhienBan`),
    UNIQUE KEY `UQ_PhienBan_SKU` (`sku`),
    UNIQUE KEY `UQ_PhienBan_CauHinh` (`maSanPham`,`ram`,`boNho`,`mauSac`),
    KEY `FK_PhienBan_SanPham` (`maSanPham`),
    CONSTRAINT `FK_PhienBan_SanPham` FOREIGN KEY (`maSanPham`) REFERENCES `sanpham` (`maSanPham`) ON UPDATE CASCADE,
    CONSTRAINT `CK_PhienBan_GiaBan` CHECK (`giaBan` >= 0),
    CONSTRAINT `CK_PhienBan_SoLuongTon` CHECK (`soLuongTon` >= 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.phivanchuyen
CREATE TABLE IF NOT EXISTS `phivanchuyen` (
                                              `maPhi` varchar(50) NOT NULL,
    `maTinh` varchar(10) DEFAULT NULL,
    `phiCoBan` decimal(15,2) NOT NULL,
    `mienPhiTu` decimal(15,2) DEFAULT NULL,
    `thoiGianGiao` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`maPhi`),
    UNIQUE KEY `maTinh` (`maTinh`),
    CONSTRAINT `FK_PhiVanChuyen_TinhThanh` FOREIGN KEY (`maTinh`) REFERENCES `tinhthanh` (`maTinh`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.phuongxa
CREATE TABLE IF NOT EXISTS `phuongxa` (
                                          `maPhuong` varchar(10) NOT NULL,
    `tenPhuong` varchar(100) NOT NULL,
    `maTinh` varchar(10) NOT NULL,
    PRIMARY KEY (`maPhuong`),
    KEY `IDX_PhuongXa_maTinh` (`maTinh`),
    CONSTRAINT `FK_PhuongXa_TinhThanh` FOREIGN KEY (`maTinh`) REFERENCES `tinhthanh` (`maTinh`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.quantrivien
CREATE TABLE IF NOT EXISTS `quantrivien` (
                                             `maQtv` varchar(50) NOT NULL,
    `hoTen` varchar(100) NOT NULL,
    `vaiTro` varchar(100) DEFAULT NULL,
    `chucVu` varchar(100) DEFAULT NULL,
    PRIMARY KEY (`maQtv`),
    CONSTRAINT `FK_QuanTriVien_TaiKhoan` FOREIGN KEY (`maQtv`) REFERENCES `taikhoan` (`maTk`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.sanpham
CREATE TABLE IF NOT EXISTS `sanpham` (
                                         `maSanPham` varchar(50) NOT NULL,
    `maLoai` varchar(50) NOT NULL,
    `tenMay` varchar(150) NOT NULL,
    `giaBan` decimal(15,2) NOT NULL,
    `moTa` text DEFAULT NULL,
    `kichThuocManHinh` decimal(3,2) DEFAULT NULL,
    `loaiManHinh` varchar(50) DEFAULT NULL,
    `tanSoQuet` int(11) DEFAULT NULL,
    `chip` varchar(100) DEFAULT NULL,
    `ram` int(11) DEFAULT NULL,
    `boNho` int(11) DEFAULT NULL,
    `cameraSau` varchar(150) DEFAULT NULL,
    `cameraTruoc` int(11) DEFAULT NULL,
    `pin` int(11) DEFAULT NULL,
    `congSuatSac` int(11) DEFAULT NULL,
    `heDieuHanh` varchar(50) DEFAULT NULL,
    `nfc` tinyint(1) NOT NULL DEFAULT 0,
    `ho5G` tinyint(1) NOT NULL DEFAULT 0,
    `trongLuong` int(11) DEFAULT NULL,
    `soLuongTon` int(11) NOT NULL DEFAULT 0,
    `trangThai` enum('CON_HANG','HET_HANG','NGUNG_KINH_DOANH') NOT NULL DEFAULT 'CON_HANG',
    `diemDanhGiaTb` decimal(3,2) DEFAULT 0.00,
    `thoiHanBaoHanhThang` int(11) NOT NULL DEFAULT 12 COMMENT 'Thời hạn bảo hành tính theo tháng',
    PRIMARY KEY (`maSanPham`),
    KEY `FK_SanPham_LoaiSanPham` (`maLoai`),
    CONSTRAINT `FK_SanPham_LoaiSanPham` FOREIGN KEY (`maLoai`) REFERENCES `loaisanpham` (`maLoai`) ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.taikhoan
CREATE TABLE IF NOT EXISTS `taikhoan` (
                                          `maTk` varchar(50) NOT NULL,
    `email` varchar(255) NOT NULL,
    `matKhau` varchar(255) NOT NULL,
    `ngayTao` date NOT NULL,
    `trangThaiHoatDong` tinyint(1) NOT NULL DEFAULT 1,
    `loaiDangNhap` varchar(50) DEFAULT NULL,
    `soDienThoai` varchar(20) DEFAULT NULL,
    PRIMARY KEY (`maTk`),
    UNIQUE KEY `email` (`email`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.thanhtoan
CREATE TABLE IF NOT EXISTS `thanhtoan` (
                                           `maThanhToan` varchar(50) NOT NULL,
    `maDonHang` varchar(50) NOT NULL,
    `phuongThuc` varchar(50) DEFAULT NULL,
    `soTien` decimal(15,2) DEFAULT NULL,
    `trangThai` varchar(50) DEFAULT 'CHO_THANH_TOAN',
    `maGiaoDich` varchar(255) DEFAULT NULL,
    `ngayTao` datetime DEFAULT NULL,
    `ngayThanhToan` datetime DEFAULT NULL,
    PRIMARY KEY (`maThanhToan`),
    UNIQUE KEY `uk_thanhtoan_maGiaoDich` (`maGiaoDich`),
    KEY `idx_thanhtoan_donhang` (`maDonHang`),
    CONSTRAINT `fk_thanhtoan_donhang` FOREIGN KEY (`maDonHang`) REFERENCES `donhang` (`maDonHang`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.thongbao
CREATE TABLE IF NOT EXISTS `thongbao` (
                                          `maThongBao` varchar(50) NOT NULL,
    `maTk` varchar(50) NOT NULL,
    `noiDung` text NOT NULL,
    `daDoc` tinyint(1) NOT NULL DEFAULT 0,
    `ngayTao` datetime NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`maThongBao`),
    KEY `FK_ThongBao_TaiKhoan` (`maTk`),
    CONSTRAINT `FK_ThongBao_TaiKhoan` FOREIGN KEY (`maTk`) REFERENCES `taikhoan` (`maTk`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.tinhthanh
CREATE TABLE IF NOT EXISTS `tinhthanh` (
                                           `maTinh` varchar(10) NOT NULL,
    `tenTinh` varchar(100) NOT NULL,
    PRIMARY KEY (`maTinh`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.wishlist
CREATE TABLE IF NOT EXISTS `wishlist` (
                                          `maDanhSach` varchar(50) NOT NULL,
    `maKh` varchar(50) NOT NULL,
    PRIMARY KEY (`maDanhSach`),
    UNIQUE KEY `maKh` (`maKh`),
    CONSTRAINT `FK_Wishlist_KhachHang` FOREIGN KEY (`maKh`) REFERENCES `khachhang` (`maKh`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.wishlistsanpham
CREATE TABLE IF NOT EXISTS `wishlistsanpham` (
                                                 `maDanhSach` varchar(50) NOT NULL,
    `maSanPham` varchar(50) NOT NULL,
    PRIMARY KEY (`maDanhSach`,`maSanPham`),
    KEY `FK_WishlistSanPham_SanPham` (`maSanPham`),
    CONSTRAINT `FK_WishlistSanPham_SanPham` FOREIGN KEY (`maSanPham`) REFERENCES `sanpham` (`maSanPham`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK_WishlistSanPham_Wishlist` FOREIGN KEY (`maDanhSach`) REFERENCES `wishlist` (`maDanhSach`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dumping structure for table bandienthoai.yeucaudoitra
CREATE TABLE IF NOT EXISTS `yeucaudoitra` (
                                              `maYeuCau` varchar(50) NOT NULL,
    `maDonHang` varchar(50) NOT NULL,
    `lyDo` text DEFAULT NULL,
    `hinhAnhMinhChung` varchar(500) DEFAULT NULL,
    `ngayTao` date NOT NULL,
    `trangThai` enum('CHO_DUYET','DA_DUYET','TU_CHOI','DA_HOAN_TIEN') NOT NULL DEFAULT 'CHO_DUYET',
    `ghiChuXuLy` text DEFAULT NULL,
    `lyDoChung` text DEFAULT NULL,
    PRIMARY KEY (`maYeuCau`),
    UNIQUE KEY `maDonHang` (`maDonHang`),
    CONSTRAINT `FK_YeuCauDoiTra_DonHang` FOREIGN KEY (`maDonHang`) REFERENCES `donhang` (`maDonHang`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
