package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.ChatDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.KhachHangRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCartService {

    private final SanPhamService sanPhamService;
    private final GioHangService gioHangService;
    private final KhachHangRepository khachHangRepository;

    private static final Pattern SO_LUONG = Pattern.compile("\\bthem\\s+(\\d+)\\b");
    private static final Pattern MA_SAN_PHAM = Pattern.compile("\\bsp\\s*\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAM = Pattern.compile("\\b(\\d+)\\s*gb\\s*ram\\b");
    private static final Pattern BO_NHO = Pattern.compile("\\b(\\d+)\\s*gb\\s*(rom|bo nho|luu tru)\\b");
    private static final Pattern THU_TU = Pattern.compile("\\b(?:thu|so)\\s*(\\d+)\\b");
    private static final String[] MAU_SAC = {"den", "trang", "xanh", "tim", "vang", "bac", "hong", "xam", "kem", "do", "nau"};

    private boolean laCustomerDangDangNhap() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_CUSTOMER".equals(authority.getAuthority())
                );
    }

    // 1. NHẬN DIỆN YÊU CẦU THÊM GIỎ HÀNG
    public boolean laYeuCauThemGioHang(String noiDung) {
        if (noiDung == null || noiDung.isBlank()) return false;
        String text = chuanHoa(noiDung);
        return text.contains("them") && (text.contains("gio hang") || text.contains("vao gio"));
    }

    // 2. NHẬN DIỆN CÂU TRẢ LỜI CHỌN PHIÊN BẢN
    public boolean laTiepTucChonPhienBan(String noiDung, List<Message> history) {
        if (noiDung == null || noiDung.isBlank() || history == null || history.isEmpty()) return false;

        String text = chuanHoa(noiDung);
        boolean coThongTinPhienBan = layMauSac(text) != null || RAM.matcher(text).find() || BO_NHO.matcher(text).find();
        if (!coThongTinPhienBan) return false;

        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof AssistantMessage assistantMessage && assistantMessage.getText() != null && !assistantMessage.getText().isBlank()) {
                String assistantNormalized = chuanHoa(assistantMessage.getText());
                return assistantNormalized.contains("ban chon")
                        || assistantNormalized.contains("chon mau sac")
                        || assistantNormalized.contains("nhieu phien ban")
                        || assistantNormalized.contains("ram hoac bo nho")
                        || assistantNormalized.contains("chon phien ban");
            }
        }
        return false;
    }

    // 3. THÊM SẢN PHẨM VÀO GIỎ HÀNG
    // Giao dich rieng: GioHangService tu choi (AppException) chi rollback thao tac gio hang,
    // khong lam hong giao dich luu lich su hoi thoai cua ChatService.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatDto.Response themVaoGio(String cauHoi, String maTk, List<Message> history) {
        if (!laCustomerDangDangNhap()) {
            throw new AppException(HttpStatus.FORBIDDEN, "Chức năng thêm giỏ hàng chỉ dành cho khách hàng.");
        }
        // Kiểm tra tài khoản đăng nhập.
        if (maTk == null || maTk.isBlank()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để sử dụng chức năng giỏ hàng."
            );
        }

        KhachHang khachHang = khachHangRepository.findById(maTk)
                .orElseThrow(() -> new AppException(HttpStatus.FORBIDDEN, "Tài khoản này không có thông tin khách hàng."));

        List<SanPhamDto> danhSachSanPham = sanPhamService.list(null, null, null, null, null, false);
        if (danhSachSanPham == null || danhSachSanPham.isEmpty()) {
            return response("Hiện tại cửa hàng chưa có sản phẩm.", List.of(), "CART_NOT_FOUND");
        }

        List<SanPhamDto> sanPhamTimDuoc = timSanPhamTheoMa(cauHoi, danhSachSanPham);
        if (sanPhamTimDuoc.isEmpty()) sanPhamTimDuoc = timSanPhamTheoTen(cauHoi, danhSachSanPham);
        if (sanPhamTimDuoc.isEmpty() && laThamChieuLichSu(cauHoi)) {
            sanPhamTimDuoc = timSanPhamTrongLichSu(history, danhSachSanPham);
        }

        if (sanPhamTimDuoc.size() > 1) {
            SanPhamDto sanPhamDuocChon = chonSanPhamTheoThuTu(cauHoi, sanPhamTimDuoc);
            if (sanPhamDuocChon != null) sanPhamTimDuoc = List.of(sanPhamDuocChon);
        }

        if (sanPhamTimDuoc.isEmpty()) {
            return response("Mình chưa xác định được sản phẩm bạn muốn thêm. Bạn hãy gửi tên hoặc mã sản phẩm chính xác nhé.", List.of(), "CART_NOT_FOUND");
        }

        if (sanPhamTimDuoc.size() > 1) {
            StringBuilder noiDung = new StringBuilder("Mình tìm thấy nhiều sản phẩm phù hợp. Bạn muốn thêm sản phẩm nào?\n");
            for (int i = 0; i < sanPhamTimDuoc.size(); i++) {
                noiDung.append(i + 1).append(". ").append(sanPhamTimDuoc.get(i).tenMay()).append("\n");
            }
            return response(noiDung.toString(), sanPhamTimDuoc, "CART_SELECT_PRODUCT");
        }

        SanPhamDto sanPham = sanPhamTimDuoc.get(0);
        if (sanPham.trangThai() != TrangThaiSanPham.DANG_KINH_DOANH) {
            return response("Sản phẩm " + sanPham.tenMay() + " hiện không kinh doanh.", List.of(sanPham), "CART_UNAVAILABLE");
        }

        List<SanPhamDto.PhienBan> phienBanConHang = sanPham.phienBans() == null ? List.of() : sanPham.phienBans().stream()
                .filter(Objects::nonNull)
                .filter(pb -> pb.trangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                // Chi bien the duoc phep ban (phe duyet ban hang con hieu luc); GioHangService van kiem tra lai.
                .filter(pb -> Boolean.TRUE.equals(pb.duocPhepBan()))
                .filter(pb -> pb.soLuongTon() > 0 && pb.giaBan() != null && pb.giaBan().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (phienBanConHang.isEmpty()) {
            return response("Sản phẩm " + sanPham.tenMay() + " hiện đã hết hàng.", List.of(sanPham), "CART_OUT_OF_STOCK");
        }

        String cauHoiChuanHoa = chuanHoa(cauHoi);
        String mauSac = layMauSac(cauHoiChuanHoa);
        if (mauSac != null) {
            phienBanConHang = phienBanConHang.stream().filter(pb -> chuanHoa(pb.mauSac()).contains(mauSac)).toList();
        }

        Matcher ramMatcher = RAM.matcher(cauHoiChuanHoa);
        if (ramMatcher.find()) {
            int ram = Integer.parseInt(ramMatcher.group(1));
            phienBanConHang = phienBanConHang.stream().filter(pb -> Objects.equals(pb.ram(), ram)).toList();
        }

        Matcher boNhoMatcher = BO_NHO.matcher(cauHoiChuanHoa);
        if (boNhoMatcher.find()) {
            int boNho = Integer.parseInt(boNhoMatcher.group(1));
            phienBanConHang = phienBanConHang.stream().filter(pb -> Objects.equals(pb.boNho(), boNho)).toList();
        }

        if (phienBanConHang.isEmpty()) {
            return response("Không tìm thấy phiên bản còn hàng phù hợp với màu sắc, RAM hoặc bộ nhớ bạn chọn.", List.of(sanPham), "CART_VARIANT_NOT_FOUND");
        }

        if (phienBanConHang.size() > 1) {
            StringBuilder noiDung = new StringBuilder("Sản phẩm " + sanPham.tenMay() + " có nhiều phiên bản còn hàng:\n");
            for (SanPhamDto.PhienBan pb : phienBanConHang) {
                noiDung.append("- Màu: ").append(pb.mauSac())
                        .append(" | RAM: ").append(pb.ram())
                        .append("GB | Bộ nhớ: ").append(pb.boNho())
                        .append("GB | Giá: ").append(tien(pb.giaBan())).append("\n");
            }
            noiDung.append("Bạn chọn màu sắc, RAM và bộ nhớ nào?");
            return response(noiDung.toString(), List.of(sanPham), "CART_SELECT_VARIANT");
        }

        int soLuong = laySoLuong(cauHoi);
        SanPhamDto.PhienBan phienBan = phienBanConHang.get(0);

        if (phienBan.soLuongTon() < soLuong) {
            return response("Phiên bản này chỉ còn " + phienBan.soLuongTon() + " sản phẩm trong kho.", List.of(sanPham), "CART_INSUFFICIENT_STOCK");
        }

        try {
            gioHangService.add(khachHang.getMaKh(), phienBan.maPhienBan(), soLuong);
            log.info("[CHAT CART] Added product={}, variant={}, quantity={}", sanPham.maSanPham(), phienBan.maPhienBan(), soLuong);
            return response("Đã thêm " + soLuong + " sản phẩm " + sanPham.tenMay() + " vào giỏ hàng thành công!", List.of(sanPham), "CART_ADDED");
        } catch (AppException e) {
            log.warn("[CHAT CART] Cannot add product: {}", e.getMessage());
            throw e;
        }
    }

    // 4. TÌM SẢN PHẨM THEO MÃ
    private List<SanPhamDto> timSanPhamTheoMa(String noiDung, List<SanPhamDto> danhSach) {
        Matcher matcher = MA_SAN_PHAM.matcher(chuanHoa(noiDung));
        List<String> danhSachMa = new ArrayList<>();
        while (matcher.find()) {
            String ma = matcher.group().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (!danhSachMa.contains(ma)) danhSachMa.add(ma);
        }
        if (danhSachMa.isEmpty()) return List.of();

        return danhSach.stream()
                .filter(sp -> sp.maSanPham() != null && danhSachMa.stream().anyMatch(ma -> sp.maSanPham().equalsIgnoreCase(ma)))
                .toList();
    }

    // CHỌN SẢN PHẨM THEO THỨ TỰ
    private SanPhamDto chonSanPhamTheoThuTu(String noiDung, List<SanPhamDto> danhSach) {
        if (noiDung == null || danhSach == null || danhSach.isEmpty()) return null;

        String text = chuanHoa(noiDung);
        Matcher matcher = THU_TU.matcher(text);
        int viTri = -1;

        if (matcher.find()) {
            try {
                viTri = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        } else if (text.matches(".*\\bthu nhat\\b.*")) {
            viTri = 1;
        } else if (text.matches(".*\\bthu hai\\b.*")) {
            viTri = 2;
        } else if (text.matches(".*\\bthu ba\\b.*")) {
            viTri = 3;
        }

        return (viTri >= 1 && viTri <= danhSach.size()) ? danhSach.get(viTri - 1) : null;
    }

    // 5. TÌM SẢN PHẨM THEO TÊN
    private List<SanPhamDto> timSanPhamTheoTen(String noiDung, List<SanPhamDto> danhSach) {
        String text = chuanHoaTenSanPham(noiDung);
        return danhSach.stream()
                .filter(sp -> sp.tenMay() != null && khopSanPham(text, sp.tenMay()))
                .toList();
    }

    private boolean khopSanPham(String noiDung, String tenSanPham) {
        String ten = chuanHoaTenSanPham(tenSanPham);
        if (ten.isBlank() || ten.matches("\\d+")) return false;
        String regex = "(?<![a-z0-9])" + Pattern.quote(ten) + "(?![a-z0-9+])";
        return Pattern.compile(regex).matcher(noiDung).find();
    }

    // 6. TÌM SẢN PHẨM TRONG LỊCH SỬ CHAT
    private List<SanPhamDto> timSanPhamTrongLichSu(List<Message> history, List<SanPhamDto> danhSach) {
        if (history == null || history.isEmpty()) return List.of();

        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof AssistantMessage assistantMessage && assistantMessage.getText() != null && !assistantMessage.getText().isBlank()) {
                String noiDung = assistantMessage.getText();
                List<SanPhamDto> ketQua = timSanPhamTheoMa(noiDung, danhSach);
                if (!ketQua.isEmpty()) return ketQua;

                ketQua = timSanPhamTheoTen(noiDung, danhSach);
                if (!ketQua.isEmpty()) return ketQua;
            }
        }
        return List.of();
    }

    // 7. NHẬN DIỆN THAM CHIẾU LỊCH SỬ
    private boolean laThamChieuLichSu(String noiDung) {
        String text = chuanHoa(noiDung);
        return text.contains("may nay") || text.contains("san pham nay") || text.contains("con nay")
                || text.contains("con do") || text.contains("cai nay") || text.contains("may vua tu van")
                || text.contains("san pham vua tu van") || text.contains("may do")
                || text.contains("them vao gio") || text.contains("them may nay");
    }

    // 8. LẤY SỐ LƯỢNG
    private int laySoLuong(String noiDung) {
        Matcher matcher = SO_LUONG.matcher(chuanHoa(noiDung));
        if (matcher.find()) {
            try {
                int soLuong = Integer.parseInt(matcher.group(1));
                if (soLuong > 0) return soLuong;
            } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    // 9. NHẬN DIỆN MÀU SẮC
    private String layMauSac(String noiDung) {
        for (String mau : MAU_SAC) {
            if (Pattern.compile("(?<![a-z])" + Pattern.quote(mau) + "(?![a-z])").matcher(noiDung).find()) {
                return mau;
            }
        }
        return null;
    }

    // 10. CHUẨN HÓA VĂN BẢN
    private String chuanHoa(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String chuanHoaTenSanPham(String text) {
        return chuanHoa(text).replaceAll("\\s*\\+\\s*", "+").replaceAll("\\s+", " ").trim();
    }

    private static String tien(BigDecimal n) {
        return java.text.NumberFormat.getInstance(Locale.of("vi", "VN")).format(n) + " ₫";
    }

    // 11. TẠO RESPONSE
    private ChatDto.Response response(String reply, List<SanPhamDto> products, String mode) {
        return new ChatDto.Response(reply, products, mode);
    }
}