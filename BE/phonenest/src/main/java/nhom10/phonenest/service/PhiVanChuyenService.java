package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.KetQuaPhiVanChuyen;
import nhom10.phonenest.dto.PhiVanChuyenDto;
import nhom10.phonenest.entity.DiaChi;
import nhom10.phonenest.entity.PhiVanChuyen;
import nhom10.phonenest.entity.PhuongXa;
import nhom10.phonenest.entity.TinhThanh;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDiaBan;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.PhiVanChuyenRepository;
import nhom10.phonenest.repository.PhuongXaRepository;
import nhom10.phonenest.repository.TinhThanhRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phí vận chuyển cho hàng điện thoại (nhẹ nhưng giá trị cao), kho đặt tại TP.HCM:
 * <pre>
 * Phí ship = 0                                        nếu tạm tính >= ngưỡng freeship của khu vực
 *          = phí vùng + phí khai giá + phụ phí COD    ngược lại
 * phí vùng    = phí cơ bản (phường) + phụ phí xã (ngoại thành) / phụ phí đặc khu (huyện đảo)
 * phí khai giá = 0,5% giá trị hàng, tối đa 50.000đ, bắt buộc với đơn từ 3.000.000đ
 * phụ phí COD  = 15.000đ khi thanh toán COD cho đơn trên 10.000.000đ
 * </pre>
 * Phí vùng, phụ phí, ngưỡng freeship và số ngày giao cấu hình theo từng tỉnh/thành (34 đơn vị sau sáp nhập).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PhiVanChuyenService {

    private static final String MAC_DINH = "Khu vực khác (mặc định)";

    static final BigDecimal NGUONG_KHAI_GIA = new BigDecimal("3000000");
    static final BigDecimal TY_LE_KHAI_GIA = new BigDecimal("0.005");
    static final BigDecimal TRAN_KHAI_GIA = new BigDecimal("50000");
    static final BigDecimal NGUONG_PHU_PHI_COD = new BigDecimal("10000000");
    static final BigDecimal PHU_PHI_COD = new BigDecimal("15000");
    /** Đặc khu / huyện đảo phải trung chuyển tàu hoặc máy bay: cộng thêm ngày giao. */
    static final int THEM_NGAY_DAC_KHU_TOI_THIEU = 1;
    static final int THEM_NGAY_DAC_KHU_TOI_DA = 2;
    /** Đơn đặt sau giờ này được bàn giao cho đơn vị vận chuyển vào ngày hôm sau. */
    static final LocalTime GIO_CHOT_DON = LocalTime.of(16, 0);

    private final PhiVanChuyenRepository phiVanChuyenRepository;
    private final TinhThanhRepository tinhThanhRepository;
    private final PhuongXaRepository phuongXaRepository;
    private final NhatKyHoatDongService nhatKyHoatDongService;

    @Transactional(readOnly = true)
    public List<PhiVanChuyenDto> list() {
        return phiVanChuyenRepository.findAll().stream()
                .map(this::toDto)
                .sorted(java.util.Comparator
                        .comparing((PhiVanChuyenDto d) -> d.vung() == null ? 99 : d.vung())
                        .thenComparing(PhiVanChuyenDto::tenKhuVuc, java.text.Collator.getInstance(java.util.Locale.of("vi", "VN"))))
                .toList();
    }

    public PhiVanChuyenDto tao(PhiVanChuyenDto d) {
        if (d.maTinh() == null || d.maTinh().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn tỉnh / thành phố");
        }
        if (!tinhThanhRepository.existsById(d.maTinh())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Tỉnh / thành phố không hợp lệ");
        }
        if (phiVanChuyenRepository.existsByMaTinh(d.maTinh())) {
            throw new AppException(HttpStatus.CONFLICT, "Tỉnh / thành phố này đã có bảng phí");
        }
        PhiVanChuyen p = new PhiVanChuyen();
        p.setMaPhi(IdGenerator.next("PVC"));
        p.setMaTinh(d.maTinh());
        return luu(p, d, true);
    }

    public PhiVanChuyenDto sua(String ma, PhiVanChuyenDto d) {
        return luu(tim(ma), d, false);
    }

    public void xoa(String ma) {
        PhiVanChuyen p = tim(ma);
        if (p.getMaTinh() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể xóa mức phí mặc định");
        }
        Map<String, Object> duLieuTruoc = snapshot(p);
        phiVanChuyenRepository.delete(p);
        nhatKyHoatDongService.ghi(HanhDongAudit.DELETE_SHIPPING_FEE, LoaiDoiTuongAudit.SHIPPING_FEE,
                ma, duLieuTruoc, null);
    }

    @Transactional(readOnly = true)
    public PhiVanChuyen theoDiaChi(String maTinh) {
        return phiVanChuyenRepository.findByMaTinh(maTinh)
                .or(phiVanChuyenRepository::findByMaTinhIsNull)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Chưa cấu hình phí vận chuyển cho khu vực này"));
    }

    /** Tính phí và thời gian giao dự kiến cho địa chỉ giao hàng của khách. */
    @Transactional(readOnly = true)
    public KetQuaPhiVanChuyen tinhChoDiaChi(DiaChi dc, BigDecimal tamTinh, PhuongThucTt phuongThuc, LocalDateTime thoiDiemDat) {
        LoaiDiaBan loai = LoaiDiaBan.tuTen(dc.getMaPhuong() == null ? null
                : phuongXaRepository.findById(dc.getMaPhuong()).map(PhuongXa::getTenPhuong).orElse(null));
        return tinh(theoDiaChi(dc.getMaTinh()), loai, tamTinh, phuongThuc, thoiDiemDat);
    }

    /** Công thức tính phí thuần (không truy cập CSDL). {@code phuongThuc} null = chưa chọn, không tính phụ phí COD. */
    public static KetQuaPhiVanChuyen tinh(PhiVanChuyen p, LoaiDiaBan loai, BigDecimal tamTinh,
                                          PhuongThucTt phuongThuc, LocalDateTime thoiDiemDat) {
        BigDecimal giaTri = tamTinh == null ? BigDecimal.ZERO : tamTinh;
        LoaiDiaBan diaBan = loai == null ? LoaiDiaBan.PHUONG : loai;

        BigDecimal phiVung = khongAm(p.getPhiCoBan());
        if (diaBan == LoaiDiaBan.XA) phiVung = phiVung.add(khongAm(p.getPhuPhiXa()));
        if (diaBan == LoaiDiaBan.DAC_KHU) phiVung = phiVung.add(khongAm(p.getPhuPhiDacKhu()));

        boolean mienPhi = p.getMienPhiTu() != null && giaTri.compareTo(p.getMienPhiTu()) >= 0;
        BigDecimal khaiGia = BigDecimal.ZERO;
        BigDecimal cod = BigDecimal.ZERO;
        if (!mienPhi) {
            if (giaTri.compareTo(NGUONG_KHAI_GIA) >= 0) {
                khaiGia = giaTri.multiply(TY_LE_KHAI_GIA).setScale(0, RoundingMode.HALF_UP).min(TRAN_KHAI_GIA);
            }
            if (phuongThuc == PhuongThucTt.COD && giaTri.compareTo(NGUONG_PHU_PHI_COD) > 0) {
                cod = PHU_PHI_COD;
            }
        }
        BigDecimal tong = mienPhi ? BigDecimal.ZERO : phiVung.add(khaiGia).add(cod);

        int toiThieu = soNgay(p.getSoNgayGiaoToiThieu(), 3);
        int toiDa = Math.max(toiThieu, soNgay(p.getSoNgayGiaoToiDa(), 5));
        if (diaBan == LoaiDiaBan.DAC_KHU) {
            toiThieu += THEM_NGAY_DAC_KHU_TOI_THIEU;
            toiDa += THEM_NGAY_DAC_KHU_TOI_DA;
        }
        LocalDateTime luc = thoiDiemDat == null ? LocalDateTime.now() : thoiDiemDat;
        LocalDate ngayBanGiao = luc.toLocalTime().isBefore(GIO_CHOT_DON) ? luc.toLocalDate() : luc.toLocalDate().plusDays(1);

        return new KetQuaPhiVanChuyen(p.getVung(), tenVung(p.getVung()), diaBan, phiVung, khaiGia, cod, tong, mienPhi,
                p.getMienPhiTu(), toiThieu, toiDa, moTaThoiGian(toiThieu, toiDa),
                ngayBanGiao.plusDays(toiThieu), ngayBanGiao.plusDays(toiDa));
    }

    public static String tenVung(Integer vung) {
        if (vung == null) return "Khu vực khác";
        return switch (vung) {
            case 0 -> "Nội tỉnh TP.HCM";
            case 1 -> "Lân cận & Miền Nam";
            case 2 -> "Miền Trung & Tây Nguyên";
            case 3 -> "Miền Bắc";
            default -> "Khu vực khác";
        };
    }

    static String moTaThoiGian(int toiThieu, int toiDa) {
        if (toiDa <= 0) return "Trong ngày";
        if (toiThieu <= 0) return "Trong ngày - " + toiDa + " ngày";
        if (toiThieu == toiDa) return toiDa + " ngày";
        return toiThieu + " - " + toiDa + " ngày";
    }

    private static int soNgay(Integer n, int macDinh) {
        return n == null || n < 0 ? macDinh : n;
    }

    private static BigDecimal khongAm(BigDecimal n) {
        return n == null ? BigDecimal.ZERO : n.max(BigDecimal.ZERO);
    }

    private PhiVanChuyenDto luu(PhiVanChuyen p, PhiVanChuyenDto d, boolean taoMoi) {
        if (d.soNgayGiaoToiThieu() > d.soNgayGiaoToiDa()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số ngày giao tối thiểu không được lớn hơn số ngày giao tối đa");
        }
        Map<String, Object> duLieuTruoc = taoMoi ? null : snapshot(p);
        p.setVung(d.vung());
        p.setPhiCoBan(d.phiCoBan());
        p.setPhuPhiXa(d.phuPhiXa() == null ? BigDecimal.ZERO : d.phuPhiXa());
        p.setPhuPhiDacKhu(d.phuPhiDacKhu() == null ? BigDecimal.ZERO : d.phuPhiDacKhu());
        p.setMienPhiTu(d.mienPhiTu());
        p.setSoNgayGiaoToiThieu(d.soNgayGiaoToiThieu());
        p.setSoNgayGiaoToiDa(d.soNgayGiaoToiDa());
        p.setThoiGianGiao(moTaThoiGian(d.soNgayGiaoToiThieu(), d.soNgayGiaoToiDa()));
        PhiVanChuyen daLuu = phiVanChuyenRepository.save(p);
        Map<String, Object> duLieuSau = snapshot(daLuu);
        if (taoMoi) {
            nhatKyHoatDongService.ghi(HanhDongAudit.CREATE_SHIPPING_FEE, LoaiDoiTuongAudit.SHIPPING_FEE,
                    daLuu.getMaPhi(), null, duLieuSau);
        } else {
            nhatKyHoatDongService.ghi(HanhDongAudit.UPDATE_SHIPPING_FEE, LoaiDoiTuongAudit.SHIPPING_FEE,
                    daLuu.getMaPhi(), duLieuTruoc, duLieuSau);
        }
        return toDto(daLuu);
    }

    private Map<String, Object> snapshot(PhiVanChuyen p) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("maTinh", p.getMaTinh());
        duLieu.put("vung", p.getVung());
        duLieu.put("phiCoBan", p.getPhiCoBan());
        duLieu.put("phuPhiXa", p.getPhuPhiXa());
        duLieu.put("phuPhiDacKhu", p.getPhuPhiDacKhu());
        duLieu.put("mienPhiTu", p.getMienPhiTu());
        duLieu.put("soNgayGiaoToiThieu", p.getSoNgayGiaoToiThieu());
        duLieu.put("soNgayGiaoToiDa", p.getSoNgayGiaoToiDa());
        duLieu.put("thoiGianGiao", p.getThoiGianGiao());
        return duLieu;
    }

    private PhiVanChuyen tim(String ma) {
        return phiVanChuyenRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy bảng phí vận chuyển"));
    }

    private PhiVanChuyenDto toDto(PhiVanChuyen p) {
        String ten = p.getMaTinh() == null ? MAC_DINH
                : tinhThanhRepository.findById(p.getMaTinh()).map(TinhThanh::getTenTinh).orElse(p.getMaTinh());
        return new PhiVanChuyenDto(p.getMaPhi(), p.getMaTinh(), ten, p.getVung(), tenVung(p.getVung()),
                p.getPhiCoBan(), p.getPhuPhiXa(), p.getPhuPhiDacKhu(), p.getMienPhiTu(),
                p.getSoNgayGiaoToiThieu(), p.getSoNgayGiaoToiDa(), p.getThoiGianGiao());
    }
}
