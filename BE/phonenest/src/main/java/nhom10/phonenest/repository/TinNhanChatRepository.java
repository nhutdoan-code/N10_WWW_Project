package nhom10.phonenest.repository;

/*
 * @description
 * @author: LuuVanQuoc
 * @date: 11/15/2026
 * @version: 1.0
 */
import nhom10.phonenest.entity.TinNhanChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TinNhanChatRepository
        extends JpaRepository<TinNhanChat, Long> {

    // Lấy tin nhắn theo thứ tự thời gian tăng dần
    List<TinNhanChat> findByCuocHoiThoaiIdOrderByNgayTaoAscIdAsc(
            String cuocHoiThoaiId
    );

    // Lấy 10 tin nhắn gần nhất để gửi cho AI
    List<TinNhanChat> findTop10ByCuocHoiThoaiIdOrderByNgayTaoDescIdDesc(
            String cuocHoiThoaiId
    );

    // Xóa tin nhắn thuộc một cuộc hội thoại
    void deleteByCuocHoiThoaiId(String cuocHoiThoaiId);
}
