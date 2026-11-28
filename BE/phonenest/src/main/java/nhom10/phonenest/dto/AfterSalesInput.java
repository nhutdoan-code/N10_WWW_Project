package nhom10.phonenest.dto;

import java.util.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.service.LuuTruAnhService;
import org.springframework.http.HttpStatus;

/** Extends existing JSON payload; details are readable by existing admin tools. */
public record AfterSalesInput(String maDonHang, String maPhienBan, Integer soLuong,
    LoaiHauMai loai, LoaiYeuCauDoiTra loaiYeuCau, String lyDo,
    String hinhAnhMinhChung, List<String> anhMinhChung, Map<String,String> chiTiet) {
    private static final Set<String> REASONS = Set.of("Máy lỗi", "Giao sai hàng", "Không đúng mô tả", "Đổi ý", "Khác");
    private static final Set<String> BANKS = Set.of("Vietcombank", "VietinBank", "BIDV", "Agribank", "Techcombank", "MB", "ACB", "VPBank", "TPBank", "Sacombank", "HDBank", "VIB", "SHB", "OCB", "MSB", "SeABank", "LPBank", "Eximbank", "Nam A Bank", "BVBank", "ABBANK", "PVcomBank", "Vietbank", "Bac A Bank", "KienlongBank", "NCB", "Viet A Bank", "UOB", "HSBC", "Standard Chartered", "Woori Bank", "Shinhan Bank", "Public Bank", "Hong Leong Bank", "CIMB", "Co-opBank", "Bao Viet Bank");
    private static final Set<String> MISMATCHES = Set.of("Sai dung lượng", "Sai màu", "Sai phiên bản", "Thiếu phụ kiện", "Khác");
    private static final Set<String> FAULTS = Set.of("Không lên nguồn", "Màn hình / cảm ứng", "Pin / sạc", "Camera / âm thanh", "Kết nối", "Lỗi khác");
    private static final Set<String> PROOF = Set.of("Máy lỗi", "Giao sai hàng", "Không đúng mô tả");
    private static String clean(String value) { return value == null ? "" : value.replaceAll("(?U)^\\s+|\\s+$", ""); }
    private static AppException bad(String message) { return new AppException(HttpStatus.BAD_REQUEST, message); }
    private String required(String key, String label, int max, Set<String> choices) {
        String value = clean(chiTiet.get(key));
        if (value == null || value.isBlank() || value.strip().length() > max || (choices != null && !choices.contains(value.strip())))
            throw bad("Vui lòng " + (choices == null ? "nhập " : "chọn ") + label + " hợp lệ");
        return value.strip();
    }
    private String validatedFaults() {
        String rawFaults = required("fault", "loại lỗi", 150, null);
        List<String> faults = Arrays.asList(rawFaults.split("; ", -1));
        if (faults.size() > FAULTS.size() || new HashSet<>(faults).size() != faults.size() || !FAULTS.containsAll(faults))
            throw bad("Vui lòng chọn ít nhất một loại lỗi hợp lệ, không trùng lặp");
        return String.join("; ", faults);
    }
    public boolean proofRequired(String type) {
        return "BAO_HANH".equals(type) || (!"KHIEU_NAI".equals(type) && (chiTiet == null || (PROOF.contains(clean(chiTiet.get("reason"))) || "Có lỗi".equals(clean(chiTiet.get("condition"))))));
    }
    public String description(String type) {
        String text = clean(lyDo);
        if (text.isBlank()) throw bad("Vui lòng mô tả vấn đề");
        if (text.length() > 1000) throw bad("Mô tả tối đa 1000 ký tự");
        if (chiTiet == null) {
            if ("BAO_HANH".equals(type)) throw bad("Vui lòng gửi tình trạng ngoại hình và loại lỗi");
            return text; // legacy payload: conservatively require proof for returns
        }
        List<String> lines = new ArrayList<>();
        if ("KHIEU_NAI".equals(type)) {
            lines.add("Chủ đề: " + required("topic", "chủ đề", 50, Set.of("Giao hàng", "Thái độ nhân viên", "Giá/khuyến mãi", "Sản phẩm", "Khác")));
        } else if ("BAO_HANH".equals(type)) {
            lines.add("Ngoại hình: " + required("appearance", "tình trạng ngoại hình", 50, Set.of("Bình thường", "Trầy xước", "Rơi vỡ", "Vào nước", "Khác")));
            lines.add("Loại lỗi: " + validatedFaults());
            // Legacy customer outcome is deliberately ignored; only the store decides.
        } else {
            String reason = required("reason", "lý do", 50, REASONS);
            lines.add("Lý do: " + reason);
            String condition = "Máy lỗi".equals(reason) ? "Có lỗi" : required("condition", "tình trạng máy", 50, Set.of("Còn nguyên seal", "Đã kích hoạt", "Có lỗi"));
            if ("Đổi ý".equals(reason) && !"Còn nguyên seal".equals(condition)) throw bad("Đổi ý yêu cầu máy còn nguyên seal");
            if (Set.of("Giao sai hàng", "Không đúng mô tả").contains(reason) && "Có lỗi".equals(condition))
                throw bad("Vui lòng chọn Còn nguyên seal hoặc Đã kích hoạt; nếu máy bị hỏng, chọn lý do Máy lỗi");
            lines.add("Tình trạng máy: " + condition);
            if ("Đổi ý".equals(reason)) {
                if (!"true".equals(chiTiet.get("sealConfirmed"))) throw bad("Vui lòng xác nhận máy chưa bóc seal, đầy đủ phụ kiện");
                lines.add("Cam kết khách hàng: Tất cả máy yêu cầu xử lý chưa bóc seal, đầy đủ phụ kiện");
            }
            if ("Không đúng mô tả".equals(reason)) lines.add("Điểm khác mô tả: " + required("mismatch", "điểm khác với mô tả", 50, MISMATCHES));
            if ("Máy lỗi".equals(reason)) {
                lines.add("Loại lỗi: " + validatedFaults());
            }
            if ("DOI_HANG".equals(type)) lines.add("Phiên bản muốn đổi: " + required("target", "sản phẩm/phiên bản muốn đổi", 150, null));
            else {
                String method = required("refund", "hình thức hoàn tiền", 50, Set.of("Phương thức thanh toán gốc", "Chuyển khoản"));
                lines.add("Hoàn tiền đề nghị: " + method);
                if (method.equals("Chuyển khoản")) {
                    lines.add("Ngân hàng: " + required("bank", "ngân hàng", 80, BANKS));
                    String account = required("account", "số tài khoản", 20, null);
                    if (!account.matches("[0-9]{6,20}")) throw bad("Số tài khoản phải gồm 6–20 chữ số");
                    lines.add("Số tài khoản: " + account);
                    String holder = java.text.Normalizer.normalize(required("holder", "chủ tài khoản", 100, null), java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replace('Đ', 'D');
                    if (!holder.matches("[A-Z][A-Z .’'\\-]{1,99}")) throw bad("Vui lòng nhập tên chủ tài khoản hợp lệ");
                    lines.add("Chủ tài khoản: " + holder);
                }
            }
        }
        lines.add("Mô tả chi tiết: " + text);
        return String.join("\n", lines);
    }
    public String images(String type, String user, LuuTruAnhService storage) {
        List<String> urls = anhMinhChung != null ? anhMinhChung : hinhAnhMinhChung == null || hinhAnhMinhChung.isBlank() ? List.of() : List.of(hinhAnhMinhChung);
        if (urls.size() > 5) throw bad("Chỉ được gửi tối đa 5 ảnh");
        if (proofRequired(type) && urls.isEmpty()) throw bad("Vui lòng tải ít nhất 1 ảnh minh chứng");
        if (new HashSet<>(urls).size() != urls.size()) throw bad("Không gửi ảnh trùng lặp");
        for (int i=0; i<urls.size(); i++) if (!storage.anhHauMaiHopLe(urls.get(i), user)) throw bad("Ảnh thứ " + (i+1) + " không hợp lệ hoặc chưa được tải lên");
        return urls.isEmpty() ? null : String.join("\n", urls);
    }
}
