package nhom10.phonenest.service;

import nhom10.phonenest.dto.KhuyenMaiDto;
import nhom10.phonenest.entity.KhuyenMai;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.LoaiKhuyenMai;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.SuDungKhuyenMaiRepository;
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
    private final SuDungKhuyenMaiRepository suDungKhuyenMaiRepository;
    private final KhachHangRepository khachHangRepository;
    private final DonHangRepository donHangRepository;
    private final NhatKyHoatDongService nhatKyHoatDongService;

    @Transactional(readOnly = true)
    public List<KhuyenMaiDto> list() {
        return khuyenMaiRepository.findAll().stream().map(this::toDto).toList();
    }

    public KhuyenMaiDto tao(KhuyenMaiDto d) {
        String ma = chuanHoaMa(d.maKm());
        if (khuyenMaiRepository.existsById(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Mã khuyến mãi đã tồn tại");
        }
        return luu(new KhuyenMai(), ma, d, true);
    }

    public KhuyenMaiDto sua(String ma, KhuyenMaiDto d) {
        String maChuan = chuanHoaMa(ma);
        if (!maChuan.equals(chuanHoaMa(d.maKm()))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không được thay đổi mã khuyến mãi");
        }
        return luu(tim(maChuan), maChuan, d, false);
    }

    public void xoa(String ma) {
        KhuyenMai k = tim(chuanHoaMa(ma));
        if (suDungKhuyenMaiRepository.existsByKhuyenMaiMaKm(k.getMaKm())) {
            throw new AppException(HttpStatus.CONFLICT, "Mã đã được sử dụng, chỉ có thể tắt hoạt động");
        }
        Map<String, Object> duLieuTruoc = snapshot(k);
        khuyenMaiRepository.delete(k);
        nhatKyHoatDongService.ghi(HanhDongAudit.DELETE_PROMOTION, LoaiDoiTuongAudit.PROMOTION,
                k.getMaKm(), duLieuTruoc, null);
    }
public Map<String, Object> getImpact(String ma) {
    KhuyenMai k = tim(chuanHoaMa(ma));
    long ordersCount = donHangRepository.countOrdersByKhuyenMai(k.getMaKm());

    Map<String, Object> res = new LinkedHashMap<>();
    res.put("maKm", k.getMaKm());
    res.put("tenKm", k.getTenKm());
    res.put("daDung", k.getDaDung());
    res.put("soDonHangDaDung", ordersCount);
    res.put("trangThaiHoatDong", k.isTrangThaiHoatDong());
    res.put("coDonHangLienQuan", ordersCount > 0 || k.getDaDung() > 0);

    return res;
}

public KhuyenMaiDto archive(String ma) {
    KhuyenMai k = tim(chuanHoaMa(ma));

    if (!k.isTrangThaiHoatDong()) {
        return toDto(k);
    }

    Map<String, Object> duLieuTruoc = snapshot(k);

    k.setTrangThaiHoatDong(false);
    KhuyenMai saved = khuyenMaiRepository.save(k);

    nhatKyHoatDongService.ghi(
            HanhDongAudit.UPDATE_PROMOTION,
            LoaiDoiTuongAudit.PROMOTION,
            saved.getMaKm(),
            duLieuTruoc,
            snapshot(saved)
    );

    return toDto(saved);
}

public KhuyenMaiDto restore(String ma) {
    KhuyenMai k = tim(chuanHoaMa(ma));

    if (k.isTrangThaiHoatDong()) {
        return toDto(k);
    }

    Map<String, Object> duLieuTruoc = snapshot(k);

    k.setTrangThaiHoatDong(true);
    KhuyenMai saved = khuyenMaiRepository.save(k);

    nhatKyHoatDongService.ghi(
            HanhDongAudit.UPDATE_PROMOTION,
            LoaiDoiTuongAudit.PROMOTION,
            saved.getMaKm(),
            duLieuTruoc,
            snapshot(saved)
    );

    return toDto(saved);
}

    private KhuyenMaiDto luu(KhuyenMai k, String ma, KhuyenMaiDto d, boolean taoMoi) {
        Map<String, Object> duLieuTruoc = taoMoi ? null : snapshot(k);
        if (d.ngayKetThuc().isBefore(d.ngayBatDau())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }
        if (d.phanTramGiam() == null || d.phanTramGiam().compareTo(BigDecimal.ZERO) <= 0
                || d.phanTramGiam().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Phần trăm giảm phải trong khoảng (0, 100]"
            );
        }
        LoaiKhuyenMai loai = d.loai() == null ? LoaiKhuyenMai.DON_HANG : d.loai();
        if (!taoMoi && k.getLoai() != loai) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không được thay đổi loại voucher");
        }
        if (!taoMoi && k.getPhanTramGiam().compareTo(d.phanTramGiam()) != 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không được thay đổi phần trăm giảm");
        }
        if (loai == LoaiKhuyenMai.VAN_CHUYEN && d.soTienGiamToiDa() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Voucher vận chuyển phải có mức giảm tối đa");
        }
        if (d.soTienGiamToiDa() != null && d.soTienGiamToiDa().compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Mức giảm tối đa không hợp lệ");
        }
        if (d.donToiThieu() != null && d.donToiThieu().compareTo(BigDecimal.ZERO) < 0
                || d.soLuong() != null && d.soLuong() < 1
                || d.gioiHanMoiKhach() < 1) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Giá trị giới hạn khuyến mãi không hợp lệ");
        }
        if (!taoMoi && d.soLuong() != null && d.soLuong() < k.getDaDung()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng mã không được nhỏ hơn số lượt đã dùng");
        }

        k.setMaKm(ma);
        k.setTenKm(d.tenKm().trim());
        k.setPhanTramGiam(d.phanTramGiam());
        k.setSoTienGiamToiDa(d.soTienGiamToiDa());
        k.setNgayBatDau(d.ngayBatDau());
        k.setNgayKetThuc(d.ngayKetThuc());
        k.setLoai(loai);
        k.setDonToiThieu(d.donToiThieu() == null ? BigDecimal.ZERO : d.donToiThieu());
        k.setSoLuong(d.soLuong());
        k.setChiKhachMoi(d.chiKhachMoi());
        k.setGioiHanMoiKhach(d.gioiHanMoiKhach());
        // Trạng thái hiệu lực được xác định từ ngày bắt đầu và ngày kết thúc.
        k.setTrangThaiHoatDong(true);
        k.setDieuKienApDung(d.dieuKienApDung());
        KhuyenMai daLuu = khuyenMaiRepository.save(k);
        Map<String, Object> duLieuSau = snapshot(daLuu);
        nhatKyHoatDongService.ghi(taoMoi ? HanhDongAudit.CREATE_PROMOTION : HanhDongAudit.UPDATE_PROMOTION,
                LoaiDoiTuongAudit.PROMOTION, daLuu.getMaKm(), duLieuTruoc, duLieuSau);
        return toDto(daLuu);
    }

    private String chuanHoaMa(String ma) {
        if (ma == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập mã khuyến mãi");
        }
        String normalized = ma.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]+")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Mã khuyến mãi chỉ gồm chữ in hoa, số và dấu gạch ngang hoặc gạch dưới");
        }
        return normalized;
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
    	return new KhuyenMaiDto(
            k.getMaKm(),
            k.getTenKm(),
            k.getLoai(),
            k.getPhanTramGiam(),
            k.getSoTienGiamToiDa(),
            k.getDonToiThieu(),
            k.getSoLuong(),
            k.getDaDung(),
            k.isChiKhachMoi(),
            k.getGioiHanMoiKhach(),
            k.getNgayBatDau(),
            k.getNgayKetThuc(),
            k.getDieuKienApDung(),
            k.isTrangThaiHoatDong()
    	);
    }

    public String lyDoKhongDung(KhuyenMai k, BigDecimal tamTinh, boolean khachMoi, String maKh) {
        LocalDate hom = LocalDate.now();
        if (hom.isBefore(k.getNgayBatDau()) || hom.isAfter(k.getNgayKetThuc())) {
            return "Mã khuyến mãi không còn hiệu lực";
        }
        if (k.getSoLuong() != null && k.getDaDung() >= k.getSoLuong()) {
            return "Mã khuyến mãi đã hết lượt sử dụng";
        }
        if (tamTinh.compareTo(k.getDonToiThieu()) < 0) {
            String thieu = NumberFormat.getInstance(Locale.of("vi", "VN"))
                    .format(k.getDonToiThieu().subtract(tamTinh));
            return "Mua thêm " + thieu + " ₫ để dùng mã này";
        }
        if (k.isChiKhachMoi() && !khachMoi) {
            return "Chỉ dành cho khách hàng mua lần đầu";
        }
        if (maKh != null && demLuotDung(k.getMaKm(), maKh) >= k.getGioiHanMoiKhach()) {
            return "Bạn đã đạt giới hạn sử dụng mã này";
        }
        return null;
    }

    public long demLuotDung(String maKm, String maKh) {
        if (maKm == null || maKh == null) return 0;
        try {
            return suDungKhuyenMaiRepository.findByKhuyenMaiMaKmAndKhachHangMaKhForCheck(maKm, maKh).size();
        } catch (Exception e) {
            return suDungKhuyenMaiRepository.countByKhuyenMaiMaKmAndKhachHangMaKh(maKm, maKh);
        }
    }

    public BigDecimal tinhGiam(KhuyenMai k, BigDecimal coSo) {
        BigDecimal giam = coSo.multiply(k.getPhanTramGiam())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
        if (k.getSoTienGiamToiDa() != null && giam.compareTo(k.getSoTienGiamToiDa()) > 0) {
            giam = k.getSoTienGiamToiDa();
        }
        return giam.max(BigDecimal.ZERO).min(coSo.max(BigDecimal.ZERO));
    }

    public void ghiNhanSuDung(KhuyenMai k, String maKh, String maDonHang) {
        if (khuyenMaiRepository.tangDaDungNeuConLuot(k.getMaKm()) == 0) {
            throw new AppException(HttpStatus.CONFLICT, "Mã khuyến mãi " + k.getMaKm() + " vừa hết lượt sử dụng", "VOUCHER_USAGE_LIMIT_REACHED");
        }
        k.setDaDung(k.getDaDung() + 1);
        nhom10.phonenest.entity.SuDungKhuyenMai suDung = new nhom10.phonenest.entity.SuDungKhuyenMai();
        suDung.setMaSuDung(nhom10.phonenest.util.IdGenerator.next("SDKM"));
        suDung.setKhuyenMai(k);
        suDung.setKhachHang(khachHangRepository.findById(maKh)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng")));
        suDung.setDonHang(donHangRepository.findById(maDonHang)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng")));
        suDung.setNgaySuDung(java.time.LocalDateTime.now());
        suDungKhuyenMaiRepository.save(suDung);
    }

    public void hoanLuot(KhuyenMai k, String maDonHang) {
        if (k == null) {
            return;
        }
        KhuyenMai locked = khuyenMaiRepository.findByIdForUpdate(k.getMaKm()).orElse(k);
        List<nhom10.phonenest.entity.SuDungKhuyenMai> usages =
                suDungKhuyenMaiRepository.findByDonHangMaDonHangAndKhuyenMaiMaKm(maDonHang, locked.getMaKm());
        if (!usages.isEmpty()) {
            suDungKhuyenMaiRepository.deleteAll(usages);
            if (khuyenMaiRepository.hoanDaDung(locked.getMaKm()) > 0) {
                locked.setDaDung(Math.max(0, locked.getDaDung() - 1));
            }
        }
    }
}
