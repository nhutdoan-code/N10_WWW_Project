-- Run only when reverting the customer-confirmation feature and its backend.
ALTER TABLE YeuCauDoiTra
    DROP COLUMN ghiChuXacNhanKhach,
    DROP COLUMN hanXacNhanKhach,
    DROP COLUMN trangThaiXacNhanKhach;

ALTER TABLE YeuCauHauMai
    DROP COLUMN ghiChuXacNhanKhach,
    DROP COLUMN hanXacNhanKhach,
    DROP COLUMN trangThaiXacNhanKhach;
