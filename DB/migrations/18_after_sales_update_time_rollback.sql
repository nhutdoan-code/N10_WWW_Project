-- ROLLBACK MIGRATION 18: bo cot ngayCapNhat / ghiChuNoiBo da them cho hau mai, doi tra.
-- CANH BAO: mat du lieu ghi chu noi bo va thoi diem cap nhat. Sao luu truoc khi chay.
ALTER TABLE YeuCauHauMai DROP COLUMN ghiChuNoiBo;
ALTER TABLE YeuCauDoiTra DROP COLUMN ghiChuNoiBo;
ALTER TABLE YeuCauDoiTra DROP COLUMN ngayCapNhat;
