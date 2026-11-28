-- Migration 29: stage online checkout without creating an order before payment.
-- Run after migration 28.
CREATE TABLE PhienThanhToan (
    maPhienThanhToan VARCHAR(50) PRIMARY KEY,
    maKh VARCHAR(50) NOT NULL,
    idempotencyKey VARCHAR(36) NOT NULL,
    requestHash VARCHAR(64) NOT NULL,
    phuongThuc ENUM('CHUYEN_KHOAN','VI_DIEN_TU') NOT NULL,
    soTien DECIMAL(15,2) NOT NULL,
    duLieuCheckout LONGTEXT NOT NULL,
    trangThai ENUM('CHO_THANH_TOAN','DA_TAO_DON','THAT_BAI','CAN_HOAN_TIEN','DA_HOAN_TIEN') NOT NULL,
    payUrl VARCHAR(3000),
    maDonHang VARCHAR(50),
    maGiaoDichNhaCungCap VARCHAR(100),
    maGiaoDichHoanTien VARCHAR(100),
    lyDoHoanTien VARCHAR(500),
    ngayTao DATETIME NOT NULL,
    ngayCapNhat DATETIME NOT NULL,
    CONSTRAINT uq_PhienThanhToan_maKh_idempotencyKey UNIQUE (maKh, idempotencyKey),
    CONSTRAINT FK_PhienThanhToan_KhachHang FOREIGN KEY (maKh)
        REFERENCES KhachHang(maKh) ON UPDATE CASCADE ON DELETE CASCADE
);
