-- Inventory audit extensions. Apply after 01_schema.sql without dropping existing data.
ALTER TABLE BienDongKho
    ADD COLUMN nguoiThucHien VARCHAR(50) NULL,
    ADD COLUMN maYeuCau VARCHAR(50) NULL,
    ADD CONSTRAINT FK_BienDongKho_NguoiThucHien
        FOREIGN KEY (nguoiThucHien) REFERENCES TaiKhoan(maTk)
        ON UPDATE CASCADE ON DELETE SET NULL,
    ADD CONSTRAINT FK_BienDongKho_YeuCau
        FOREIGN KEY (maYeuCau) REFERENCES YeuCauDoiTra(maYeuCau)
        ON UPDATE CASCADE ON DELETE SET NULL,
    ADD CONSTRAINT CK_BienDongKho_TonNonNegative CHECK (tonTruoc >= 0 AND tonSau >= 0);

ALTER TABLE PhienBanSanPham
    ADD CONSTRAINT CK_PhienBanSanPham_TonNonNegative CHECK (soLuongTon >= 0);

CREATE INDEX IDX_BienDongKho_Loai_ThoiGian
    ON BienDongKho(loaiBienDong, thoiGian);
