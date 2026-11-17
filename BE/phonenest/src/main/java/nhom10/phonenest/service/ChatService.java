package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.*;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.service.ai.PhoneNestProductTools;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final Pattern TRIEU = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(triệu|tr)\\b");
    private static final Pattern RANGE = Pattern.compile("(?:từ\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:(?:triệu|tr)\\s*)?(?:đến|tới|-)\\s*(\\d+(?:[.,]\\d+)?)\\s*(triệu|tr)\\b");

    private static final Set<String> LOI_CHAO = Set.of("xin chao", "chao", "chao shop", "chao ban", "chao phonenest", "hello", "hi", "alo", "shop oi");
    private static final Set<String> YEU_CAU_TIM_KHAC = Set.of(
            "co lua chon khac khong", "co lua chon nao khac", "con lua chon nao khac",
            "co may nao khac", "con may nao khac", "goi y them", "tim them",
            "cho xem them", "san pham khac", "mau khac", "con mau nao nua",
            "co mau nao nua", "con nua khong"
    );

    private record DieuKienGia(BigDecimal min, boolean minInclusive, BigDecimal max, boolean maxInclusive, boolean active) {
        public boolean hopLe(BigDecimal gia) {
            if (gia == null) return false;
            if (min != null && (minInclusive ? gia.compareTo(min) < 0 : gia.compareTo(min) <= 0)) return false;
            if (max != null && (maxInclusive ? gia.compareTo(max) > 0 : gia.compareTo(max) >= 0)) return false;
            return true;
        }
    }

    // 1. SYSTEM PROMPT
    private static final String PROMPT = """
            Bạn là trợ lý tư vấn bán hàng chuyên nghiệp của PhoneNest.

            VAI TRÒ:
            - Hiểu câu hỏi tự nhiên của khách hàng, phân tích nhu cầu và ngữ cảnh hội thoại.
            - Tư vấn thân thiện, tự nhiên, dễ hiểu; không trả lời máy móc hoặc chỉ liệt kê tên và giá.

            NGÔN NGỮ:
            - Luôn trả lời bằng tiếng Việt, đúng trọng tâm, không lặp lại thông tin thừa.
            - Câu hỏi đơn giản: trả lời ngắn gọn. Câu hỏi tư vấn: giải thích rõ lý do lựa chọn.

            NGUYÊN TẮC DỮ LIỆU:
            - Chỉ sử dụng dữ liệu thực tế từ backend PhoneNest.
            - Không tự tạo sản phẩm/mã sản phẩm; không tự bịa hoặc suy luận giá, cấu hình, camera, pin, RAM, bộ nhớ, màu sắc, phiên bản hay tồn kho.
            - Không dùng kiến thức bên ngoài thay thế dữ liệu sản phẩm trong database.

            QUY TẮC GỌI CÔNG CỤ:
            1. Tìm/tư vấn điện thoại: Bắt buộc gọi searchProducts, truyền nhu cầu tự nhiên (có thể kết hợp nhiều tiêu chí).
            2. Xem chi tiết sản phẩm: Gọi getProductDetails với mã sản phẩm thực tế từ backend.
            3. Hỏi về phiên bản: Gọi getProductVariants, chỉ dùng màu sắc, RAM, bộ nhớ, giá và tồn kho từ backend.
            4. Có thể gọi nhiều công cụ nếu cần; không tự suy diễn khi công cụ không tìm thấy.

            PHÂN TÍCH NHU CẦU:
            - Pin lâu: dung lượng pin, sạc | Camera đẹp: thông số camera thực tế | Chơi game: chip, RAM thực tế | Học tập/văn phòng: cấu hình, màn hình, giá.
            - Ngân sách: tôn trọng giới hạn giá khách đưa ra. Giữ toàn bộ điều kiện nếu khách đưa nhiều tiêu chí.

            QUY TẮC LỊCH SỬ:
            - Luôn đọc lịch sử để hiểu tham chiếu ("máy đó", "con vừa rồi", "bản đó", "có lựa chọn khác không"); không bắt khách nhắc lại điều kiện cũ.
            - Khi khách yêu cầu lựa chọn khác: giữ nguyên nhu cầu, ngân sách, thương hiệu cũ; đọc danh sách đã giới thiệu và loại bỏ chúng; gọi searchProducts kết hợp lịch sử + câu hỏi hiện tại (ưu tiên lấy tối đa 20 kết quả để có đủ lựa chọn mới).
            - Nếu khách muốn xem lại sản phẩm cũ, không áp dụng quy tắc loại trừ. Nếu thiếu ngữ cảnh, hỏi lại ngắn gọn.

            QUY TẮC SẢN PHẨM & TRẢ VỀ:
            - Chỉ đề xuất sản phẩm thực tế, đang kinh doanh, còn hàng (tối đa 3 sản phẩm, không cố ép đủ 3 nếu thiếu).
            - TUYỆT ĐỐI KHÔNG ghi mã nội bộ (VD: SP500166) trong nội dung reply.
            - Phải trả về JSON gồm đúng hai trường (không thêm nội dung ngoài JSON):
            {
                "reply": "Nội dung tư vấn dành cho khách hàng",
                "maSanPham": ["Mã sản phẩm thực tế"]
            }
            - maSanPham chỉ chứa mã sản phẩm thực sự đề xuất (tối đa 3 mã, trả về [] nếu không đề xuất).

            DỮ LIỆU CỬA HÀNG:
            Voucher đang chạy:
            {voucher}

            Phí vận chuyển:
            {phiShip}

            Sản phẩm khách gần đây quan tâm:
            {hanhVi}

            SẢN PHẨM ĐÃ ĐƯỢC GIỚI THIỆU TRONG HỘI THOẠI:
            {sanPhamDaGioiThieu}

            QUY TẮC CUỐI:
            - Sản phẩm khách từng quan tâm chỉ để tham khảo, không ưu tiên nếu không đáp ứng nhu cầu hiện tại.
            - Không đưa sản phẩm không phù hợp vào kết quả chỉ để đủ số lượng.
            """;

    // 2. DEPENDENCIES
    private final SanPhamService sanPhamService;
    private final SanPhamRepository sanPhamRepository;
    private final KhuyenMaiRepository khuyenMaiRepository;
    private final PhiVanChuyenRepository phiVanChuyenRepository;
    private final TinhThanhRepository tinhThanhRepository;
    private final TrackingService trackingService;
    private final ChatClient chatClient;
    private final PhoneNestProductTools phoneNestProductTools;
    private final UserPreferenceAggregationService userPreferenceService;
    private final ChatHistoryService chatHistoryService;
    private final ChatCartService chatCartService;


    // Lấy vai trò của tài khoản đang đăng nhập
    private boolean laCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
    }
    private boolean laAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
    // 3. XỬ LÝ CHAT
    @Transactional
    public ChatDto.Response chat(ChatDto.Request req) {
        String currentUserId = SecurityUtil.currentUserIdOrNull();
        if (currentUserId == null) throw new AppException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để sử dụng ChatBox.");
        if (req == null || req.message() == null || req.message().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Nội dung tin nhắn không được để trống.");
        }

        String cauHoi = req.message().trim();
        CuocHoiThoaiChat conversation = (req.conversationId() == null || req.conversationId().isBlank())
                ? chatHistoryService.taoCuocHoiThoai(cauHoi.substring(0, Math.min(50, cauHoi.length())))
                : chatHistoryService.layCuocHoiThoaiCuaToi(req.conversationId());

        List<Message> history = new ArrayList<>();
        for (TinNhanChat tinNhan : chatHistoryService.layLichSuGanNhat(conversation.getId())) {
            String content = chatHistoryService.layNoiDungHienThi(tinNhan);
            if ("ASSISTANT".equalsIgnoreCase(tinNhan.getRole())) {
                StringBuilder assistantContext = new StringBuilder(content == null ? "" : content);
                List<String> productIds = chatHistoryService.layMaSanPham(tinNhan);
                if (productIds != null && !productIds.isEmpty()) {
                    assistantContext.append("\n\n[NGỮ CẢNH SẢN PHẨM ĐÃ TƯ VẤN - NỘI BỘ]");
                    for (String productId : productIds) {
                        if (productId == null || productId.isBlank()) continue;
                        try {
                            SanPhamDto sp = sanPhamService.get(productId, false);
                            if (sp != null) assistantContext.append("\nTên sản phẩm: ").append(sp.tenMay()).append(" | Mã nội bộ: ").append(sp.maSanPham());
                        } catch (Exception e) {
                            log.debug("[CHAT HISTORY] Không lấy được sản phẩm {}", productId);
                        }
                    }
                }
                history.add(new AssistantMessage(assistantContext.toString()));
            } else {
                history.add(new UserMessage(content));
            }
        }

        Set<String> sanPhamDaGioiThieu = new LinkedHashSet<>(chatHistoryService.layTatCaMaSanPhamDaGioiThieu(conversation.getId()));
        chatHistoryService.luuTinNhan(conversation.getId(), "USER", cauHoi);

        ChatDto.Response res = taoPhanHoi(req, currentUserId, cauHoi, history, sanPhamDaGioiThieu);

        if (res.reply() != null && !res.reply().isBlank()) {
            List<String> productIds = res.products() == null ? List.of() : res.products().stream()
                    .filter(Objects::nonNull)
                    .map(SanPhamDto::maSanPham)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            chatHistoryService.luuTinNhan(conversation.getId(), "ASSISTANT", res.reply(), productIds);
        }

        return new ChatDto.Response(res.reply(), res.products(), res.mode(), conversation.getId());
    }

    // 4. XỬ LÝ Ý ĐỊNH VÀ GỌI AI
    private ChatDto.Response taoPhanHoi(ChatDto.Request req, String currentUserId, String cauHoi, List<Message> history, Set<String> sanPhamDaGioiThieu) {
        String cauHoiChuanHoa = chuanHoaVanBan(cauHoi);

        boolean yeuCauThemGioHang = chatCartService.laYeuCauThemGioHang(cauHoi)
                || laYeuCauThemGioHang(cauHoi)
                || chatCartService.laTiepTucChonPhienBan(cauHoi, history);
//        log.info("[CHAT CART] message='{}', addToCart={}", cauHoi, yeuCauThemGioHang);
        if (yeuCauThemGioHang) {
            if (laAdmin()) {
                return new ChatDto.Response(
                        "Chức năng thêm sản phẩm vào giỏ hàng chỉ dành cho tài khoản khách hàng. "
                                + "Bạn vẫn có thể tìm kiếm và xem thông tin sản phẩm.",
                        List.of(),
                        "ADMIN_CART_DENIED"
                );
            }

            if (laCustomer()) {
                return chatCartService.themVaoGio(cauHoi, currentUserId, history);
            }
            throw new AppException(HttpStatus.FORBIDDEN, "Bạn không có quyền sử dụng chức năng này.");
        }

        if (LOI_CHAO.contains(cauHoiChuanHoa)) {
            return new ChatDto.Response("Xin chào! PhoneNest rất vui được hỗ trợ bạn. Bạn đang muốn tìm điện thoại hay cần tư vấn sản phẩm nào ạ?", List.of(), "LOCAL_GREETING");
        }
        if (laCauHoiVanChuyen(cauHoiChuanHoa)) {
            return new ChatDto.Response("Phí vận chuyển của PhoneNest:\n" + phiShipText() + "\nPhí được tính theo tỉnh/thành của địa chỉ nhận hàng và hiển thị rõ ở bước thanh toán.", List.of(), "SHIPPING_INFO");
        }
        if (laCauHoiKhuyenMai(cauHoiChuanHoa)) {
            return new ChatDto.Response("Hiện cửa hàng có các voucher:\n" + voucherText() + "\nVoucher tốt nhất sẽ được chọn sẵn ở bước thanh toán.", List.of(), "VOUCHER_INFO");
        }

        boolean timLuaChonKhac = laYeuCauLuaChonKhac(cauHoiChuanHoa);
        DieuKienGia dieuKienGia = phanTichDieuKienGia(cauHoi);
        if (dieuKienGia.active() && !coNhuCauTuVan(cauHoiChuanHoa) && !timLuaChonKhac) {
            return traLoiTheoGia(dieuKienGia);
        }

        Set<String> maCanLoaiTru = timLuaChonKhac ? sanPhamDaGioiThieu : Set.of();
        List<SanPhamDto> sanPhamQuanTam = List.of();
        if (laCustomer()) {sanPhamQuanTam = sanPhamQuanTam(trackingService.preferences(req.visitorId()));
        }
        try {
            String systemPrompt = PROMPT;
            if (laCustomer()) {
                String userContext = userPreferenceService.buildAiPromptContext(currentUserId);
                if (userContext != null && !userContext.isBlank()) {
                    systemPrompt += "\n\n" + userContext;
                }
            }

            String hanhViText = sanPhamQuanTam.isEmpty()
                    ? "Chưa có lịch sử quan tâm phù hợp."
                    : sanPhamQuanTam.stream().map(this::sanPhamDong).collect(Collectors.joining("\n"));

            String sanPhamDaGioiThieuText = taoContextSanPhamDaGioiThieu(sanPhamDaGioiThieu);
            if (timLuaChonKhac) {
                sanPhamDaGioiThieuText += "\nKhách đang yêu cầu lựa chọn khác. Bắt buộc loại các sản phẩm trên khỏi kết quả.";
            }

            systemPrompt = systemPrompt
                    .replace("{voucher}", voucherText())
                    .replace("{phiShip}", phiShipText())
                    .replace("{hanhVi}", hanhViText)
                    .replace("{sanPhamDaGioiThieu}", sanPhamDaGioiThieuText);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.addAll(history);
            messages.add(new UserMessage(cauHoi));

            log.info("[CHAT AI] Bắt đầu gọi Gemini. Nội dung: {}", cauHoi);
            ChatDto.AiReply kq = chatClient.prompt().messages(messages).tools(phoneNestProductTools).call().entity(ChatDto.AiReply.class);

            if (kq != null && kq.reply() != null && !kq.reply().isBlank()) {
                List<SanPhamDto> products = sanPhamGoiYTuDatabase(kq.maSanPham(), maCanLoaiTru);
                if (timLuaChonKhac && kq.maSanPham() != null && !kq.maSanPham().isEmpty() && products.isEmpty()) {
                    return traLoiTheoTuKhoa(cauHoi, maCanLoaiTru);
                }
                log.info("[CHAT AI] Gemini hoàn thành. Số sản phẩm hợp lệ: {}", products.size());
                return new ChatDto.Response(anMaSanPhamNoiBo(kq.reply()), products, "GEMINI_AI");
            }
            log.warn("[CHAT AI] Gemini không trả về nội dung hợp lệ.");
        } catch (Exception e) {
            log.error("[CHAT AI] Lỗi khi gọi Gemini. Chuyển sang chế độ tìm kiếm dự phòng.", e);
        }

        return traLoiTheoTuKhoa(cauHoi, maCanLoaiTru);
    }

    // 5. NHẬN DIỆN Ý ĐỊNH
    private boolean laYeuCauThemGioHang(String cauHoi) {
        String k = chuanHoaVanBan(cauHoi);
        return List.of("them", "bo vao", "cho vao", "dat vao").stream().anyMatch(k::contains)
                && List.of("vao gio", "gio hang", "gio cua toi").stream().anyMatch(k::contains);
    }

    private boolean laCauHoiVanChuyen(String k) {
        return List.of("ship", "giao hang", "van chuyen").stream().anyMatch(k::contains);
    }

    private boolean laCauHoiKhuyenMai(String k) {
        return List.of("khuyen mai", "voucher", "giam gia", "ma giam").stream().anyMatch(k::contains);
    }

    private boolean coNhuCauTuVan(String k) {
        return List.of("game", "chup", "anh", "camera", "pin", "cau hinh", "man hinh", "hoc tap", "ngon", "tot", "muot", "tu van", "so sanh", "ram", "bo nho", "sac")
                .stream().anyMatch(k::contains);
    }

    private boolean laYeuCauLuaChonKhac(String k) {
        return YEU_CAU_TIM_KHAC.contains(k) || k.contains("lua chon khac") || k.contains("may khac")
                || k.contains("mau khac") || k.contains("goi y them") || k.contains("tim them")
                || k.contains("con may nao") || k.contains("con mau nao") || k.contains("con san pham nao");
    }

    // 6. ẨN MÃ SẢN PHẨM NỘI BỘ KHỎI CÂU TRẢ LỜI
    private String anMaSanPhamNoiBo(String reply) {
        if (reply == null || reply.isBlank()) return reply;
        return reply.replaceAll("(?i)\\bSP\\s*\\d+\\b", "")
                .replaceAll("[ \t]{2,}", " ")
                .replaceAll(" *([,;:])", "$1")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
    }

    // 7. CHUẨN HÓA VĂN BẢN
    private String chuanHoaVanBan(String value) {
        if (value == null || value.isBlank()) return "";
        String res = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("\\biphone\\b", "ip")
                .replaceAll("([a-z])([0-9])", "$1 $2")
                .replaceAll("([0-9])([a-z])", "$1 $2");
        return res.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    // 8. TẠO NGỮ CẢNH SẢN PHẨM ĐÃ GIỚI THIỆU
    private String taoContextSanPhamDaGioiThieu(Set<String> maSanPham) {
        if (maSanPham == null || maSanPham.isEmpty()) return "Chưa có sản phẩm nào được giới thiệu.";

        List<String> lines = new ArrayList<>();
        for (String ma : maSanPham) {
            if (ma == null || ma.isBlank()) continue;
            try {
                SanPhamDto sp = sanPhamService.get(ma, false);
                if (sp != null) lines.add("- Mã nội bộ: " + sp.maSanPham() + " | Tên: " + sp.tenMay());
            } catch (Exception e) {
                log.debug("[CHAT HISTORY] Không lấy được sản phẩm {}", ma);
            }
        }
        return lines.isEmpty() ? "Có lịch sử sản phẩm nhưng hiện không lấy được thông tin chi tiết." : String.join("\n", lines);
    }

    // 9. XÁC MINH SẢN PHẨM TỪ DATABASE
    private List<SanPhamDto> sanPhamGoiYTuDatabase(List<String> maSanPham, Set<String> maCanLoaiTru) {
        if (maSanPham == null || maSanPham.isEmpty()) return List.of();

        Set<String> exclude = maCanLoaiTru == null ? Set.of() : maCanLoaiTru.stream()
                .filter(Objects::nonNull)
                .map(ma -> ma.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Map<String, SanPhamDto> ketQua = new LinkedHashMap<>();
        for (String ma : maSanPham) {
            if (ma == null || ma.isBlank()) continue;
            String maChuan = ma.trim();
            if (exclude.contains(maChuan.toUpperCase(Locale.ROOT)) || ketQua.containsKey(maChuan)) continue;

            try {
                SanPhamDto sanPham = sanPhamService.get(maChuan, false);
                if (coHang(sanPham)) ketQua.put(maChuan, sanPham);
            } catch (Exception e) {
                log.warn("[CHAT AI] Không thể xác minh sản phẩm {}: {}", maChuan, e.getMessage());
            }
            if (ketQua.size() >= 3) break;
        }
        return new ArrayList<>(ketQua.values());
    }

    // 10. PHÂN TÍCH ĐIỀU KIỆN GIÁ
    private DieuKienGia phanTichDieuKienGia(String cauHoi) {
        String k = cauHoi.toLowerCase(Locale.ROOT).replace(",", ".");
        Matcher rm = RANGE.matcher(k);
        if (rm.find()) {
            BigDecimal min = doiSangTien(rm.group(1));
            BigDecimal max = doiSangTien(rm.group(2));
            return min.compareTo(max) > 0
                    ? new DieuKienGia(max, true, min, true, true)
                    : new DieuKienGia(min, true, max, true, true);
        }

        Matcher m = TRIEU.matcher(k);
        if (!m.find()) return new DieuKienGia(null, false, null, false, false);

        BigDecimal gia = doiSangTien(m.group(1));
        if (List.of("trên", "hơn", "lớn hơn", "cao hơn").stream().anyMatch(k::contains)) return new DieuKienGia(gia, false, null, false, true);
        if (List.of("từ", "ít nhất", "tối thiểu", "trở lên").stream().anyMatch(k::contains)) return new DieuKienGia(gia, true, null, false, true);
        if (List.of("dưới", "thấp hơn", "nhỏ hơn", "ít hơn").stream().anyMatch(k::contains)) return new DieuKienGia(null, false, gia, false, true);
        if (List.of("không quá", "tối đa", "không vượt quá").stream().anyMatch(k::contains)) return new DieuKienGia(null, false, gia, true, true);
        if (List.of("khoảng", "tầm", "quanh").stream().anyMatch(k::contains)) {
            BigDecimal saiSo = gia.multiply(new BigDecimal("0.1"));
            return new DieuKienGia(gia.subtract(saiSo), true, gia.add(saiSo), true, true);
        }
        return new DieuKienGia(null, false, gia, true, true);
    }

    private BigDecimal doiSangTien(String gia) {
        return new BigDecimal(gia).multiply(BigDecimal.valueOf(1_000_000));
    }

    // 11. TƯ VẤN THEO NGÂN SÁCH
    private List<SanPhamDto> chonNamSanPhamPhanBoTheoGia(List<SanPhamDto> dsSanPham) {
        int n = dsSanPham.size();
        if (n <= 5) return dsSanPham;
        List<SanPhamDto> ketQua = new ArrayList<>();
        for (int i = 0; i < 5; i++) ketQua.add(dsSanPham.get((int) Math.round(i * (n - 1) / 4.0)));
        return ketQua;
    }

    private ChatDto.Response traLoiTheoGia(DieuKienGia dk) {
        List<SanPhamDto> dsSanPham = sanPhamService.list(null, null, null, null, null, false).stream()
                .filter(this::coHang)
                .filter(s -> s.giaBan() != null && dk.hopLe(s.giaBan()))
                .sorted(Comparator.comparing(SanPhamDto::giaBan))
                .toList();

        if (dsSanPham.isEmpty()) {
            return new ChatDto.Response("PhoneNest hiện chưa tìm thấy điện thoại đang kinh doanh và còn hàng phù hợp với mức giá bạn yêu cầu. Bạn có thể thử một mức giá khác nhé!", List.of(), "PRICE_FILTER");
        }

        List<SanPhamDto> dsDeXuat = chonNamSanPhamPhanBoTheoGia(dsSanPham);
        String reply = "Mình tìm được " + dsSanPham.size() + " điện thoại còn hàng phù hợp với ngân sách của bạn. Dưới đây là "
                + dsDeXuat.size() + " lựa chọn có mức giá trải đều để bạn dễ so sánh:\n"
                + dsDeXuat.stream().map(s -> "• " + s.tenMay() + " - " + tien(s.giaBan())).collect(Collectors.joining("\n"));
        return new ChatDto.Response(reply, dsDeXuat, "PRICE_FILTER");
    }

    // 12. FALLBACK BẰNG SEMANTIC SEARCH
    private ChatDto.Response traLoiTheoTuKhoa(String cauHoi, Set<String> maCanLoaiTru) {
        try {
            List<SemanticSearchResultDto> searchResults = phoneNestProductTools.searchProducts(cauHoi, 15);
            if (searchResults == null || searchResults.isEmpty()) {
                return new ChatDto.Response("PhoneNest chưa tìm thấy sản phẩm phù hợp với yêu cầu của bạn. Bạn có thể mô tả thêm nhu cầu hoặc ngân sách để mình tìm chính xác hơn nhé!", List.of(), "SEMANTIC_FALLBACK");
            }

            Set<String> exclude = maCanLoaiTru == null ? Set.of() : maCanLoaiTru.stream()
                    .filter(Objects::nonNull)
                    .map(ma -> ma.trim().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            DieuKienGia dieuKienGia = phanTichDieuKienGia(cauHoi);
            Map<String, SanPhamDto> products = new LinkedHashMap<>();

            for (SemanticSearchResultDto result : searchResults) {
                if (result == null || result.sanPham() == null) continue;
                SanPhamDto sp = result.sanPham();
                if (!coHang(sp) || exclude.contains(sp.maSanPham().trim().toUpperCase(Locale.ROOT))) continue;
                if (dieuKienGia.active() && !dieuKienGia.hopLe(sp.giaBan())) continue;

                products.putIfAbsent(sp.maSanPham(), sp);
                if (products.size() >= 3) break;
            }

            List<SanPhamDto> ketQua = new ArrayList<>(products.values());
            if (ketQua.isEmpty()) {
                return new ChatDto.Response("Mình chưa tìm thấy lựa chọn mới phù hợp với yêu cầu hiện tại. Bạn có muốn thay đổi ngân sách hoặc tiêu chí tìm kiếm không?", List.of(), "SEMANTIC_FALLBACK");
            }

            String reply = "Mình tìm được một số điện thoại phù hợp với nhu cầu của bạn:\n"
                    + ketQua.stream().map(s -> "• " + s.tenMay() + " - " + tien(s.giaBan())).collect(Collectors.joining("\n"));
            return new ChatDto.Response(reply, ketQua, "SEMANTIC_FALLBACK");
        } catch (Exception e) {
            log.error("[CHAT FALLBACK] Lỗi Semantic Search", e);
            return new ChatDto.Response("Hiện tại mình chưa thể tìm kiếm sản phẩm. Bạn vui lòng thử lại sau nhé!", List.of(), "SEMANTIC_FALLBACK");
        }
    }

    // 13. TRACKING SẢN PHẨM KHÁCH QUAN TÂM
    private List<SanPhamDto> sanPhamQuanTam(TrackingService.Preferences preferences) {
        if (preferences == null) return List.of();
        Map<String, SanPhamDto> products = new LinkedHashMap<>();

        preferences.productIds().stream()
                .map(sanPhamRepository::findById)
                .flatMap(Optional::stream)
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

    // 14. KIỂM TRA SẢN PHẨM CÒN HÀNG
    private boolean coHang(SanPhamDto sp) {
        return sp != null && sp.trangThai() == TrangThaiSanPham.DANG_KINH_DOANH && sp.soLuongTon() > 0;
    }

    // 15. TẠO DỮ LIỆU SẢN PHẨM CHO AI
    private String sanPhamDong(SanPhamDto sp) {
        String ts = (sp.thongSo() != null && sp.thongSo().tomTat() != null) ? sp.thongSo().tomTat() : "Chưa có thông tin thông số";
        return "%s | %s | %s | %s VND | %s".formatted(
                sp.maSanPham(), sp.tenMay(), sp.tenThuongHieu(),
                sp.giaBan() == null ? "Chưa có giá" : sp.giaBan().toBigInteger(), ts
        );
    }

    // 16. VOUCHER
    private String voucherText() {
        LocalDate hom = LocalDate.now();
        String ds = khuyenMaiRepository.findAll().stream()
                .filter(k -> !hom.isBefore(k.getNgayBatDau()) && !hom.isAfter(k.getNgayKetThuc()))
                .map(this::moTaVoucher)
                .collect(Collectors.joining("\n"));
        return ds.isEmpty() ? "Hiện chưa có voucher." : ds;
    }

    private String moTaVoucher(KhuyenMai k) {
        String loai = k.getLoai().name().equals("VAN_CHUYEN") ? "phí vận chuyển" : "đơn hàng";
        String toiDa = k.getSoTienGiamToiDa() == null ? "" : ", tối đa " + tien(k.getSoTienGiamToiDa());
        String toiThieu = (k.getDonToiThieu() != null && k.getDonToiThieu().signum() > 0) ? ", đơn từ " + tien(k.getDonToiThieu()) : "";
        return "- %s (%s): giảm %s%% %s%s%s".formatted(
                k.getMaKm(), k.getTenKm(), k.getPhanTramGiam().stripTrailingZeros().toPlainString(), loai, toiDa, toiThieu
        );
    }

    // 17. PHÍ VẬN CHUYỂN
    private String phiShipText() {
        String ds = StreamSupport.stream(phiVanChuyenRepository.findAll().spliterator(), false)
                .sorted(Comparator.comparing((PhiVanChuyen p) -> p.getMaTinh() == null))
                .map(p -> "- %s: %s%s, giao %s".formatted(
                        p.getMaTinh() == null ? "Nơi khác" : tinhThanhRepository.findById(p.getMaTinh()).map(TinhThanh::getTenTinh).orElse(p.getMaTinh()),
                        tien(p.getPhiCoBan()),
                        p.getMienPhiTu() == null ? "" : " (miễn phí cho đơn từ " + tien(p.getMienPhiTu()) + ")",
                        p.getThoiGianGiao()
                ))
                .collect(Collectors.joining("\n"));
        return ds.isEmpty() ? "Chưa có thông tin phí vận chuyển." : ds;
    }

    // 18. ĐỊNH DẠNG TIỀN
    private String tien(BigDecimal n) {
        return n == null ? "Chưa có thông tin" : NumberFormat.getInstance(Locale.of("vi", "VN")).format(n) + " ₫";
    }
}