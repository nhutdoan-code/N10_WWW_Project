package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int LUOT_NHO = 10;
    private static final Pattern TRIEU = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(triệu|tr)\\b");
    private static final Pattern RANGE = Pattern.compile("(?:từ\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:(?:triệu|tr)\\s*)?(?:đến|tới|-)\\s*(\\d+(?:[.,]\\d+)?)\\s*(triệu|tr)\\b");

    private record DieuKienGia(BigDecimal min, boolean minInclusive, BigDecimal max, boolean maxInclusive, boolean active) {
        public boolean hopLe(BigDecimal gia) {
            if (gia == null) return false;
            if (min != null) {
                int res = gia.compareTo(min);
                if (minInclusive ? res < 0 : res <= 0) return false;
            }
            if (max != null) {
                int res = gia.compareTo(max);
                if (maxInclusive ? res > 0 : res >= 0) return false;
            }
            return true;
        }
    }

    private static final String PROMPT = """
            Bạn là trợ lý tư vấn bán hàng của cửa hàng điện thoại PhoneNest.

            NHIỆM VỤ:
            - Trả lời bằng tiếng Việt, thân thiện, ngắn gọn, tối đa 5 câu.
            - Chỉ sử dụng thông tin trong phần DỮ LIỆU CỬA HÀNG.
            - Không tự bịa thông số, giá bán hoặc chính sách.
            - Nếu không có thông tin, hãy nói rõ.
            - Khi tư vấn điện thoại, ưu tiên nhu cầu của khách: ngân sách, chụp ảnh, chơi game, pin, màn hình...
            - Không gợi ý sản phẩm hết hàng.
            - Nếu khách chưa nói rõ nhu cầu, hãy hỏi thêm.

            QUY TẮC SẢN PHẨM:
            - Danh sách SẢN PHẨM được backend tìm trực tiếp từ database.
            - Sản phẩm xuất hiện trong danh sách là sản phẩm có thật.
            - Không được nói cửa hàng không có sản phẩm đã xuất hiện trong danh sách.
            - Chỉ sử dụng mã sản phẩm có trong danh sách SẢN PHẨM.
            - Không tự tạo mã sản phẩm.
            - Không tự suy luận RAM, bộ nhớ, màu sắc hoặc thông số không được cung cấp.
            - Nếu danh sách sản phẩm trống, không được tự đề xuất sản phẩm khác.
            - Chỉ đề xuất sản phẩm còn hàng.

            QUY TẮC TRẢ VỀ:
            Phải trả về JSON gồm:
            {
                "reply": "Nội dung trả lời",
                "maSanPham": ["Mã sản phẩm 1", "Mã sản phẩm 2"]
            }

            - Chỉ đề xuất tối đa 3 sản phẩm.
            - Nếu không đề xuất sản phẩm thì maSanPham phải là [].
            - Mã sản phẩm phải nằm trong danh sách SẢN PHẨM.

            DỮ LIỆU CỬA HÀNG

            Sản phẩm (mã | tên | loại | hãng | giá | tình trạng | thông số):
            {sanPham}

            Voucher đang chạy:
            {voucher}

            Phí vận chuyển:
            {phiShip}

            Sản phẩm khách gần đây quan tâm:
            {hanhVi}

            Nếu phù hợp với câu hỏi hiện tại, ưu tiên các sản phẩm khách đã quan tâm.
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

    @Value("${spring.ai.google.genai.api-key:}")
    private String apiKey;

    public ChatDto.Response chat(ChatDto.Request req) {
        String cauHoi = req.message().trim();

        List<SanPhamDto> sanPhamChinhXac = timSanPhamChinhXac(cauHoi);
        if (!sanPhamChinhXac.isEmpty() && sanPhamChinhXac.stream().noneMatch(this::coHang) && laCauHoiTonTaiSanPham(cauHoi)) {
            return new ChatDto.Response(
                    "PhoneNest có sản phẩm " + sanPhamChinhXac.get(0).tenMay() + " trong danh mục, nhưng hiện tại sản phẩm đã hết hàng. Bạn có muốn tham khảo mẫu điện thoại tương tự không?",
                    List.of(), "EXACT_PRODUCT_SEARCH"
            );
        }

        DieuKienGia dieuKienGia = phanTichDieuKienGia(cauHoi);
        if (dieuKienGia.active()) return traLoiTheoGia(dieuKienGia);

        TrackingService.Preferences preferences = trackingService.preferences(req.visitorId());
        List<SanPhamDto> sanPhamQuanTam = sanPhamQuanTam(preferences);
        List<SanPhamDto> ungVien = timSanPhamChoChatbot(cauHoi, sanPhamChinhXac);

        if (!ungVien.isEmpty() && laCauHoiTonTaiSanPham(cauHoi) && !coCauHoiTuVan(cauHoi)) {
            SanPhamDto sanPham = ungVien.get(0);
            return new ChatDto.Response(
                    "Có nhé! PhoneNest hiện có " + sanPham.tenMay() + " đang kinh doanh và còn hàng. Giá tham khảo từ " + tien(sanPham.giaBan()) + ". Bạn muốn xem thêm thông tin sản phẩm không?",
                    ungVien.stream().limit(3).toList(), "EXACT_PRODUCT_SEARCH"
            );
        }

        if (coKhoaAi()) {
            try {
                String systemPrompt = PROMPT;
                String currentUserId = SecurityUtil.currentUserIdOrNull();
                if (currentUserId != null) {
                    String userContext = userPreferenceService.buildAiPromptContext(currentUserId);
                    if (userContext != null && !userContext.isBlank()) systemPrompt += "\n\n" + userContext;
                }

                String hanhViText = sanPhamQuanTam.isEmpty() ? "Chưa có lịch sử quan tâm phù hợp." : sanPhamQuanTam.stream().map(this::sanPhamDong).collect(Collectors.joining("\n"));
                systemPrompt = systemPrompt
                        .replace("{sanPham}", sanPhamText(ungVien))
                        .replace("{voucher}", voucherText())
                        .replace("{phiShip}", phiShipText())
                        .replace("{hanhVi}", hanhViText);

                List<Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(systemPrompt));
                messages.addAll(lichSu(req.history()));
                messages.add(new UserMessage(cauHoi));

                ChatDto.AiReply kq = chatClient.prompt().messages(messages).call().entity(ChatDto.AiReply.class);
                if (kq != null && kq.reply() != null && !kq.reply().isBlank()) {
                    List<SanPhamDto> sanPhamDuocChon = sanPhamGoiY(kq.maSanPham(), ungVien);
                    if (sanPhamDuocChon.isEmpty() && !ungVien.isEmpty()) {
                        sanPhamDuocChon = ungVien.stream().limit(3).toList();
                    }
                    return new ChatDto.Response(kq.reply(), sanPhamDuocChon, "GEMINI_AI");
                }
                log.warn("Gemini không trả về nội dung hợp lệ.");
            } catch (Exception e) {
                log.error("Lỗi khi gọi Gemini AI. Chuyển sang chế độ dự phòng.", e);
            }
        } else {
            log.warn("Chưa cấu hình GEMINI_API_KEY. Chuyển sang chế độ dự phòng.");
        }

        return traLoiTheoTuKhoa(cauHoi, preferences, sanPhamQuanTam);
    }

    private List<SanPhamDto> timSanPhamChinhXac(String cauHoi) {
        String tuKhoa = chuanHoaTuKhoaSanPham(cauHoi);
        if (tuKhoa.isBlank()) return List.of();

        List<SanPhamDto> tatCaSanPham = sanPhamService.list(null, null, null, null, null, false);
        return tatCaSanPham.stream()
                .filter(Objects::nonNull)
                .filter(s -> {
                    String ten = chuanHoaVanBan(s.tenMay());
                    String ma = chuanHoaVanBan(s.maSanPham());
                    return ten.contains(tuKhoa) || ma.contains(tuKhoa);
                })
                .sorted(Comparator.comparingInt(s -> chuanHoaVanBan(s.tenMay()).equals(tuKhoa) ? 0 : 1))
                .limit(10).toList();
    }

    private List<SanPhamDto> timSanPhamChoChatbot(String cauHoi, List<SanPhamDto> sanPhamChinhXac) {
        Map<String, SanPhamDto> ketQua = new LinkedHashMap<>();
        if (sanPhamChinhXac != null) {
            sanPhamChinhXac.stream().filter(this::coHang).forEach(s -> ketQua.putIfAbsent(s.maSanPham(), s));
        }
        if (!ketQua.isEmpty()) return ketQua.values().stream().limit(8).toList();

        try {
            List<SemanticSearchResultDto> semanticResults = semanticSearchService.search(cauHoi, 8);
            if (semanticResults != null) {
                for (SemanticSearchResultDto result : semanticResults) {
                    if (result != null && result.sanPham() != null && coHang(result.sanPham())) {
                        ketQua.putIfAbsent(result.sanPham().maSanPham(), result.sanPham());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Lỗi Semantic Search khi tìm ứng viên cho chatbot.", e);
        }
        return ketQua.values().stream().limit(8).toList();
    }

    private String chuanHoaVanBan(String value) {
        if (value == null || value.isBlank()) return "";
        String result = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd');
        result = result.replaceAll("\\biphone\\b", "ip");
        result = result.replaceAll("([a-z])([0-9])", "$1 $2");
        result = result.replaceAll("([0-9])([a-z])", "$1 $2");
        return result.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String chuanHoaTuKhoaSanPham(String cauHoi) {
        String normalized = chuanHoaVanBan(cauHoi);
        Set<String> tuBoQua = Set.of("co", "khong", "shop", "cua", "hang", "ban", "minh", "toi", "muon", "tim", "cho", "hoi", "giup", "xem", "may", "dien", "thoai", "la", "nao", "nhe", "a", "vay", "khong a");
        return Arrays.stream(normalized.split("\\s+")).filter(t -> !t.isBlank() && !tuBoQua.contains(t)).collect(Collectors.joining(" "));
    }

    private boolean laCauHoiTonTaiSanPham(String cauHoi) {
        String k = chuanHoaVanBan(cauHoi);
        return (k.startsWith("co ") || k.startsWith("shop co ") || k.startsWith("cua hang co ")) && (k.endsWith(" khong") || k.endsWith(" khong a") || k.endsWith(" khong vay"));
    }

    private boolean coCauHoiTuVan(String cauHoi) {
        String k = chuanHoaVanBan(cauHoi);
        return k.contains("gia") || k.contains("ram") || k.contains("bo nho") || k.contains("camera") || k.contains("pin") || k.contains("mau sac") || k.contains("phien ban") || k.contains("so sanh") || k.contains("tu van");
    }

    private boolean coKhoaAi() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("chua-cau-hinh");
    }

    private List<SanPhamDto> sanPhamGoiY(List<String> ma, List<SanPhamDto> ungVien) {
        if (ma == null || ma.isEmpty() || ungVien == null || ungVien.isEmpty()) return List.of();
        Map<String, SanPhamDto> danhSachHopLe = ungVien.stream().filter(this::coHang).collect(Collectors.toMap(SanPhamDto::maSanPham, s -> s, (a, b) -> a, LinkedHashMap::new));
        return ma.stream().filter(Objects::nonNull).map(String::trim).filter(danhSachHopLe::containsKey).distinct().limit(3).map(danhSachHopLe::get).toList();
    }

    private DieuKienGia phanTichDieuKienGia(String cauHoi) {
        String k = cauHoi.toLowerCase(Locale.ROOT).replace(",", ".");
        Matcher rangeMatcher = RANGE.matcher(k);

        if (rangeMatcher.find()) {
            BigDecimal min = doiSangTien(rangeMatcher.group(1));
            BigDecimal max = doiSangTien(rangeMatcher.group(2));
            if (min.compareTo(max) > 0) { BigDecimal temp = min; min = max; max = temp; }
            return new DieuKienGia(min, true, max, true, true);
        }

        Matcher matcher = TRIEU.matcher(k);
        if (!matcher.find()) return new DieuKienGia(null, false, null, false, false);

        BigDecimal gia = doiSangTien(matcher.group(1));
        if (k.contains("trên") || k.contains("hơn") || k.contains("lớn hơn") || k.contains("cao hơn")) return new DieuKienGia(gia, false, null, false, true);
        if (k.contains("từ") || k.contains("ít nhất") || k.contains("tối thiểu") || k.contains("trở lên")) return new DieuKienGia(gia, true, null, false, true);
        if (k.contains("dưới") || k.contains("thấp hơn") || k.contains("nhỏ hơn") || k.contains("ít hơn")) return new DieuKienGia(null, false, gia, false, true);
        if (k.contains("không quá") || k.contains("tối đa") || k.contains("không vượt quá")) return new DieuKienGia(null, false, gia, true, true);
        if (k.contains("khoảng") || k.contains("tầm") || k.contains("quanh")) {
            BigDecimal saiSo = gia.multiply(new BigDecimal("0.1"));
            return new DieuKienGia(gia.subtract(saiSo), true, gia.add(saiSo), true, true);
        }
        return new DieuKienGia(null, false, gia, true, true);
    }

    private BigDecimal doiSangTien(String gia) {
        return new BigDecimal(gia).multiply(BigDecimal.valueOf(1_000_000));
    }

    private ChatDto.Response traLoiTheoGia(DieuKienGia dieuKienGia) {
        List<SanPhamDto> danhSach = sanPhamService.list(null, null, null, null, null, false).stream()
                .filter(this::coHang).filter(s -> dieuKienGia.hopLe(s.giaBan())).sorted(Comparator.comparing(SanPhamDto::giaBan)).limit(3).toList();

        if (danhSach.isEmpty()) {
            return new ChatDto.Response("PhoneNest hiện chưa tìm thấy điện thoại đang kinh doanh và còn hàng phù hợp với mức giá bạn yêu cầu. Bạn có thể thử một mức giá khác nhé!", List.of(), "PRICE_FILTER");
        }
        String reply = "Các điện thoại phù hợp với mức giá bạn yêu cầu:\n" + danhSach.stream().map(s -> "• " + s.tenMay() + " - " + tien(s.giaBan())).collect(Collectors.joining("\n"));
        return new ChatDto.Response(reply, danhSach, "PRICE_FILTER");
    }

    private List<Message> lichSu(List<ChatDto.Turn> history) {
        if (history == null || history.isEmpty()) return List.of();
        return history.stream()
                .skip(Math.max(0, history.size() - LUOT_NHO))
                .filter(t -> t != null && t.content() != null && !t.content().isBlank())
                .filter(t -> "assistant".equalsIgnoreCase(t.role()) || "user".equalsIgnoreCase(t.role()))
                .map(t -> "assistant".equalsIgnoreCase(t.role()) ? (Message) new AssistantMessage(cat(t.content())) : new UserMessage(cat(t.content())))
                .toList();
    }

    private String cat(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private ChatDto.Response traLoiTheoTuKhoa(String cauHoi, TrackingService.Preferences preferences, List<SanPhamDto> sanPhamQuanTam) {
        String k = cauHoi.toLowerCase(Locale.ROOT);

        if (k.contains("ship") || k.contains("giao hàng") || k.contains("vận chuyển")) {
            return new ChatDto.Response("Phí vận chuyển của PhoneNest:\n" + phiShipText() + "\nPhí được tính theo tỉnh/thành của địa chỉ nhận hàng và hiển thị rõ ở bước thanh toán.", List.of(), "KEYWORD_FALLBACK");
        }
        if (k.contains("khuyến mãi") || k.contains("voucher") || k.contains("giảm giá") || k.contains("mã giảm")) {
            return new ChatDto.Response("Hiện cửa hàng có các voucher:\n" + voucherText() + "\nVoucher tốt nhất sẽ được chọn sẵn ở bước thanh toán.", List.of(), "KEYWORD_FALLBACK");
        }

        List<SemanticSearchResultDto> semanticResults = List.of();
        try {
            semanticResults = semanticSearchService.search(cauHoi, 3);
        } catch (Exception e) {
            log.error("Lỗi khi tìm kiếm Vector Database: {}", e.getMessage(), e);
        }

        List<SemanticSearchResultDto> ketQuaSemantic = semanticResults == null ? List.of() : semanticResults.stream().filter(Objects::nonNull).filter(r -> r.sanPham() != null && coHang(r.sanPham())).toList();

        if (!ketQuaSemantic.isEmpty()) {
            StringBuilder reply = new StringBuilder();
            BigDecimal toiDa = nganSach(k);
            if (toiDa != null) reply.append("Với ngân sách khoảng ").append(tien(toiDa)).append(", PhoneNest gợi ý cho bạn:\n");
            else reply.append("Dựa trên nhu cầu của bạn, PhoneNest gợi ý các mẫu máy phù hợp:\n");

            for (SemanticSearchResultDto r : ketQuaSemantic) {
                reply.append("• ").append(r.sanPham().tenMay()).append(" (").append(tien(r.sanPham().giaBan())).append("): ").append(r.lyDoPhuHop()).append("\n");
            }
            String mode = ketQuaSemantic.get(0).retrievalMode() != null ? ketQuaSemantic.get(0).retrievalMode() : "VECTOR_SEMANTIC";
            return new ChatDto.Response(reply.toString().trim(), ketQuaSemantic.stream().map(SemanticSearchResultDto::sanPham).toList(), mode);
        }

        if (chuaKhoaModel(cauHoi)) {
            return new ChatDto.Response("PhoneNest chưa tìm thấy mẫu điện thoại đúng với tên hoặc mã sản phẩm bạn vừa hỏi. Bạn kiểm tra lại tên model giúp mình nhé!", List.of(), "KEYWORD_FALLBACK");
        }

        BigDecimal toiDa = nganSach(k);
        List<SanPhamDto> dsSanPham = sanPhamService.list(null, null, null, toiDa, "ban_chay", false);
        List<SanPhamDto> kq = dsSanPham.stream().filter(this::coHang).filter(s -> khop(s, k) || matchesNhuCau(s, k)).limit(3).toList();

        if (kq.isEmpty()) {
            Map<String, SanPhamDto> candidates = new LinkedHashMap<>();
            dsSanPham.stream().filter(this::coHang).forEach(s -> candidates.put(s.maSanPham(), s));
            sanPhamQuanTam.stream().filter(this::coHang).filter(s -> toiDa == null || (s.giaBan() != null && s.giaBan().compareTo(toiDa) <= 0)).forEach(s -> candidates.putIfAbsent(s.maSanPham(), s));

            kq = candidates.values().stream()
                    .sorted(Comparator.comparingInt((SanPhamDto s) -> diemQuanTam(s, preferences)).reversed()
                            .thenComparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(3).toList();
        }

        if (!kq.isEmpty()) {
            String mo = toiDa == null ? "Dưới đây là một số mẫu điện thoại phù hợp với yêu cầu của bạn:" : "Với ngân sách dưới " + tien(toiDa) + ", PhoneNest gợi ý bạn các mẫu máy sau:";
            return new ChatDto.Response(mo, kq, "KEYWORD_FALLBACK");
        }

        return new ChatDto.Response("PhoneNest chưa tìm thấy sản phẩm phù hợp hoàn toàn với yêu cầu của bạn. Bạn vui lòng thử tìm theo thương hiệu hoặc ngân sách chi tiết hơn nhé!", List.of(), "KEYWORD_FALLBACK");
    }

    private boolean chuaKhoaModel(String cauHoi) {
        String k = chuanHoaVanBan(cauHoi);
        return k.matches(".*\\b[a-z]+\\s*\\d+[a-z0-9 ]*.*");
    }

    private boolean matchesNhuCau(SanPhamDto s, String k) {
        if (s.thongSo() == null || s.thongSo().tomTat() == null) return false;
        String tomTat = s.thongSo().tomTat().toLowerCase(Locale.ROOT);
        if (k.contains("pin") || k.contains("pin trâu")) return tomTat.contains("mah") || tomTat.contains("pin");
        if (k.contains("game") || k.contains("cấu hình")) return tomTat.contains("snapdragon") || tomTat.contains("dimensity") || tomTat.contains("ram");
        if (k.contains("chụp") || k.contains("ảnh") || k.contains("camera")) return tomTat.contains("mp") || tomTat.contains("camera");
        return false;
    }

    private List<SanPhamDto> sanPhamQuanTam(TrackingService.Preferences preferences) {
        Map<String, SanPhamDto> products = new LinkedHashMap<>();
        preferences.productIds().stream().map(sanPhamRepository::findById).flatMap(Optional::stream)
                .filter(s -> s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                .map(SanPhamMapper::toDto).filter(this::coHang)
                .forEach(s -> products.putIfAbsent(s.maSanPham(), s));

        preferences.searchTerms().stream().limit(5)
                .flatMap(term -> sanPhamService.list(term, null, null, null, "ban_chay", false).stream())
                .filter(this::coHang)
                .forEach(s -> products.putIfAbsent(s.maSanPham(), s));

        return products.values().stream().limit(8).toList();
    }

    private int diemQuanTam(SanPhamDto sanPham, TrackingService.Preferences preferences) {
        int idScore = preferences.productIds().indexOf(sanPham.maSanPham());
        int score = idScore < 0 ? 0 : preferences.productIds().size() - idScore;
        if (preferences.searchTerms().stream().anyMatch(term -> khop(sanPham, term.toLowerCase(Locale.ROOT)))) score += 5;
        return score;
    }

    private boolean coHang(SanPhamDto sanPham) {
        return sanPham != null && sanPham.trangThai() == TrangThaiSanPham.DANG_KINH_DOANH && sanPham.soLuongTon() > 0;
    }

    private String sanPhamDong(SanPhamDto sanPham) {
        String thongSo = sanPham.thongSo() != null && sanPham.thongSo().tomTat() != null ? sanPham.thongSo().tomTat() : "Chưa có thông tin thông số";
        return "%s | %s | %s | %s VND | %s".formatted(sanPham.maSanPham(), sanPham.tenMay(), sanPham.tenThuongHieu(), sanPham.giaBan() == null ? "Chưa có giá" : sanPham.giaBan().toBigInteger(), thongSo);
    }

    private boolean khop(SanPhamDto s, String k) {
        String noiDung = chuanHoaVanBan(s.tenMay() + " " + s.maSanPham() + " " + s.tenLoai() + " " + (s.thongSo() != null ? s.thongSo().tomTat() : ""));
        String tuKhoa = chuanHoaTuKhoaSanPham(k);
        return !tuKhoa.isBlank() && noiDung.contains(tuKhoa);
    }

    private BigDecimal nganSach(String k) {
        Matcher m = TRIEU.matcher(k);
        return m.find() ? doiSangTien(m.group(1)) : null;
    }

    private String sanPhamText(List<SanPhamDto> danhSach) {
        if (danhSach == null || danhSach.isEmpty()) return "Backend chưa tìm thấy sản phẩm phù hợp.";
        String ds = danhSach.stream().filter(this::coHang)
                .map(s -> "%s | %s | %s | %s | %s VND | Còn hàng | %s".formatted(
                        s.maSanPham(), s.tenMay(), s.tenLoai(), s.tenThuongHieu(),
                        s.giaBan() == null ? "Chưa có giá" : s.giaBan().toBigInteger(),
                        s.thongSo() != null ? s.thongSo().tomTat() : "Chưa có thông số"
                ))
                .collect(Collectors.joining("\n"));
        return ds.isEmpty() ? "Backend chưa tìm thấy sản phẩm còn hàng." : ds;
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
        String toiThieu = k.getDonToiThieu() != null && k.getDonToiThieu().signum() > 0 ? ", đơn từ " + tien(k.getDonToiThieu()) : "";
        return "- %s (%s): giảm %s%% %s%s%s".formatted(k.getMaKm(), k.getTenKm(), k.getPhanTramGiam().stripTrailingZeros().toPlainString(), loai, toiDa, toiThieu);
    }

    private String phiShipText() {
        String ds = phiVanChuyenRepository.findAll().stream()
                .sorted(Comparator.comparing((PhiVanChuyen p) -> p.getMaTinh() == null))
                .map(p -> "- %s: %s%s, giao %s".formatted(
                        p.getMaTinh() == null ? "Nơi khác" : tinhThanhRepository.findById(p.getMaTinh()).map(t -> t.getTenTinh()).orElse(p.getMaTinh()),
                        tien(p.getPhiCoBan()),
                        p.getMienPhiTu() == null ? "" : " (miễn phí cho đơn từ " + tien(p.getMienPhiTu()) + ")",
                        p.getThoiGianGiao()
                )).collect(Collectors.joining("\n"));
        return ds.isEmpty() ? "Chưa có thông tin phí vận chuyển." : ds;
    }

    private String tien(BigDecimal n) {
        if (n == null) return "Chưa có thông tin";
        return NumberFormat.getInstance(Locale.of("vi", "VN")).format(n) + " ₫";
    }
}