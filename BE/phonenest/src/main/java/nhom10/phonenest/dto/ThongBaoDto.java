package nhom10.phonenest.dto;

import java.time.LocalDateTime;

/** duongDan: trang FE cần mở khi bấm vào thông báo (null = chỉ đánh dấu đã đọc). */
public record ThongBaoDto(String maThongBao, String noiDung, boolean daDoc, LocalDateTime ngayTao, String duongDan) {
}
