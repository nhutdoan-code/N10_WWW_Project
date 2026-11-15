-- ROLLBACK MIGRATION 19: bo voucher KHACHMOI10.
-- Neu ma da duoc dung (co dong SuDungKhuyenMai) thi KHONG xoa duoc vi khoa ngoai giu lich su don hang:
-- khi do chi tat ma (trangThaiHoatDong = 0) de giu nguyen lich su.
UPDATE KhuyenMai SET trangThaiHoatDong = 0 WHERE maKm = 'KHACHMOI10';

DELETE FROM KhuyenMai
WHERE maKm = 'KHACHMOI10'
  AND NOT EXISTS (SELECT 1 FROM SuDungKhuyenMai s WHERE s.maKm = 'KHACHMOI10')
  AND NOT EXISTS (SELECT 1 FROM DonHang d WHERE d.maKm = 'KHACHMOI10');
