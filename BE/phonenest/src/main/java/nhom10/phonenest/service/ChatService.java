package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.ChatDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.dto.SemanticSearchResultDto;
import nhom10.phonenest.entity.KhuyenMai;
import nhom10.phonenest.entity.PhiVanChuyen;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import nhom10.phonenest.repository.PhiVanChuyenRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.TinhThanhRepository;
import nhom10.phonenest.service.ai.SemanticSearchService;
import nhom10.phonenest.util.SecurityUtil;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

            Sản phẩm khách gần đây quan tâm (chỉ là gợi ý tham khảo, không khẳng định khách đã mua):
            {hanhVi}
            Nếu phù hợp với câu hỏi hiện tại, ưu tiên các sản phẩm này khi tư vấn.
            """;

    private final SanPhamService sanPhamService;
    private final SanPhamRepository sanPhamRepository;
    private final KhuyenMaiRepository khuyenMaiRepository;
    private final PhiVanChuyenRepository phiVanChuyenRepository;
    private final TinhThanhRepository tinhThanhRepository;
    private final TrackingService trackingService;
    private final ChatClient chatClient;
    private final SemanticSearchService semanticSearchService;
    private final UserPreferenceAggregationService userPreferenceService;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public ChatDto.Response chat(ChatDto.Request req) {
        String cauHoi = req.message().trim();
        TrackingService.Preferences preferences = trackingService.preferences(req.visitorId());
        List<SanPhamDto> sanPhamQuanTam = sanPhamQuanTam(preferences);
        if (coKhoaAi()) {
            try {
                String systemPrompt = PROMPT;
                String currentUserId = SecurityUtil.currentUserIdOrNull();
                if (currentUserId != null) {
                    String userContext = userPreferenceService.buildAiPromptContext(currentUserId);
                    if (!userContext.isBlank()) {
                        systemPrompt += "\n\n" + userContext;
                    }
                }

                final String finalPrompt = systemPrompt;
                ChatDto.AiReply kq = chatClient.prompt()
                        .system(s -> s.text(finalPrompt).param("sanPham", sanPhamText())
                                .param("voucher", voucherText()).param("phiShip", phiShipText())
                                .param("hanhVi", sanPhamQuanTam.isEmpty()
                                        ? "Chưa có lịch sử quan tâm phù hợp."
                                        : sanPhamQuanTam.stream()
                                                .map(this::sanPhamDong)
                                                .collect(Collectors.joining("\n"))))
                        .messages(lichSu(req.history()))
                        .user(cauHoi)
                        .call()
                        .entity(ChatDto.AiReply.class);
                return new ChatDto.Response(kq.reply(), sanPhamGoiY(kq.maSanPham()));
            } catch (Exception e) {
                // chuyển sang trả lời theo semantic search
            }
        }
        return traLoiTheoTuKhoa(cauHoi, preferences, sanPhamQuanTam);
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
                .filter(s -> s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                .map(SanPhamMapper::toDto)
                .filter(s -> s.soLuongTon() > 0)
                .toList();
    }

    private ChatDto.Response traLoiTheoTuKhoa(
            String cauHoi,
            TrackingService.Preferences preferences,
            List<SanPhamDto> sanPhamQuanTam) {
        String k = cauHoi.toLowerCase();
        if (k.contains("ship") || k.contains("giao hàng") || k.contains("vận chuyển")) {
            return new ChatDto.Response("Phí vận chuyển của PhoneNest:\n" + phiShipText()
                    + "\nPhí được tính theo tỉnh/thành của địa chỉ nhận hàng và hiển thị rõ ở bước thanh toán.", List.of());
        }
        if (k.contains("khuyến mãi") || k.contains("voucher") || k.contains("giảm giá") || k.contains("mã giảm")) {
            return new ChatDto.Response("Hiện cửa hàng có các voucher:\n" + voucherText()
                    + "\nVoucher tốt nhất sẽ được chọn sẵn ở bước thanh toán.", List.of());
        }

        // Semantic Vector AI Search
        List<SemanticSearchResultDto> semanticResults = semanticSearchService.search(cauHoi, 3);
        if (!semanticResults.isEmpty()) {
            StringBuilder reply = new StringBuilder();
            BigDecimal toiDa = nganSach(k);
            if (toiDa != null) {
                reply.append("Với ngân sách khoảng ").append(tien(toiDa)).append(", PhoneNest gợi ý cho bạn:\n");
            } else {
                reply.append("Dựa trên nhu cầu của bạn, PhoneNest gợi ý các mẫu máy phù hợp:\n");
            }
            for (var r : semanticResults) {
                reply.append("• ").append(r.sanPham().tenMay())
                        .append(" (").append(tien(r.sanPham().giaBan())).append("): ")
                        .append(r.lyDoPhuHop()).append("\n");
            }
            String mode = semanticResults.get(0).retrievalMode() != null
                    ? semanticResults.get(0).retrievalMode()
                    : "VECTOR_SEMANTIC";
            return new ChatDto.Response(reply.toString().trim(),
                    semanticResults.stream().map(SemanticSearchResultDto::sanPham).toList(), mode);
        }

        BigDecimal toiDa = nganSach(k);
        List<SanPhamDto> kq = sanPhamService.list(null, null, null, toiDa, "ban_chay", false).stream()
                .filter(this::coHang)
                .filter(s -> khop(s, k))
                .limit(3).toList();
        if (kq.isEmpty()) {
            Map<String, SanPhamDto> candidates = new LinkedHashMap<>();
            sanPhamService.list(null, null, null, toiDa, null, false).stream()
                    .filter(this::coHang)
                    .forEach(s -> candidates.put(s.maSanPham(), s));
            sanPhamQuanTam.stream()
                    .filter(s -> toiDa == null || s.giaBan().compareTo(toiDa) <= 0)
                    .forEach(s -> candidates.putIfAbsent(s.maSanPham(), s));
            kq = candidates.values().stream()
                    .sorted(Comparator
                            .comparingInt((SanPhamDto s) -> diemQuanTam(s, preferences)).reversed()
                            .thenComparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(3).toList();
        }
        String mo = toiDa == null ? "Mình gợi ý vài mẫu đang được yêu thích:"
                : "Với ngân sách khoảng " + tien(toiDa) + ", mình gợi ý:";
        return kq.isEmpty()
                ? new ChatDto.Response("Mình chưa tìm thấy mẫu nào phù hợp. Bạn thử nói rõ hãng, ngân sách hoặc nhu cầu (chụp ảnh, chơi game, pin...) nhé.", List.of())
                : new ChatDto.Response(mo, kq);
    }

    private List<SanPhamDto> sanPhamQuanTam(TrackingService.Preferences preferences) {
        Map<String, SanPhamDto> products = new LinkedHashMap<>();
        preferences.productIds().stream()
                .map(sanPhamRepository::findById).flatMap(Optional::stream)
                .filter(s -> s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                .map(SanPhamMapper::toDto)
                .filter(this::coHang)
                .forEach(s -> products.putIfAbsent(s.maSanPham(), s));
        preferences.searchTerms().stream()
                .limit(5)
                .flatMap(term -> sanPhamService.list(term, null, null, null, "ban_chay", false).stream())
                .filter(this::coHang)
                .forEach(s -> products.putIfAbsent(s.maSanPham(), s));
        return products.values().stream().limit(8).toList();
    }

    private int diemQuanTam(SanPhamDto sanPham, TrackingService.Preferences preferences) {
        int idScore = preferences.productIds().indexOf(sanPham.maSanPham());
        int score = idScore < 0 ? 0 : preferences.productIds().size() - idScore;
        if (preferences.searchTerms().stream().anyMatch(term -> khop(sanPham, term.toLowerCase()))) {
            score += 5;
        }
        return score;
    }

    private boolean coHang(SanPhamDto sanPham) {
        return sanPham.trangThai() == TrangThaiSanPham.DANG_KINH_DOANH && sanPham.soLuongTon() > 0;
    }

    private String sanPhamDong(SanPhamDto sanPham) {
        return "%s | %s | %s | %s VND | %s".formatted(
                sanPham.maSanPham(), sanPham.tenMay(), sanPham.tenThuongHieu(),
                sanPham.giaBan().toBigInteger(), sanPham.thongSo().tomTat());
    }

    private boolean khop(SanPhamDto s, String k) {
        String noiDung = (s.tenMay() + " " + s.tenLoai() + " " + (s.thongSo() != null ? s.thongSo().tomTat() : "")).toLowerCase();
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
                .filter(this::coHang)
                .map(s -> "%s | %s | %s | %s VND | %s | %s".formatted(s.maSanPham(), s.tenMay(), s.tenLoai(),
                        s.giaBan().toBigInteger(), "còn hàng",
                        s.thongSo() != null ? s.thongSo().tomTat() : ""))
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
