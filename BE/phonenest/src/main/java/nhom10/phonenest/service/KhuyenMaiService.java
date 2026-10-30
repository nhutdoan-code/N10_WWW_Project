package nhom10.phonenest.service;

import nhom10.phonenest.dto.KhuyenMaiDto;
import nhom10.phonenest.entity.KhuyenMai;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.LoaiKhuyenMai;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class KhuyenMaiService {

    private final KhuyenMaiRepository khuyenMaiRepository;
    private final NhatKyHoatDongService nhatKyHoatDongService;

    @Transactional(readOnly = true)
    public List<KhuyenMaiDto> list() {
        return khuyenMaiRepository.findAll().stream().map(this::toDto).toList();
    }

    public KhuyenMaiDto tao(KhuyenMaiDto d) {
        String ma = d.maKm().trim().toUpperCase();
        if (khuyenMaiRepository.existsById(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Mã khuyến mãi đã tồn tại");
        }
        return luu(new KhuyenMai(), ma, d, true);
    }

    public KhuyenMaiDto sua(String ma, KhuyenMaiDto d) {
        return luu(tim(ma), ma, d, false);
    }

    public void xoa(String ma) {
        KhuyenMai k = tim(ma);
        Map<String, Object> duLieuTruoc = snapshot(k);
        khuyenMaiRepository.delete(k);
        nhatKyHoatDongService.ghi(HanhDongAudit.DELETE_PROMOTION, LoaiDoiTuongAudit.PROMOTION,
                ma, duLieuTruoc, null);
    }

    private KhuyenMaiDto luu(KhuyenMai k, String ma, KhuyenMaiDto d, boolean taoMoi) {
        Map<String, Object> duLieuTruoc = taoMoi ? null : snapshot(k);
        if (d.ngayKetThuc().isBefore(d.ngayBatDau())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }
        k.setMaKm(ma);
        k.setTenKm(d.tenKm().trim());
        k.setPhanTramGiam(d.phanTramGiam());
        k.setSoTienGiamToiDa(d.soTienGiamToiDa());
        k.setNgayBatDau(d.ngayBatDau());
        k.setNgayKetThuc(d.ngayKetThuc());
        k.setLoai(d.loai() == null ? LoaiKhuyenMai.DON_HANG : d.loai());
        k.setDonToiThieu(d.donToiThieu() == null ? BigDecimal.ZERO : d.donToiThieu());
        k.setSoLuong(d.soLuong());
        k.setChiKhachMoi(d.chiKhachMoi());
        k.setDieuKienApDung(d.dieuKienApDung());
        KhuyenMai daLuu = khuyenMaiRepository.save(k);
        Map<String, Object> duLieuSau = snapshot(daLuu);
        if (taoMoi) {
            nhatKyHoatDongService.ghi(HanhDongAudit.CREATE_PROMOTION, LoaiDoiTuongAudit.PROMOTION,
                    daLuu.getMaKm(), null, duLieuSau);
        } else {
            nhatKyHoatDongService.ghi(HanhDongAudit.UPDATE_PROMOTION, LoaiDoiTuongAudit.PROMOTION,
                    daLuu.getMaKm(), duLieuTruoc, duLieuSau);
        }
        return toDto(daLuu);
    }

    private KhuyenMai tim(String ma) {
        return khuyenMaiRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy khuyến mãi"));
    }

    private Map<String, Object> snapshot(KhuyenMai k) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("tenKm", k.getTenKm());
        duLieu.put("loai", k.getLoai() == null ? null : k.getLoai().name());
        duLieu.put("phanTramGiam", k.getPhanTramGiam());
        duLieu.put("soTienGiamToiDa", k.getSoTienGiamToiDa());
        duLieu.put("donToiThieu", k.getDonToiThieu());
        duLieu.put("soLuong", k.getSoLuong());
        duLieu.put("daDung", k.getDaDung());
        duLieu.put("chiKhachMoi", k.isChiKhachMoi());
        duLieu.put("gioiHanMoiKhach", k.getGioiHanMoiKhach());
        duLieu.put("ngayBatDau", k.getNgayBatDau());
        duLieu.put("ngayKetThuc", k.getNgayKetThuc());
        duLieu.put("dieuKienApDung", k.getDieuKienApDung());
        duLieu.put("trangThaiHoatDong", k.isTrangThaiHoatDong());
        return duLieu;
    }

    private KhuyenMaiDto toDto(KhuyenMai k) {
        return new KhuyenMaiDto(k.getMaKm(), k.getTenKm(), k.getLoai(), k.getPhanTramGiam(), k.getSoTienGiamToiDa(),
                k.getDonToiThieu(), k.getSoLuong(), k.getDaDung(), k.isChiKhachMoi(),
                k.getNgayBatDau(), k.getNgayKetThuc(), k.getDieuKienApDung());
    }

    public String lyDoKhongDung(KhuyenMai k, BigDecimal tamTinh, boolean khachMoi) {
        LocalDate hom = LocalDate.now();
        if (hom.isBefore(k.getNgayBatDau()) || hom.isAfter(k.getNgayKetThuc())) {
            return "Mã khuyến mãi không còn hiệu lực";
        }
        if (k.getSoLuong() != null && k.getDaDung() >= k.getSoLuong()) {
            return "Mã khuyến mãi đã hết lượt sử dụng";
        }
        if (k.isChiKhachMoi() && !khachMoi) {
            return "Chỉ dành cho khách hàng mua lần đầu";
        }
        if (tamTinh.compareTo(k.getDonToiThieu()) < 0) {
            String thieu = NumberFormat.getInstance(Locale.of("vi", "VN")).format(k.getDonToiThieu().subtract(tamTinh));
            return "Mua thêm " + thieu + " ₫ để dùng mã này";
        }
        return null;
    }

    public BigDecimal tinhGiam(KhuyenMai k, BigDecimal coSo) {
        BigDecimal giam = coSo.multiply(k.getPhanTramGiam()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal max = k.getSoTienGiamToiDa();
        return max != null && giam.compareTo(max) > 0 ? max : giam;
    }
}
