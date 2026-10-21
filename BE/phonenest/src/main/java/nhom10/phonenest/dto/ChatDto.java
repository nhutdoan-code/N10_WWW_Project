package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatDto() {

    public record Turn(String role, String content) {
    }

    public record Request(
            @NotBlank(message = "Vui lòng nhập câu hỏi") @Size(max = 500, message = "Câu hỏi tối đa 500 ký tự") String message,
            List<Turn> history) {
    }

    public record AiReply(String reply, List<String> maSanPham) {
    }

    public record Response(String reply, List<SanPhamDto> products) {
    }
}
