
package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record ChatDto() {

    // Request gửi câu hỏi đến ChatBot
    public record Request(
            @NotBlank(message = "Vui lòng nhập câu hỏi")
            @Size(max = 500, message = "Câu hỏi tối đa 500 ký tự")
            String message,

            @Pattern(
                    regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                    message = "ID cuộc hội thoại không hợp lệ"
            )
            String conversationId,

            @Pattern(
                    regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
            )
            String visitorId
    ) {
    }

    // Lịch sử tin nhắn gửi cho AI (nếu cần dùng nội bộ)
    public record Turn(String role, String content) {
    }

    // Kết quả AI trả về
    public record AiReply(
            String reply,
            List<String> maSanPham
    ) {
    }

    // Response trả về frontend
    public record Response(
            String reply,
            List<SanPhamDto> products,
            String mode,
            String conversationId
    ) {
        public Response(
                String reply,
                List<SanPhamDto> products,
                String mode
        ) {
            this(reply, products, mode, null);
        }

        public Response(
                String reply,
                List<SanPhamDto> products
        ) {
            this(reply, products, "KEYWORD_FALLBACK", null);
        }
    }

    // Thông tin một cuộc hội thoại
    public record ConversationResponse(
            String id,
            String tieuDe,
            LocalDateTime ngayTao,
            LocalDateTime ngayCapNhat
    ) {
    }

    // Thông tin một tin nhắn
    public record MessageResponse(
            Long id,
            String role,
            String noiDung,
            LocalDateTime ngayTao
    ) {
    }
}
