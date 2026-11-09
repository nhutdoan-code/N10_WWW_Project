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

    private record DieuKienGia(BigDecimal min, boolean minInclusive, BigDecimal max, boolean maxInclusive,
                               boolean active) {
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
            - Khi khách hỏi mở (như chụp ảnh đẹp, chơi game, pin trâu, máy đáng mua, giá tốt...), hãy đề xuất ngay các sản phẩm phù hợp nhất từ danh sách SẢN PHẨM.
            - Khách KHÔNG bắt buộc phải cung cấp ngân sách, hãng hay thông số chi tiết. Nếu khách nói "giá bao nhiêu cũng được", hãy tư vấn các sản phẩm tốt nhất mà không giới hạn mức giá.
            - Giải thích ngắn gọn lý do từng sản phẩm phù hợp với nhu cầu của khách dựa trên thông số thực tế (camera, chip, pin, màn hình, giá bán...).
            - Sau khi đã gợi ý sản phẩm, có thể hỏi thêm câu hỏi gợi mở (nếu cần), tuyệt đối không từ chối tư vấn để hỏi thêm.
            - Không gợi ý sản phẩm hết hàng.

            QUY TẮC SẢN PHẨM:
            - Danh sách SẢN PHẨM được backend tìm trực tiếp từ database.
            - Sản phẩm xuất hiện trong danh sách là sản phẩm có thật và còn hàng.
            - Không được nói cửa hàng không có sản phẩm khi danh sách SẢN PHẨM có sản phẩm.
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

            - Đề xuất tối đa 3 sản phẩm phù hợp nhất.
            - Nếu không có sản phẩm nào trong danh sách SẢN PHẨM thì maSanPham phải là [].
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

        if (laCauHoiChaoHoi(cauHoi)) {
            return new ChatDto.Response(
                    "Xin chào! Mình là trợ lý AI của PhoneNest. " +
                            "Mình có thể giúp bạn tìm kiếm và tư vấn điện thoại, " +
                            "so sánh sản phẩm, kiểm tra giá bán, " +
                            "thông tin khuyến mãi và phí vận chuyển. " +
                            "Bạn cần mình hỗ trợ gì hôm nay?",
                    List.of(),
                    "GREETING"
            );
        }

        List<SanPhamDto> sanPhamChinhXac = timSanPhamChinhXac(cauHoi);
        if (!sanPhamChinhXac.isEmpty() && sanPhamChinhXac.stream().noneMatch(this::coHang) && laCauHoiTonTaiSanPham(cauHoi)) {
            return new ChatDto.Response(
                    "PhoneNest có sản phẩm " + sanPhamChinhXac.get(0).tenMay() + " trong danh mục, nhưng hiện tại sản phẩm đã hết hàng. Bạn có muốn tham khảo mẫu điện thoại tương tự không?",
                    List.of(), "EXACT_PRODUCT_SEARCH"
            );
        }

        boolean khongGioiHanGia = laGiaBaoNhieuCungDuoc(cauHoi);
        DieuKienGia dieuKienGia = khongGioiHanGia ? new DieuKienGia(null, false, null, false, false) : phanTichDieuKienGia(cauHoi);
        String brand = detectBrand(cauHoi.toLowerCase(Locale.ROOT));

        // Check if store inventory has any in-stock products
        List<SanPhamDto> tatCaKhaDung = sanPhamService.list(null, null, null, null, null, false)
                .stream().filter(this::coHang).toList();
        if (tatCaKhaDung.isEmpty()) {
            if (laCauHoiPhiShip(cauHoi)) {
                return new ChatDto.Response(
                        "Phí vận chuyển của PhoneNest:\n" + phiShipText() + "\nPhí được tính theo tỉnh/thành của địa chỉ nhận hàng và hiển thị rõ ở bước thanh toán.",
                        List.of(),
                        "KEYWORD_FALLBACK"
                );
            }
            if (laCauHoiVoucher(cauHoi)) {
                return new ChatDto.Response(
                        "Hiện cửa hàng có các voucher:\n" + voucherText() + "\nVoucher tốt nhất sẽ được chọn sẵn ở bước thanh toán.",
                        List.of(),
                        "KEYWORD_FALLBACK"
                );
            }
            return new ChatDto.Response(
                    "Hiện tại PhoneNest tạm hết hàng các sản phẩm điện thoại trong kho. Bạn vui lòng quay lại sau ít ngày hoặc liên hệ hotline để được hỗ trợ nhé!",
                    List.of(),
                    "OUT_OF_STOCK"
            );
        }

        TrackingService.Preferences preferences = trackingService.preferences(req.visitorId());
        List<SanPhamDto> sanPhamQuanTam = sanPhamQuanTam(preferences);
        if (brand != null) {
            sanPhamQuanTam = sanPhamQuanTam.stream()
                    .filter(s -> s.tenThuongHieu() != null && s.tenThuongHieu().equalsIgnoreCase(brand))
                    .toList();
        }
        List<SanPhamDto> ungVien = timSanPhamChoChatbot(cauHoi, sanPhamChinhXac, dieuKienGia, brand);

        // When a budget constraint was specified but no products fit that range
        if (ungVien.isEmpty() && dieuKienGia.active()) {
            return new ChatDto.Response(
                    "PhoneNest chưa tìm thấy điện thoại còn hàng phù hợp với khoảng giá bạn yêu cầu.",
                    List.of(),
                    "PRICE_FILTER"
            );
        }

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
                    List<SanPhamDto> sanPhamDuocChon =
                            sanPhamGoiY(kq.maSanPham(), ungVien);

                    if (sanPhamDuocChon.isEmpty() && !ungVien.isEmpty()) {
                        sanPhamDuocChon = ungVien.stream().limit(3).toList();
                    }

                    return new ChatDto.Response(
                            kq.reply(),
                            sanPhamDuocChon,
                            "GEMINI_AI"
                    );
                }
                log.warn("Gemini không trả về nội dung hợp lệ.");
            } catch (Exception e) {
                log.error("Lỗi khi gọi Gemini AI. Chuyển sang chế độ dự phòng.", e);
            }
        } else {
            log.warn("Chưa cấu hình GEMINI_API_KEY. Chuyển sang chế độ dự phòng.");
        }

        return traLoiTuDongTheoNhuCau(cauHoi, ungVien, dieuKienGia, preferences, sanPhamQuanTam, brand);
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

    private List<SanPhamDto> timSanPhamChoChatbot(String cauHoi, List<SanPhamDto> sanPhamChinhXac, DieuKienGia dieuKienGia, String brand) {
        Map<String, SanPhamDto> ketQua = new LinkedHashMap<>();
        if (sanPhamChinhXac != null) {
            sanPhamChinhXac.stream()
                    .filter(this::coHang)
                    .filter(s -> brand == null || (s.tenThuongHieu() != null && s.tenThuongHieu().equalsIgnoreCase(brand)))
                    .filter(s -> !dieuKienGia.active() || dieuKienGia.hopLe(s.giaBan()))
                    .forEach(s -> ketQua.putIfAbsent(s.maSanPham(), s));
        }

        try {
            List<SemanticSearchResultDto> semanticResults = semanticSearchService.search(cauHoi, 8);
            if (semanticResults != null) {
                for (SemanticSearchResultDto result : semanticResults) {
                    if (result != null && result.sanPham() != null && coHang(result.sanPham())) {
                        if (brand == null || (result.sanPham().tenThuongHieu() != null && result.sanPham().tenThuongHieu().equalsIgnoreCase(brand))) {
                            if (!dieuKienGia.active() || dieuKienGia.hopLe(result.sanPham().giaBan())) {
                                ketQua.putIfAbsent(result.sanPham().maSanPham(), result.sanPham());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Lỗi Semantic Search khi tìm ứng viên cho chatbot.", e);
        }

        if (ketQua.size() < 3) {
            List<SanPhamDto> dbCandidates = timSanPhamTheoNhuCau(cauHoi, dieuKienGia, brand);
            for (SanPhamDto sp : dbCandidates) {
                if (coHang(sp) && (!dieuKienGia.active() || dieuKienGia.hopLe(sp.giaBan()))) {
                    ketQua.putIfAbsent(sp.maSanPham(), sp);
                }
                if (ketQua.size() >= 8) break;
            }
        }

        return ketQua.values().stream().limit(8).toList();
    }

    private boolean laGiaBaoNhieuCungDuoc(String cauHoi) {
        String k = chuanHoaVanBan(cauHoi);
        return k.contains("gia bao nhieu cung duoc")
                || k.contains("bao nhieu tien cung duoc")
                || k.contains("gia nao cung duoc")
                || k.contains("khong quan trong gia")
                || k.contains("khong gioi han gia")
                || k.contains("thoai mai gia")
                || k.contains("bat ky gia nao");
    }

    private List<SanPhamDto> timSanPhamTheoNhuCau(String cauHoi, DieuKienGia dieuKienGia, String brand) {
        String lower = cauHoi.toLowerCase(Locale.ROOT);
        boolean laCamera = lower.contains("chụp") || lower.contains("ảnh") || lower.contains("camera") || lower.contains("selfie") || lower.contains("quay video") || lower.contains("quay phim");
        boolean laGaming = lower.contains("game") || lower.contains("gaming") || lower.contains("cấu hình") || lower.contains("hiệu năng") || lower.contains("chip") || lower.contains("mượt");
        boolean laPin = lower.contains("pin") || lower.contains("pin trâu") || lower.contains("pin khủng") || lower.contains("pin lâu") || lower.contains("dung lượng pin");
        boolean laGiaTot = lower.contains("giá tốt") || lower.contains("giá rẻ") || lower.contains("ngon bổ rẻ") || lower.contains("tiết kiệm") || lower.contains("hợp lý");

        if (brand == null) {
            brand = detectBrand(lower);
        }
        final String targetBrand = brand;

        List<SanPhamDto> tatCa = sanPhamService.list(null, null, null, null, "ban_chay", false)
                .stream()
                .filter(this::coHang)
                .filter(s -> targetBrand == null || (s.tenThuongHieu() != null && s.tenThuongHieu().equalsIgnoreCase(targetBrand)))
                .filter(s -> !dieuKienGia.active() || dieuKienGia.hopLe(s.giaBan()))
                .toList();

        if (tatCa.isEmpty()) return List.of();

        Comparator<SanPhamDto> comparator;
        if (laCamera && laGiaTot) {
            comparator = Comparator.comparingDouble(this::diemCameraGiaTot).reversed()
                    .thenComparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder()));
        } else if (laCamera) {
            comparator = Comparator.comparingInt(this::diemCamera).reversed()
                    .thenComparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder()));
        } else if (laGaming) {
            comparator = Comparator.comparingInt(this::diemGaming).reversed()
                    .thenComparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder()));
        } else if (laPin) {
            comparator = Comparator.comparingInt(this::diemPin).reversed()
                    .thenComparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder()));
        } else if (laGiaTot) {
            comparator = Comparator.comparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(SanPhamDto::giaBan);
        } else {
            comparator = Comparator.comparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(SanPhamDto::soLuongTon, Comparator.reverseOrder());
        }

        return tatCa.stream().sorted(comparator).limit(8).toList();
    }

    private double diemCameraGiaTot(SanPhamDto s) {
        int cam = diemCamera(s);
        BigDecimal gia = s.giaBan();
        double valueBonus = 0.0;
        if (gia != null) {
            long giaVnd = gia.longValue();
            if (giaVnd <= 8_000_000L) {
                valueBonus = 40.0;
            } else if (giaVnd <= 12_000_000L) {
                valueBonus = 25.0;
            } else if (giaVnd <= 18_000_000L) {
                valueBonus = 10.0;
            }
        }
        double ratingBonus = (s.diemDanhGiaTb() != null && s.diemDanhGiaTb().compareTo(BigDecimal.valueOf(4.0)) >= 0) ? 10.0 : 0.0;
        return cam + valueBonus + ratingBonus;
    }

    private boolean laCauHoiPhiShip(String cauHoi) {
        String k = cauHoi.toLowerCase(Locale.ROOT);
        return k.contains("ship") || k.contains("giao hang") || k.contains("giao hàng")
                || k.contains("van chuyen") || k.contains("vận chuyển");
    }

    private boolean laCauHoiVoucher(String cauHoi) {
        String k = cauHoi.toLowerCase(Locale.ROOT);
        return k.contains("khuyen mai") || k.contains("khuyến mãi") || k.contains("voucher")
                || k.contains("giam gia") || k.contains("giảm giá") || k.contains("ma giam") || k.contains("mã giảm");
    }

    private int diemCamera(SanPhamDto s) {
        if (s.thongSo() == null) return 0;
        int score = 0;
        String camSau = s.thongSo().cameraSau();
        if (camSau != null) {
            Matcher m = Pattern.compile("(\\d+)\\s*mp", Pattern.CASE_INSENSITIVE).matcher(camSau);
            if (m.find()) {
                score += Integer.parseInt(m.group(1));
            }
            if (camSau.contains("+")) {
                score += 20;
            }
        }
        if (s.thongSo().cameraTruoc() != null) {
            score += s.thongSo().cameraTruoc();
        }
        return score;
    }

    private int diemGaming(SanPhamDto s) {
        if (s.thongSo() == null) return 0;
        int score = 0;
        String chip = s.thongSo().chip() != null ? s.thongSo().chip().toLowerCase(Locale.ROOT) : "";
        if (chip.contains("snapdragon 8") || chip.contains("a17") || chip.contains("a18") || chip.contains("dimensity 9") || chip.contains("dimensity 8300")) {
            score += 100;
        } else if (chip.contains("snapdragon") || chip.contains("dimensity") || chip.contains("apple")) {
            score += 50;
        }
        if (s.thongSo().ram() != null) {
            score += s.thongSo().ram() * 5;
        }
        if (s.thongSo().tanSoQuet() != null && s.thongSo().tanSoQuet() >= 120) {
            score += 30;
        }
        return score;
    }

    private int diemPin(SanPhamDto s) {
        if (s.thongSo() == null) return 0;
        int pin = s.thongSo().pin() != null ? s.thongSo().pin() : 0;
        int sac = s.thongSo().congSuatSac() != null ? s.thongSo().congSuatSac() : 0;
        return pin + (sac * 10);
    }

    private String detectBrand(String lower) {
        if (lower.contains("iphone") || lower.contains("apple")) return "Apple";
        if (lower.contains("samsung") || lower.contains("galaxy")) return "Samsung";
        if (lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco")) return "Xiaomi";
        if (lower.contains("oppo")) return "OPPO";
        if (lower.contains("vivo")) return "Vivo";
        return null;
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

    private boolean laCauHoiChaoHoi(String cauHoi) {
        String k = chuanHoaVanBan(cauHoi);

        return k.matches(
                "^(xin chao|chao|hello|hi|hey)( ban)?$"
        )
                || k.matches(
                "^(xin chao|chao|hello|hi|hey).*" +
                        "(ban la ai|ban lam gi|gioi thieu).*"
        )
                || k.matches(
                "^(ban la ai|ban lam gi|gioi thieu ve ban).*"
        );
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
        if (laGiaBaoNhieuCungDuoc(cauHoi)) {
            return new DieuKienGia(null, false, null, false, false);
        }

        String k = cauHoi.toLowerCase(Locale.ROOT).replace(",", ".");
        Matcher rangeMatcher = RANGE.matcher(k);

        if (rangeMatcher.find()) {
            BigDecimal min = doiSangTien(rangeMatcher.group(1));
            BigDecimal max = doiSangTien(rangeMatcher.group(2));
            if (min.compareTo(max) > 0) {
                BigDecimal temp = min;
                min = max;
                max = temp;
            }
            return new DieuKienGia(min, true, max, true, true);
        }

        Matcher matcher = TRIEU.matcher(k);
        if (!matcher.find()) return new DieuKienGia(null, false, null, false, false);

        BigDecimal gia = doiSangTien(matcher.group(1));
        if (k.contains("trên") || k.contains("hơn") || k.contains("lớn hơn") || k.contains("cao hơn"))
            return new DieuKienGia(gia, false, null, false, true);
        if (k.contains("từ") || k.contains("ít nhất") || k.contains("tối thiểu") || k.contains("trở lên"))
            return new DieuKienGia(gia, true, null, false, true);
        if (k.contains("dưới") || k.contains("thấp hơn") || k.contains("nhỏ hơn") || k.contains("ít hơn"))
            return new DieuKienGia(null, false, gia, false, true);
        if (k.contains("không quá") || k.contains("tối đa") || k.contains("không vượt quá"))
            return new DieuKienGia(null, false, gia, true, true);
        if (k.contains("khoảng") || k.contains("tầm") || k.contains("quanh")) {
            BigDecimal saiSo = gia.multiply(new BigDecimal("0.1"));
            return new DieuKienGia(gia.subtract(saiSo), true, gia.add(saiSo), true, true);
        }
        return new DieuKienGia(null, false, gia, true, true);
    }

    private BigDecimal doiSangTien(String gia) {
        return new BigDecimal(gia).multiply(BigDecimal.valueOf(1_000_000));
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

    private ChatDto.Response traLoiTuDongTheoNhuCau(
            String cauHoi,
            List<SanPhamDto> ungVien,
            DieuKienGia dieuKienGia,
            TrackingService.Preferences preferences,
            List<SanPhamDto> sanPhamQuanTam,
            String brand
    ) {
        String k = cauHoi.toLowerCase(Locale.ROOT);
        if (k.contains("ship") || k.contains("giao hàng") || k.contains("vận chuyển")) {
            return new ChatDto.Response("Phí vận chuyển của PhoneNest:\n" + phiShipText() + "\nPhí được tính theo tỉnh/thành của địa chỉ nhận hàng và hiển thị rõ ở bước thanh toán.", List.of(), "KEYWORD_FALLBACK");
        }
        if (k.contains("khuyến mãi") || k.contains("voucher") || k.contains("giảm giá") || k.contains("mã giảm")) {
            return new ChatDto.Response("Hiện cửa hàng có các voucher:\n" + voucherText() + "\nVoucher tốt nhất sẽ được chọn sẵn ở bước thanh toán.", List.of(), "KEYWORD_FALLBACK");
        }

        Map<String, SanPhamDto> candidates = new LinkedHashMap<>();
        if (ungVien != null) {
            ungVien.stream().filter(this::coHang).forEach(s -> candidates.put(s.maSanPham(), s));
        }

        if (candidates.size() < 3) {
            List<SanPhamDto> dbList = timSanPhamTheoNhuCau(cauHoi, dieuKienGia, brand);
            for (SanPhamDto s : dbList) {
                if (coHang(s)) {
                    candidates.putIfAbsent(s.maSanPham(), s);
                }
            }
        }

        if (sanPhamQuanTam != null) {
            sanPhamQuanTam.stream().filter(this::coHang)
                    .filter(s -> brand == null || (s.tenThuongHieu() != null && s.tenThuongHieu().equalsIgnoreCase(brand)))
                    .filter(s -> !dieuKienGia.active() || dieuKienGia.hopLe(s.giaBan()))
                    .forEach(s -> candidates.putIfAbsent(s.maSanPham(), s));
        }

        List<SanPhamDto> danhSach;
        if (preferences != null && (!preferences.productIds().isEmpty() || !preferences.searchTerms().isEmpty())) {
            danhSach = candidates.values().stream()
                    .sorted(Comparator.comparingInt((SanPhamDto s) -> diemQuanTam(s, preferences)).reversed()
                            .thenComparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(3).toList();
        } else {
            danhSach = candidates.values().stream().limit(3).toList();
        }

        if (danhSach.isEmpty()) {
            if (dieuKienGia.active()) {
                return new ChatDto.Response(
                        "PhoneNest chưa tìm thấy điện thoại còn hàng phù hợp với khoảng giá bạn yêu cầu.",
                        List.of(),
                        "PRICE_FILTER"
                );
            }
            if (!coSanPhamKhaDung()) {
                return new ChatDto.Response(
                        "Hiện tại cửa hàng PhoneNest đang tạm hết hàng các dòng điện thoại. Bạn vui lòng quay lại sau nhé!",
                        List.of(),
                        "OUT_OF_STOCK"
                );
            }
            return new ChatDto.Response(
                    "PhoneNest chưa tìm thấy sản phẩm phù hợp hoàn toàn với yêu cầu của bạn. Bạn vui lòng thử tìm theo thương hiệu hoặc khoảng giá khác nhé!",
                    List.of(),
                    "KEYWORD_FALLBACK"
            );
        }

        boolean laCamera = k.contains("chụp") || k.contains("ảnh") || k.contains("camera") || k.contains("selfie");
        boolean laGaming = k.contains("game") || k.contains("gaming") || k.contains("cấu hình") || k.contains("hiệu năng") || k.contains("chip");
        boolean laPin = k.contains("pin");
        boolean laGiaBaoNhieu = laGiaBaoNhieuCungDuoc(cauHoi);

        StringBuilder reply = new StringBuilder();
        if (dieuKienGia.active()) {
            if (dieuKienGia.min() != null && dieuKienGia.max() != null) {
                reply.append("Với ngân sách từ ").append(tien(dieuKienGia.min()))
                        .append(" đến ").append(tien(dieuKienGia.max()))
                        .append(", PhoneNest gợi ý cho bạn các mẫu máy sau:\n");
            } else if (dieuKienGia.max() != null) {
                reply.append("Với ngân sách dưới ").append(tien(dieuKienGia.max()))
                        .append(", PhoneNest gợi ý cho bạn các mẫu máy sau:\n");
            } else {
                reply.append("Với ngân sách từ ").append(tien(dieuKienGia.min()))
                        .append(", PhoneNest gợi ý cho bạn các mẫu máy sau:\n");
            }
        } else if (laGiaBaoNhieu) {
            reply.append("Không giới hạn mức giá, PhoneNest gợi ý cho bạn các mẫu điện thoại nổi bật nhất theo nhu cầu:\n");
        } else if (laCamera) {
            reply.append("Dựa trên nhu cầu chụp ảnh đẹp, PhoneNest gợi ý cho bạn các mẫu điện thoại có hệ thống camera ấn tượng:\n");
        } else if (laGaming) {
            reply.append("Dựa trên nhu cầu chơi game và hiệu năng cao, PhoneNest gợi ý cho bạn các mẫu điện thoại mạnh mẽ:\n");
        } else if (laPin) {
            reply.append("Dựa trên nhu cầu pin trâu, PhoneNest gợi ý cho bạn các mẫu điện thoại có thời lượng pin ấn tượng:\n");
        } else {
            reply.append("Dựa trên nhu cầu của bạn, PhoneNest gợi ý các mẫu máy phù hợp đang có sẵn tại cửa hàng:\n");
        }

        for (SanPhamDto s : danhSach) {
            reply.append("• ").append(s.tenMay())
                    .append(" (").append(tien(s.giaBan())).append("): ")
                    .append(trichXuatDiemNoiBat(s, laCamera, laGaming, laPin))
                    .append("\n");
        }
        reply.append("\nBạn có thể bấm vào sản phẩm bên dưới để xem chi tiết hoặc hỏi thêm mình nhé!");

        return new ChatDto.Response(reply.toString().trim(), danhSach, "KEYWORD_FALLBACK");
    }

    private String trichXuatDiemNoiBat(SanPhamDto s, boolean laCamera, boolean laGaming, boolean laPin) {
        if (s == null || s.thongSo() == null) {
            return s != null && s.moTa() != null && !s.moTa().isBlank() ? s.moTa() : "Sản phẩm chính hãng với hiệu năng ổn định.";
        }
        var ts = s.thongSo();
        List<String> highlights = new ArrayList<>();

        if (laCamera) {
            if (ts.cameraSau() != null && !ts.cameraSau().isBlank()) {
                highlights.add("Camera sau " + ts.cameraSau());
            }
            if (ts.cameraTruoc() != null) {
                highlights.add("Camera selfie " + ts.cameraTruoc() + "MP");
            }
        }

        if (laGaming) {
            if (ts.chip() != null && !ts.chip().isBlank()) {
                highlights.add("Chip " + ts.chip());
            }
            if (ts.ram() != null) {
                highlights.add("RAM " + ts.ram() + "GB");
            }
            if (ts.tanSoQuet() != null && ts.tanSoQuet() >= 90) {
                highlights.add("Màn hình mượt " + ts.tanSoQuet() + "Hz");
            }
        }

        if (laPin) {
            if (ts.pin() != null) {
                highlights.add("Pin khủng " + ts.pin() + " mAh");
            }
            if (ts.congSuatSac() != null && ts.congSuatSac() >= 30) {
                highlights.add("Sạc nhanh " + ts.congSuatSac() + "W");
            }
        }

        if (highlights.isEmpty()) {
            if (ts.chip() != null && !ts.chip().isBlank()) {
                highlights.add("Chip " + ts.chip());
            }
            if (ts.cameraSau() != null && !ts.cameraSau().isBlank()) {
                highlights.add("Camera " + ts.cameraSau());
            }
            if (ts.pin() != null) {
                highlights.add("Pin " + ts.pin() + " mAh");
            }
            if (ts.ram() != null) {
                highlights.add(ts.ram() + "GB RAM");
            }
        }

        if (highlights.isEmpty()) {
            return ts.tomTat() != null && !ts.tomTat().isBlank() ? ts.tomTat() : "Sản phẩm chính hãng nổi bật.";
        }

        return String.join(", ", highlights) + ".";
    }

    private boolean coSanPhamKhaDung() {
        try {
            List<SanPhamDto> ds = sanPhamService.list(null, null, null, null, null, false);
            return ds != null && ds.stream().anyMatch(this::coHang);
        } catch (Exception e) {
            return false;
        }
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
        if (preferences.searchTerms().stream().anyMatch(term -> khop(sanPham, term.toLowerCase(Locale.ROOT))))
            score += 5;
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