-- Hoan thien quy trinh doi tra: xac nhan nhap kho theo dong va doi soat hoan tien.
-- Chay mot lan tren database da co migration 12/13.
ALTER TABLE YeuCauDoiTra
    ADD COLUMN hinhThucHoanTien VARCHAR(60) NULL,
    ADD COLUMN maGiaoDichHoanTien VARCHAR(150) NULL,
    ADD COLUMN ngayHoanTien DATETIME NULL;

ALTER TABLE ChiTietDoiTra
    ADD COLUMN daNhapKho BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ngayNhapKho DATETIME NULL;
