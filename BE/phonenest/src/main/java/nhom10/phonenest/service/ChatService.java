package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.ChatDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.KhuyenMai;
import nhom10.phonenest.entity.PhiVanChuyen;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import nhom10.phonenest.repository.PhiVanChuyenRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.TinhThanhRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int LUOT_NHO = 10;
    private static final Pattern TRIEU = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(triệu|tr)\\b");

    private static final String PROMPT = """
            Bạn là trợ lý tư vấn bán hàng của cửa hàng điện thoại PhoneNest. Trả lời bằng tiếng Việt, thân thiện, ngắn gọn (tối đa 5 câu).
            Chỉ dùng thông tin trong phần DỮ LIỆU CỬA HÀNG bên dưới; không bịa thông số, giá hay chính sách. Nếu không có thông tin, hãy nói chưa có và gợi ý khách liên hệ cửa hàng.
            Khi tư vấn máy, hãy hỏi thêm nhu cầu (ngân sách, chụp ảnh, chơi game, pin...) nếu khách chưa nói rõ. Không gợi ý sản phẩm đã hết hàng.
            Trả về JSON gồm: "reply" là câu trả lời, "maSanPham" là mảng mã sản phẩm bạn đề xuất (tối đa 3, để [] nếu không đề xuất máy nào).

            DỮ LIỆU CỬA HÀNG
            Sản phẩm (mã | tên | hãng | giá | tình trạng | thông số):
            {sanPham}

            Voucher đang chạy:
            {voucher}

            Phí vận chuyển:
            {phiShip}
            """;

    private final SanPhamService sanPhamService;
    private final SanPhamRepository sanPhamRepository;
    private final KhuyenMaiRepository khuyenMaiRepository;
    private final PhiVanChuyenRepository phiVanChuyenRepository;
    private final TinhThanhRepository tinhThanhRepository;
    private final ChatClient chatClient;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public ChatDto.Response chat(ChatDto.Request req) {
        String cauHoi = req.message().trim();
        if (coKhoaAi()) {
            try {
                ChatDto.AiReply kq = chatClient.prompt()
                        .system(s -> s.text(PROMPT).param("sanPham", sanPhamText())
                                .param("voucher", voucherText()).param("phiShip", phiShipText()))
                        .messages(lichSu(req.history()))
                        .user(cauHoi)
                        .call()
                        .entity(ChatDto.AiReply.class);
                return new ChatDto.Response(kq.reply(), sanPhamGoiY(kq.maSanPham()));
            } catch (Exception e) {
                // chuyển sang trả lời theo từ khóa
            }
        }
        return traLoiTheoTuKhoa(cauHoi);
    }

    private boolean coKhoaAi() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("chua-cau-hinh");
    }

    private List<Message> lichSu(List<ChatDto.Turn> history) {
        if (history == null) {
            return List.of();
        }
        return history.stream().skip(Math.max(0, history.size() - LUOT_NHO))
                .filter(t -> t.content() != null && !t.content().isBlank())
                .map(t -> "assistant".equals(t.role())
                        ? (Message) new AssistantMessage(cat(t.content()))
                        : new UserMessage(cat(t.content())))
                .toList();
    }

    private String cat(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private List<SanPhamDto> sanPhamGoiY(List<String> ma) {
        if (ma == null) {
            return List.of();
        }
        return ma.stream().limit(3)
                .map(sanPhamRepository::findById).flatMap(Optional::stream)
                .filter(s -> s.getTrangThai() == TrangThaiSanPham.CON_HANG)
                .map(SanPhamMapper::toDto)
                .toList();
    }

    private ChatDto.Response traLoiTheoTuKhoa(String cauHoi) {
        String k = cauHoi.toLowerCase();
        if (k.contains("ship") || k.contains("giao hàng") || k.contains("vận chuyển")) {
            return new ChatDto.Response("Phí vận chuyển của PhoneNest:\n" + phiShipText()
                    + "\nPhí được tính theo tỉnh/thành của địa chỉ nhận hàng và hiển thị rõ ở bước thanh toán.", List.of());
        }
        if (k.contains("khuyến mãi") || k.contains("voucher") || k.contains("giảm giá") || k.contains("mã giảm")) {
            return new ChatDto.Response("Hiện cửa hàng có các voucher:\n" + voucherText()
                    + "\nVoucher tốt nhất sẽ được chọn sẵn ở bước thanh toán.", List.of());
        }
        BigDecimal toiDa = nganSach(k);
        List<SanPhamDto> kq = sanPhamService.list(null, null, null, toiDa, "ban_chay", false).stream()
                .filter(s -> s.trangThai() == TrangThaiSanPham.CON_HANG)
                .filter(s -> khop(s, k))
                .limit(3).toList();
        if (kq.isEmpty()) {
            kq = sanPhamService.list(null, null, null, toiDa, null, false).stream()
                    .filter(s -> s.trangThai() == TrangThaiSanPham.CON_HANG)
                    .sorted(Comparator.comparing(SanPhamDto::diemDanhGiaTb).reversed())
                    .limit(3).toList();
        }
        String mo = toiDa == null ? "Mình gợi ý vài mẫu đang được yêu thích:"
                : "Với ngân sách khoảng " + tien(toiDa) + ", mình gợi ý:";
        return kq.isEmpty()
                ? new ChatDto.Response("Mình chưa tìm thấy mẫu nào phù hợp. Bạn thử nói rõ hãng, ngân sách hoặc nhu cầu (chụp ảnh, chơi game, pin...) nhé.", List.of())
                : new ChatDto.Response(mo, kq);
    }

    private boolean khop(SanPhamDto s, String k) {
        String noiDung = (s.tenMay() + " " + s.tenLoai() + " " + s.thongSoKyThuat()).toLowerCase();
        return List.of(k.split("[^\\p{L}\\p{N}]+")).stream()
                .filter(t -> t.length() >= 3 && !t.matches("\\d+"))
                .anyMatch(noiDung::contains);
    }

    private BigDecimal nganSach(String k) {
        Matcher m = TRIEU.matcher(k);
        return m.find() ? new BigDecimal(m.group(1).replace(',', '.')).multiply(BigDecimal.valueOf(1_000_000)) : null;
    }

    private String sanPhamText() {
        return sanPhamService.list(null, null, null, null, null, false).stream()
                .map(s -> "%s | %s | %s | %s VND | %s | %s".formatted(s.maSanPham(), s.tenMay(), s.tenLoai(),
                        s.giaBan().toBigInteger(), s.trangThai() == TrangThaiSanPham.CON_HANG ? "còn hàng" : "hết hàng",
                        s.thongSoKyThuat()))
                .collect(Collectors.joining("\n"));
    }

    private String voucherText() {
        LocalDate hom = LocalDate.now();
        String ds = khuyenMaiRepository.findAll().stream()
                .filter(k -> !hom.isBefore(k.getNgayBatDau()) && !hom.isAfter(k.getNgayKetThuc()))
                .map(this::moTaVoucher).collect(Collectors.joining("\n"));
        return ds.isEmpty() ? "Hiện chưa có voucher." : ds;
    }

    private String moTaVoucher(KhuyenMai k) {
        String loai = k.getLoai().name().equals("VAN_CHUYEN") ? "phí vận chuyển" : "đơn hàng";
        String toiDa = k.getSoTienGiamToiDa() == null ? "" : ", tối đa " + tien(k.getSoTienGiamToiDa());
        String toiThieu = k.getDonToiThieu().signum() > 0 ? ", đơn từ " + tien(k.getDonToiThieu()) : "";
        return "- %s (%s): giảm %s%% %s%s%s".formatted(k.getMaKm(), k.getTenKm(), k.getPhanTramGiam().stripTrailingZeros().toPlainString(),
                loai, toiDa, toiThieu);
    }

    private String phiShipText() {
        return phiVanChuyenRepository.findAll().stream()
                .sorted(Comparator.comparing((PhiVanChuyen p) -> p.getMaTinh() == null))
                .map(p -> "- %s: %s%s, giao %s".formatted(
                        p.getMaTinh() == null ? "Nơi khác" : tinhThanhRepository.findById(p.getMaTinh()).map(t -> t.getTenTinh()).orElse(p.getMaTinh()),
                        tien(p.getPhiCoBan()),
                        p.getMienPhiTu() == null ? "" : " (miễn phí cho đơn từ " + tien(p.getMienPhiTu()) + ")",
                        p.getThoiGianGiao()))
                .collect(Collectors.joining("\n"));
    }

    private String tien(BigDecimal n) {
        return NumberFormat.getInstance(Locale.of("vi", "VN")).format(n) + " ₫";
    }
}
