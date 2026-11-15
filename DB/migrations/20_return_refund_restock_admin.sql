-- Hoan thien quy trinh doi tra: xac nhan nhap kho theo dong va doi soat hoan tien.
-- Chay mot lan tren database BanDienThoai da co migration 17/18 (hau mai).
-- (Ban dau dat ten 14_return_refund_restock_admin.sql o nhanh Phuc; doi so thanh 20 khi merge PR #24
--  vi 14 da dung cho 14_propose_flagship_pricing_draft.sql va bang hau mai la migration 17/18.)
ALTER TABLE YeuCauDoiTra
    ADD COLUMN hinhThucHoanTien VARCHAR(60) NULL,
    ADD COLUMN maGiaoDichHoanTien VARCHAR(150) NULL,
    ADD COLUMN ngayHoanTien DATETIME NULL;

ALTER TABLE ChiTietDoiTra
    ADD COLUMN daNhapKho BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ngayNhapKho DATETIME NULL;
