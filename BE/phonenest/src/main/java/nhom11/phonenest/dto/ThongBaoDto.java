package nhom11.phonenest.dto;

import java.time.LocalDateTime;

public record ThongBaoDto(String maThongBao, String noiDung, boolean daDoc, LocalDateTime ngayTao) {
}
