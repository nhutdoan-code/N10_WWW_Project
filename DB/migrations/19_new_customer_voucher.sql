-- MIGRATION 19: voucher "Khách hàng mới" giảm 10% cho đơn hàng đầu tiên của mỗi tài khoản.
-- Chay mot lan tren database BanDienThoai (sau migration 18). Chi them du lieu, khong doi schema.
--
-- Quy tac (da co san trong KhuyenMaiService / DonHangService, khong can sua code):
--   * chiKhachMoi = 1      : chi dung duoc khi tai khoan chua co don nao ngoai don DA_HUY.
--   * gioiHanMoiKhach = 1  : moi tai khoan chi dung 1 lan (don bi huy thi hoan luot).
--   * soTienGiamToiDa NULL : giam dung 10% gia tri don, khong gioi han tran.
--   * soLuong NULL         : khong gioi han tong so luot (moi tai khoan moi deu duoc huong).
-- Trang thanh toan tu chon voucher giam nhieu nhat, nen khach moi duoc ap ma nay mac dinh.
--
-- Insert thuong (khong INSERT IGNORE): chay lai se loi trung khoa chinh -> bao hieu da chay roi.
INSERT INTO KhuyenMai
    (maKm, tenKm, loai, phanTramGiam, soTienGiamToiDa, donToiThieu, soLuong, daDung,
     chiKhachMoi, gioiHanMoiKhach, ngayBatDau, ngayKetThuc, dieuKienApDung, trangThaiHoatDong)
VALUES
    ('KHACHMOI10', 'Khách hàng mới giảm 10%', 'DON_HANG', 10.00, NULL, 0.00, NULL, 0,
     1, 1, '2026-01-01', '2030-12-31',
     'Dành cho khách hàng mới: giảm 10% cho đơn hàng đầu tiên, mỗi tài khoản dùng 1 lần', 1);
