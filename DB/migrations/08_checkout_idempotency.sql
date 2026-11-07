-- Migration 08: Bảng quản lý Idempotency cho Checkout (PNE-004)
USE BanDienThoai;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS checkout_idempotency (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    order_id VARCHAR(50) DEFAULT NULL,
    response_payload LONGTEXT DEFAULT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_idempotency (user_id, idempotency_key),
    KEY idx_idempotency_order (order_id),
    KEY idx_idempotency_created (created_at),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES TaiKhoan(maTk) ON DELETE CASCADE,
    CONSTRAINT fk_idempotency_order FOREIGN KEY (order_id) REFERENCES DonHang(maDonHang) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
