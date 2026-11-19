package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record ChatDto() {

    public record Turn(String role, String content) {
    }

    /**
     * Cau hoi gui ChatBox.
     *
     * @param history        lich su do client gui (chi dung khi khong co lich su luu tren server); moi so tien bi che
     * @param conversationId cuoc hoi thoai da luu (bang CuocHoiThoaiChat); null -> tao cuoc hoi thoai moi
     */
    public record Request(
            @NotBlank(message = "Vui lòng nhập câu hỏi") @Size(max = 500, message = "Câu hỏi tối đa 500 ký tự") String message,
            List<Turn> history,
            @Pattern(regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            String visitorId,
            @Pattern(regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                    message = "ID cuộc hội thoại không hợp lệ")
            String conversationId) {

        public Request(String message, List<Turn> history, String visitorId) {
            this(message, history, visitorId, null);
        }
    }

    public record AiReply(String reply, List<String> maSanPham) {
    }

    public record Response(String reply, List<SanPhamDto> products, String mode, String conversationId) {
        public Response(String reply, List<SanPhamDto> products, String mode) {
            this(reply, products, mode, null);
        }

        public Response(String reply, List<SanPhamDto> products) {
            this(reply, products, "KEYWORD_FALLBACK", null);
        }
    }

    /** Thong tin mot cuoc hoi thoai. */
    public record ConversationResponse(String id, String tieuDe, LocalDateTime ngayTao, LocalDateTime ngayCapNhat) {
    }

    /** Thong tin mot tin nhan. */
    public record MessageResponse(Long id, String role, String noiDung, LocalDateTime ngayTao) {
    }
}
