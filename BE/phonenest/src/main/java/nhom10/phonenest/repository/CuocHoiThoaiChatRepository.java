package nhom10.phonenest.repository;

/*
 * @description
 * @author: LuuVanQuoc
 * @date: 11/15/2026
 * @version: 1.0
 */
import nhom10.phonenest.entity.CuocHoiThoaiChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CuocHoiThoaiChatRepository
        extends JpaRepository<CuocHoiThoaiChat, String> {

    // Lấy danh sách hội thoại của tài khoản, mới cập nhật trước
    List<CuocHoiThoaiChat> findByMaTkOrderByNgayCapNhatDesc(String maTk);

    // Chỉ lấy hội thoại nếu đúng chủ sở hữu
    Optional<CuocHoiThoaiChat> findByIdAndMaTk(String id, String maTk);
}
