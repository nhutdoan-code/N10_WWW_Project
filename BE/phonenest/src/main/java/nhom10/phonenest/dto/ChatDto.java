package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record ChatDto() {

    public record Turn(String role, String content) {
    }

    public record Request(
            @NotBlank(message = "Vui lòng nhập câu hỏi") @Size(max = 500, message = "Câu hỏi tối đa 500 ký tự") String message,
            List<Turn> history,
            @Pattern(regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            String visitorId) {
    }

    public record AiReply(String reply, List<String> maSanPham) {
    }

    public record Response(String reply, List<SanPhamDto> products, String mode) {
        public Response(String reply, List<SanPhamDto> products) {
            this(reply, products, "KEYWORD_FALLBACK");
        }
    }
}
