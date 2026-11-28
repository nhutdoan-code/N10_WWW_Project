-- Require a customer acknowledgment before closing completed after-sales work.
-- Run after migration 31 on existing databases; fresh installs also need the
-- matching columns in phonenest_full.sql.
ALTER TABLE YeuCauDoiTra
    ADD COLUMN trangThaiXacNhanKhach VARCHAR(24) NULL,
    ADD COLUMN hanXacNhanKhach DATETIME NULL,
    ADD COLUMN ghiChuXacNhanKhach VARCHAR(500) NULL;

ALTER TABLE YeuCauHauMai
    ADD COLUMN trangThaiXacNhanKhach VARCHAR(24) NULL,
    ADD COLUMN hanXacNhanKhach DATETIME NULL,
    ADD COLUMN ghiChuXacNhanKhach VARCHAR(500) NULL;
