-- ROLLBACK MIGRATION 21: xoa lich su ChatBox va vector store trong MariaDB.
-- CANH BAO: mat toan bo lich su hoi thoai cua khach. Vector store chi la du lieu dan xuat
-- (backend tu embedding lai tu bang SanPham khi khoi dong neu bang ton tai).
-- Sau rollback, backend van chay: SemanticSearchService tu chuyen sang vector store trong bo nho.
DROP TABLE TinNhanChat;
DROP TABLE CuocHoiThoaiChat;
DROP TABLE product_embedding_sync;
DROP TABLE phonenest_vector_store;
