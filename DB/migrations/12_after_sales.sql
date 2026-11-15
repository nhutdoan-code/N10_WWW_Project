-- PhoneNest - Nhom 10 - Feature 5: Bao hanh, khieu nai, theo doi yeu cau.
-- Chay MOT LAN tren database BanDienThoai da co du lieu. KHONG DROP bang.
-- Khong chay tren DB da co hai bang nay.
CREATE TABLE YeuCauHauMai (
    maYeuCau VARCHAR(50) PRIMARY KEY,
    maDonHang VARCHAR(50) NOT NULL,
    maChiTietDonHang VARCHAR(50) NULL,
    loai ENUM('BAO_HANH','KHIEU_NAI') NOT NULL,
    trangThai ENUM('CHO_TIEP_NHAN','DANG_XU_LY','HOAN_TAT','TU_CHOI') NOT NULL DEFAULT 'CHO_TIEP_NHAN',
    lyDo TEXT NOT NULL,
    hinhAnhMinhChung VARCHAR(500),
    ghiChuXuLy VARCHAR(500),
    ngayTao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngayCapNhat DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_YeuCauHauMai_DonHang FOREIGN KEY (maDonHang) REFERENCES DonHang(maDonHang),
    CONSTRAINT FK_YeuCauHauMai_ChiTiet FOREIGN KEY (maChiTietDonHang) REFERENCES ChiTietDonHang(maChiTiet),
    INDEX IDX_HauMai_DonHang (maDonHang, ngayTao),
    INDEX IDX_HauMai_TrangThai (trangThai)
);

CREATE TABLE LichSuHauMai (
    maLichSu VARCHAR(50) PRIMARY KEY,
    maYeuCau VARCHAR(50) NOT NULL,
    trangThai ENUM('CHO_TIEP_NHAN','DANG_XU_LY','HOAN_TAT','TU_CHOI') NOT NULL,
    ghiChu VARCHAR(500),
    thoiGian DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_LichSuHauMai_YeuCau FOREIGN KEY (maYeuCau) REFERENCES YeuCauHauMai(maYeuCau) ON DELETE CASCADE,
    INDEX IDX_LichSuHauMai_YeuCau (maYeuCau, thoiGian)
);
