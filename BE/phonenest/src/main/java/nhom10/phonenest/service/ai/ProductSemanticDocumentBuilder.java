package nhom10.phonenest.service.ai;

import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.service.PheDuyetBanHangPolicy;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Builds factual semantic documents from PhoneNest MariaDB entities.
 * <p>
 * Architectural rule: Contains ONLY factual specifications and descriptions
 * present in the database. NO artificial text injection (such as fake student,
 * gaming, or travel labels) to artificially bias retrieval.
 */
@Component
public class ProductSemanticDocumentBuilder {

    /**
     * Bien the co the ban qua chatbot: duoc phep ban (phe duyet con hieu luc + dang kinh doanh) va con ton.
     * Chi dung gia DUOC CONG BO (2B.7D), khong bao gio dua gia noi bo chua duyet vao vector store.
     */
    public boolean phienBanCoTheBan(PhienBanSanPham p) {
        return p != null
                && PheDuyetBanHangPolicy.duocPhepBan(p)
                && p.getSoLuongTon() > 0
                && p.getGiaBan() != null
                && p.getGiaBan().signum() > 0;
    }

    /** Cac phien ban thuc su ban duoc va con hang. */
    public List<PhienBanSanPham> getPhienBanConHang(SanPham s) {
        if (s == null || s.getPhienBans() == null) {
            return List.of();
        }
        return s.getPhienBans().stream().filter(this::phienBanCoTheBan).toList();
    }

    public Document build(SanPham s) {
        String ma = s.getMaSanPham();
        String tenMay = s.getTenMay();
        String hang = s.getThuongHieu() != null ? s.getThuongHieu().getTenThuongHieu() : "";
        String loai = s.getLoai() != null ? s.getLoai().getTenLoai() : "Điện thoại";
        // 2B.7D: chi dua gia DUOC CONG BO vao vector store / ngu canh chatbot.
        BigDecimal giaBan = PheDuyetBanHangPolicy.giaCongBo(s);
        int tonKho = s.getSoLuongTon();

        // Extract RAM & Storage variations from variants
        int maxRam = 0;
        int maxRom = 0;
        if (s.getPhienBans() != null) {
            for (PhienBanSanPham pb : s.getPhienBans()) {
                if (pb.getRam() > maxRam) maxRam = pb.getRam();
                if (pb.getBoNho() > maxRom) maxRom = pb.getBoNho();
            }
        }

        StringBuilder text = new StringBuilder();
        text.append("Tên sản phẩm: ").append(tenMay).append("\n");
        text.append("Hãng sản xuất: ").append(hang).append("\n");
        text.append("Danh mục: ").append(loai).append("\n");
        if (giaBan != null) {
            text.append("Giá bán: ").append(giaBan.toBigInteger()).append(" ₫\n");
        } else {
            text.append("Giá bán: đang cập nhật, chưa nhận đặt hàng\n");
        }
        text.append("Trạng thái kinh doanh: ").append(s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH ? "Đang kinh doanh" : "Ngừng kinh doanh").append("\n");
        text.append("Số lượng tồn kho: ").append(tonKho).append(" máy\n");

        if (s.getKichThuocManHinh() != null) {
            text.append("Màn hình: ").append(s.getKichThuocManHinh()).append(" inch");
            if (s.getLoaiManHinh() != null && !s.getLoaiManHinh().isBlank()) {
                text.append(", ").append(s.getLoaiManHinh());
            }
            if (s.getTanSoQuet() != null) {
                text.append(", tần số quét ").append(s.getTanSoQuet()).append("Hz");
            }
            text.append("\n");
        }

        if (s.getChip() != null && !s.getChip().isBlank()) {
            text.append("Vi xử lý (CPU/Chip): ").append(s.getChip()).append("\n");
        }

        if (maxRam > 0 || maxRom > 0) {
            text.append("Bộ nhớ: RAM ").append(maxRam).append("GB, bộ nhớ trong ").append(maxRom).append("GB\n");
        }

        // Liet ke tung cau hinh thuc te (QuocLuu): giup AI tra loi dung mau sac / RAM / bo nho dang ban.
        List<PhienBanSanPham> conHang = getPhienBanConHang(s);
        if (!conHang.isEmpty()) {
            text.append("Các phiên bản đang có hàng:\n");
            for (PhienBanSanPham p : conHang) {
                text.append("- RAM ").append(p.getRam()).append("GB, bộ nhớ trong ").append(p.getBoNho()).append("GB")
                        .append(", màu ").append(Objects.toString(p.getMauSac(), "Chưa cập nhật"))
                        .append(", giá ").append(PheDuyetBanHangPolicy.giaCongBo(p).toBigInteger()).append(" ₫\n");
            }
        }

        if (s.getCameraSau() != null && !s.getCameraSau().isBlank()) {
            text.append("Camera sau: ").append(s.getCameraSau()).append("\n");
        }

        if (s.getCameraTruoc() != null && !s.getCameraTruoc().isBlank()) {
            text.append("Camera trước (selfie): ").append(s.getCameraTruoc()).append("\n");
        }

        if (s.getPin() != null) {
            text.append("Dung lượng pin: ").append(s.getPin()).append(" mAh");
            if (s.getCongSuatSac() != null) {
                text.append(", công suất sạc ").append(s.getCongSuatSac()).append("W");
            }
            text.append("\n");
        }

        if (s.isHo5G()) {
            text.append("Hỗ trợ mạng 5G: Có\n");
        }
        if (s.isNfc()) {
            text.append("Công nghệ kết nối NFC: Có\n");
        }

        if (s.getHeDieuHanh() != null && !s.getHeDieuHanh().isBlank()) {
            text.append("Hệ điều hành: ").append(s.getHeDieuHanh()).append("\n");
        }

        if (s.getMoTa() != null && !s.getMoTa().isBlank()) {
            text.append("Mô tả chi tiết: ").append(s.getMoTa().trim()).append("\n");
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("maSanPham", ma);
        meta.put("tenMay", tenMay);
        meta.put("hang", hang);
        if (giaBan != null) {
            meta.put("giaBan", giaBan.doubleValue());
        }
        meta.put("tonKho", tonKho);

        return new Document(ma, text.toString(), meta);
    }
}
