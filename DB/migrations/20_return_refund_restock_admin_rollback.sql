-- ROLLBACK MIGRATION 20: bo cot doi soat hoan tien / nhap lai kho cua doi tra.
-- CANH BAO: mat du lieu hinh thuc hoan tien, ma giao dich hoan tien va trang thai da nhap kho. Sao luu truoc khi chay.
ALTER TABLE ChiTietDoiTra
    DROP COLUMN ngayNhapKho,
    DROP COLUMN daNhapKho;

ALTER TABLE YeuCauDoiTra
    DROP COLUMN ngayHoanTien,
    DROP COLUMN maGiaoDichHoanTien,
    DROP COLUMN hinhThucHoanTien;
