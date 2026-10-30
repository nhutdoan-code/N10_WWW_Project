package nhom10.phonenest.dto;

import java.util.List;

/**
 * Mot trang ket qua dung chung cho API danh sach (Phase 5).
 */
public record TrangDto<T>(
        List<T> noiDung,
        int trang,
        int kichThuoc,
        long tongSo,
        int tongTrang) {
}
