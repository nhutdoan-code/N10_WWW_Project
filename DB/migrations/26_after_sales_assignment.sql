-- M26: Phan cong nguoi xu ly va han cho tung yeu cau hau mai.
-- Lich su thay doi phan cong van duoc luu trong NhatKyHoatDong.
CREATE TABLE IF NOT EXISTS PhanCongHauMai (
    maPhanCong VARCHAR(50) NOT NULL,
    nguon VARCHAR(12) NOT NULL,
    maYeuCau VARCHAR(50) NOT NULL,
    maNguoiPhuTrach VARCHAR(50) NOT NULL,
    tenNguoiPhuTrach VARCHAR(150) NOT NULL,
    hanXuLy DATETIME NOT NULL,
    capNhatBoi VARCHAR(50) NOT NULL,
    capNhatLuc DATETIME NOT NULL,
    PRIMARY KEY (maPhanCong),
    UNIQUE KEY UQ_PhanCongHauMai_YeuCau (nguon, maYeuCau),
    KEY IDX_PhanCongHauMai_NguoiHan (maNguoiPhuTrach, hanXuLy),
    CONSTRAINT FK_PhanCongHauMai_NguoiPhuTrach FOREIGN KEY (maNguoiPhuTrach) REFERENCES TaiKhoan (maTk) ON UPDATE CASCADE,
    CONSTRAINT FK_PhanCongHauMai_CapNhatBoi FOREIGN KEY (capNhatBoi) REFERENCES TaiKhoan (maTk) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
