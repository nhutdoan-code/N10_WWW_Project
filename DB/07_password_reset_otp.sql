-- Migration 07: Bảng quản lý mã OTP đặt lại mật khẩu
USE BanDienThoai;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS password_reset_otp (
    id BIGINT NOT NULL AUTO_INCREMENT,
    maTk VARCHAR(50) NOT NULL,
    otpHash VARCHAR(255) NOT NULL,
    createdAt DATETIME NOT NULL,
    otpExpiresAt DATETIME NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    otpVerified BOOLEAN NOT NULL DEFAULT FALSE,
    resetTokenHash VARCHAR(255) DEFAULT NULL,
    resetExpiresAt DATETIME DEFAULT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_reset_otp_account (maTk),
    KEY idx_reset_otp_token (resetTokenHash),
    CONSTRAINT fk_reset_otp_taikhoan FOREIGN KEY (maTk) REFERENCES TaiKhoan(maTk) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
