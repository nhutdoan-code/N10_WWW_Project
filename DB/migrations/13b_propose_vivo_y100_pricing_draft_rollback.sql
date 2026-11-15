-- ==============================================================================
-- PHONENEST MIGRATION 13b PRICING DRAFT ROLLBACK — DA VO HIEU HOA (2B.7D)
--
-- Ban AGY cua script nay hoan tac GIA THI TRUONG CHUA XAC MINH (khao sat 11/2024) bang SQL truc tiep,
-- tao bang backup va stored procedure TRUOC khi kiem tra database dich. Theo quyet dinh cua chu du an
-- ("khong tu ap dung gia chua xac minh", "khong dung bang khao sat 11/2024"), gia chi duoc nhap qua
-- giao dien Admin va phai qua quy trinh phe duyet co nguon xac minh (PheDuyetBanHangService).
-- Sau Migration 15/16, sua gia bang SQL cung khong mo ban duoc (dau van tay phe duyet khong khop).
--
-- Script nay KHONG GHI GI: chi tra ve REFUSED_DEPRECATED. Ban goc duoc luu ngoai repo tai
-- phonenest_backup_2b7d_20261114/before/DB/migrations/.
-- ==============================================================================
SELECT 'REFUSED_DEPRECATED' AS ketQua,
       'Script gia chua xac minh da bi vo hieu hoa. Nhap gia qua Admin + phe duyet co nguon.' AS chiTiet;
