
package nhom10.phonenest.service.ai;

import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class ProductSemanticDocumentBuilder {

    // Kiểm tra phiên bản có thể bán
    public boolean phienBanCoTheBan(PhienBanSanPham p) {
        return p != null
                && p.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH
                && p.getSoLuongTon() > 0
                && p.getGiaBan() != null
                && p.getGiaBan().compareTo(BigDecimal.ZERO) > 0;
    }

    // Lấy các phiên bản thực sự còn hàng
    public List<PhienBanSanPham> getPhienBanConHang(SanPham s) {
        if (s == null || s.getPhienBans() == null) {
            return List.of();
        }

        return s.getPhienBans().stream()
                .filter(this::phienBanCoTheBan)
                .toList();
    }

    // Giá thấp nhất trong các phiên bản còn hàng
    public BigDecimal getGiaThapNhat(SanPham s) {
        return getPhienBanConHang(s).stream()
                .map(PhienBanSanPham::getGiaBan)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    // Tổng tồn kho của các phiên bản còn hàng
    public int getTongTonKho(SanPham s) {
        return getPhienBanConHang(s).stream()
                .mapToInt(PhienBanSanPham::getSoLuongTon)
                .sum();
    }

    public Document build(SanPham s) {
        if (s == null || s.getMaSanPham() == null) {
            throw new IllegalArgumentException("Sản phẩm không hợp lệ");
        }

        String ma = s.getMaSanPham();
        String tenMay = Objects.toString(s.getTenMay(), "");
        String hang = s.getThuongHieu() != null
                ? Objects.toString(s.getThuongHieu().getTenThuongHieu(), "")
                : "";

        String loai = s.getLoai() != null
                ? Objects.toString(s.getLoai().getTenLoai(), "Điện thoại")
                : "Điện thoại";

        List<PhienBanSanPham> variants = getPhienBanConHang(s);

        BigDecimal giaBan = getGiaThapNhat(s);
        int tonKho = getTongTonKho(s);

        StringBuilder text = new StringBuilder();

        text.append("Tên sản phẩm: ").append(tenMay).append("\n");
        text.append("Hãng sản xuất: ").append(hang).append("\n");
        text.append("Danh mục: ").append(loai).append("\n");

        text.append("Trạng thái sản phẩm: ")
                .append(s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH
                        ? "Đang kinh doanh" : "Ngừng kinh doanh")
                .append("\n");

        if (giaBan.compareTo(BigDecimal.ZERO) > 0) {
            text.append("Giá bán thấp nhất của phiên bản còn hàng: ")
                    .append(giaBan.toBigInteger())
                    .append(" đồng\n");
        }
        text.append("Tổng số lượng còn hàng: ")
                .append(tonKho)
                .append(" máy\n");
        // Ghi từng cấu hình thực tế, không ghép RAM và ROM riêng lẻ
        if (!variants.isEmpty()) {
            text.append("Các phiên bản đang có hàng:\n");
            for (PhienBanSanPham p : variants) {
                text.append("- RAM ")
                        .append(p.getRam()).append("GB")
                        .append(", bộ nhớ trong ")
                        .append(p.getBoNho()).append("GB")
                        .append(", màu ")
                        .append(Objects.toString(p.getMauSac(), "Chưa cập nhật"))
                        .append(", giá ")
                        .append(p.getGiaBan().toBigInteger())
                        .append(" đồng")
                        .append(", tồn kho ")
                        .append(p.getSoLuongTon())
                        .append(" máy\n");
            }
        } else {
            text.append("Hiện không có phiên bản còn hàng.\n");
        }
        if (s.getKichThuocManHinh() != null) {
            text.append("Màn hình: ")
                    .append(s.getKichThuocManHinh())
                    .append(" inch");
            if (s.getLoaiManHinh() != null && !s.getLoaiManHinh().isBlank()) {
                text.append(", ").append(s.getLoaiManHinh());
            }
            if (s.getTanSoQuet() != null) {
                text.append(", tần số quét ")
                        .append(s.getTanSoQuet()).append("Hz");
            }
            text.append("\n");
        }
        if (s.getChip() != null && !s.getChip().isBlank()) {
            text.append("Vi xử lý: ").append(s.getChip()).append("\n");
        }
        if (s.getCameraSau() != null && !s.getCameraSau().isBlank()) {
            text.append("Camera sau: ").append(s.getCameraSau()).append("\n");
        }
        if (s.getCameraTruoc() != null && !s.getCameraTruoc().isBlank()) {
            text.append("Camera trước: ").append(s.getCameraTruoc()).append("\n");
        }
        if (s.getPin() != null) {
            text.append("Dung lượng pin: ")
                    .append(s.getPin()).append(" mAh");
            if (s.getCongSuatSac() != null) {
                text.append(", công suất sạc ")
                        .append(s.getCongSuatSac()).append("W");
            }
            text.append("\n");
        }
        if (s.isHo5G()) {
            text.append("Hỗ trợ mạng 5G: Có\n");
        }
        if (s.isNfc()) {
            text.append("Hỗ trợ NFC: Có\n");
        }
        if (s.getHeDieuHanh() != null && !s.getHeDieuHanh().isBlank()) {
            text.append("Hệ điều hành: ")
                    .append(s.getHeDieuHanh()).append("\n");
        }
        if (s.getMoTa() != null && !s.getMoTa().isBlank()) {
            text.append("Mô tả chi tiết: ")
                    .append(s.getMoTa().trim()).append("\n");
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("maSanPham", ma);
        metadata.put("tenMay", tenMay);
        metadata.put("hang", hang);
        metadata.put("giaBan", giaBan.doubleValue());
        metadata.put("tonKho", tonKho);
        return new Document(ma, text.toString(), metadata);
    }
}
