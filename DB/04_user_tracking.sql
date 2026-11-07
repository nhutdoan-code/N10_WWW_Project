USE BanDienThoai;

CREATE TABLE IF NOT EXISTS HanhViNguoiDung (
    maHanhVi VARCHAR(36) PRIMARY KEY,
    visitorId VARCHAR(36) NOT NULL,
    maTk VARCHAR(50),
    loaiHanhVi VARCHAR(20) NOT NULL,
    productId VARCHAR(50),
    searchTerm VARCHAR(120),
    pageType VARCHAR(30),
    thoiGian DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_HanhViNguoiDung_TaiKhoan FOREIGN KEY (maTk)
        REFERENCES TaiKhoan(maTk) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT FK_HanhViNguoiDung_SanPham FOREIGN KEY (productId)
        REFERENCES SanPham(maSanPham) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX IDX_HanhViNguoiDung_Visitor_Time (visitorId, thoiGian),
    INDEX IDX_HanhViNguoiDung_TaiKhoan_Time (maTk, thoiGian),
    INDEX IDX_HanhViNguoiDung_Product_Time (productId, thoiGian),
    INDEX IDX_HanhViNguoiDung_Time (thoiGian)
);
