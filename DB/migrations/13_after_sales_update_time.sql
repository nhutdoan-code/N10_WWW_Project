-- Chay mot lan sau DB/migrations/12_after_sales.sql, tren database BanDienThoai.
-- Ghi nhan thoi diem cap nhat trang thai doi/tra de thong ke 'Hoan tat hom nay'.
ALTER TABLE YeuCauDoiTra ADD COLUMN ngayCapNhat DATETIME NULL;

-- Ghi chu noi bo chi Admin co quyen xem/sua (khong dua vao DTO tra cho customer).
ALTER TABLE YeuCauDoiTra ADD COLUMN ghiChuNoiBo VARCHAR(1000) NULL;
ALTER TABLE YeuCauHauMai ADD COLUMN ghiChuNoiBo VARCHAR(1000) NULL;
