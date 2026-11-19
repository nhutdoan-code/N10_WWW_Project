-- MIGRATION 21: luu lich su hoi thoai ChatBox theo tai khoan + vector store san pham trong MariaDB.
-- (Thay cho file 12__create_phonenest_vector_store_and_chat_history.sql cua nhanh QuocLuu: so 12 da dung cho
--  12_streamline_products_75.sql nen doi thanh 21, chay sau migration 20.)
--
-- Yeu cau MariaDB >= 11.7 (kieu VECTOR + VECTOR INDEX); may kiem thu dung MariaDB 12.3.
-- Chay mot lan tren database BanDienThoai:
--   mariadb -h 127.0.0.1 -P 3307 -u root -p BanDienThoai < DB/migrations/21_chat_history_vector_store.sql
-- CREATE TABLE nghiem ngat (khong IF NOT EXISTS): chay lai se bao loi bang da ton tai.
--
-- Backend KHONG tu tao bang (VectorStoreConfig.initializeSchema(false)) de tai khoan DB chi co
-- SELECT/INSERT/UPDATE/DELETE van khoi dong duoc.

-- 1. Vector store cua Spring AI MariaDBVectorStore (1024 chieu = Ollama bge-m3).
--    id = maSanPham (khong phai UUID) nen dung VARCHAR.
CREATE TABLE phonenest_vector_store (
    id        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content   TEXT DEFAULT NULL,
    metadata  LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (JSON_VALID(metadata)),
    embedding VECTOR(1024) NOT NULL,
    PRIMARY KEY (id),
    VECTOR KEY phonenest_vector_store_embedding_idx (embedding)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Dau van tay noi dung da embedding cua tung san pham: chi goi lai Ollama khi tai lieu thay doi.
CREATE TABLE product_embedding_sync (
    ma_san_pham  VARCHAR(100) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ma_san_pham)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Cuoc hoi thoai ChatBox (moi tai khoan chi thay hoi thoai cua minh).
CREATE TABLE CuocHoiThoaiChat (
    id          VARCHAR(36) NOT NULL,
    maTk        VARCHAR(50) NOT NULL,
    tieuDe      VARCHAR(255) DEFAULT NULL,
    ngayTao     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngayCapNhat DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_chat_conversation_updated (maTk, ngayCapNhat),
    CONSTRAINT fk_chat_conversation_account FOREIGN KEY (maTk) REFERENCES TaiKhoan (maTk) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Tung tin nhan trong cuoc hoi thoai.
CREATE TABLE TinNhanChat (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    cuocHoiThoaiId VARCHAR(36) NOT NULL,
    role           VARCHAR(20) NOT NULL,
    noiDung        TEXT NOT NULL,
    ngayTao        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_chat_message_conversation (cuocHoiThoaiId, ngayTao),
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (cuocHoiThoaiId) REFERENCES CuocHoiThoaiChat (id) ON DELETE CASCADE,
    CONSTRAINT chk_chat_message_role CHECK (role IN ('USER', 'ASSISTANT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
