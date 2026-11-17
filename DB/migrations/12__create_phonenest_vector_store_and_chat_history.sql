CREATE TABLE phonenest_vector_store (
                                        id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                                        content TEXT DEFAULT NULL,
                                        metadata LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (JSON_VALID(metadata)),
                                        embedding VECTOR(1024) NOT NULL,
                                        PRIMARY KEY (id),
                                        VECTOR KEY phonenest_vector_store_embedding_idx (embedding)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;





-- =========================================
-- BẢNG 1: Lưu các cuộc hội thoại
-- =========================================
CREATE TABLE CuocHoiThoaiChat (
                                  id VARCHAR(36) NOT NULL,
                                  maTk VARCHAR(50) NOT NULL,
                                  tieuDe VARCHAR(255) DEFAULT NULL,
                                  ngayTao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  ngayCapNhat DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

                                  PRIMARY KEY (id),

                                  INDEX idx_chat_conversation_user (maTk),
                                  INDEX idx_chat_conversation_updated (maTk, ngayCapNhat),

                                  CONSTRAINT fk_chat_conversation_account
                                      FOREIGN KEY (maTk)
                                          REFERENCES TaiKhoan(maTk)
                                          ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================
-- BẢNG 2: Lưu từng tin nhắn
-- =========================================
CREATE TABLE TinNhanChat (
                             id BIGINT NOT NULL AUTO_INCREMENT,
                             cuocHoiThoaiId VARCHAR(36) NOT NULL,
                             role VARCHAR(20) NOT NULL,
                             noiDung TEXT NOT NULL,
                             ngayTao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             PRIMARY KEY (id),

                             INDEX idx_chat_message_conversation (cuocHoiThoaiId, ngayTao),

                             CONSTRAINT fk_chat_message_conversation
                                 FOREIGN KEY (cuocHoiThoaiId)
                                     REFERENCES CuocHoiThoaiChat(id)
                                     ON DELETE CASCADE,

                             CONSTRAINT chk_chat_message_role
                                 CHECK (role IN ('USER', 'ASSISTANT'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
