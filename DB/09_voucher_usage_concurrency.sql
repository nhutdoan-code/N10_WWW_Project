-- Migration 09: Voucher concurrency & usage limits (PNE-003)
USE BanDienThoai;
SET NAMES utf8mb4;

-- 1. Index hỗ trợ tra cứu lịch sử sử dụng voucher theo đơn hàng
CREATE INDEX IF NOT EXISTS IDX_SuDungKhuyenMai_DonHang
    ON SuDungKhuyenMai(maDonHang);

-- 2. Index hỗ trợ kiểm tra giới hạn mỗi khách hàng (maKm, maKh)
CREATE INDEX IF NOT EXISTS IDX_SuDungKhuyenMai_Km_Kh
    ON SuDungKhuyenMai(maKm, maKh);

-- 3. Ràng buộc daDung không âm
ALTER TABLE KhuyenMai
    ADD CONSTRAINT CK_KhuyenMai_DaDungNonNegative CHECK (daDung >= 0);
