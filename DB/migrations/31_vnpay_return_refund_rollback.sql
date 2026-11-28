-- Roll back migration 31. In-flight refund request references are removed; reconcile with VNPay first.
ALTER TABLE YeuCauDoiTra
    DROP COLUMN thongBaoHoanVnPay,
    DROP COLUMN nguoiYeuCauHoanVnPay,
    DROP COLUMN ngayYeuCauHoanVnPay,
    DROP COLUMN maYeuCauHoanVnPay,
    DROP COLUMN trangThaiHoanVnPay;
