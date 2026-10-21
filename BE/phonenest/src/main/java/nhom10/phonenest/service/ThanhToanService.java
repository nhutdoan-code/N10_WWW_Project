package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.CheckoutDto;
import nhom10.phonenest.entity.DiaChi;
import nhom10.phonenest.entity.GioHang;
import nhom10.phonenest.entity.KhuyenMai;
import nhom10.phonenest.entity.PhiVanChuyen;
import nhom10.phonenest.enums.LoaiKhuyenMai;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DiaChiRepository;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThanhToanService {

    private final GioHangRepository gioHangRepository;
    private final SanPhamRepository sanPhamRepository;
    private final DiaChiRepository diaChiRepository;
    private final DonHangRepository donHangRepository;
    private final KhuyenMaiRepository khuyenMaiRepository;
    private final KhuyenMaiService khuyenMaiService;
    private final PhiVanChuyenService phiVanChuyenService;
    private final DiaChiService diaChiService;

    public CheckoutDto xemTruoc(String maKh, String maDiaChi) {
        GioHang gio = gioHangRepository.findByMaKh(maKh)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giỏ hàng"));
        DiaChi dc = diaChiRepository.findById(maDiaChi)
                .filter(x -> x.getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Địa chỉ giao hàng không hợp lệ"));

        BigDecimal tamTinh = gio.getItems().entrySet().stream()
                .map(e -> sanPhamRepository.findById(e.getKey()).orElseThrow().getGiaBan().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PhiVanChuyen pvc = phiVanChuyenService.theoDiaChi(dc.getMaTinh());
        BigDecimal phi = phiVanChuyenService.tinhPhi(pvc, tamTinh);
        boolean khachMoi = !donHangRepository.existsByMaKhAndTrangThaiNot(maKh, TrangThaiDonHang.DA_HUY);

        LocalDate hom = LocalDate.now();
        List<CheckoutDto.Voucher> vouchers = khuyenMaiRepository.findAll().stream()
                .filter(k -> !hom.isBefore(k.getNgayBatDau()) && !hom.isAfter(k.getNgayKetThuc()))
                .map(k -> voucher(k, tamTinh, phi, khachMoi))
                .sorted(Comparator.comparing(CheckoutDto.Voucher::apDungDuoc).reversed()
                        .thenComparing(CheckoutDto.Voucher::soTienGiam, Comparator.reverseOrder()))
                .toList();
        return new CheckoutDto(tamTinh, phi, pvc.getMienPhiTu(), diaChiService.tenTinh(dc.getMaTinh()), pvc.getThoiGianGiao(), vouchers);
    }

    private CheckoutDto.Voucher voucher(KhuyenMai k, BigDecimal tamTinh, BigDecimal phi, boolean khachMoi) {
        boolean vanChuyen = k.getLoai() == LoaiKhuyenMai.VAN_CHUYEN;
        String lyDo = khuyenMaiService.lyDoKhongDung(k, tamTinh, khachMoi);
        if (lyDo == null && vanChuyen && phi.signum() == 0) {
            lyDo = "Phí vận chuyển đã được miễn phí";
        }
        BigDecimal giam = khuyenMaiService.tinhGiam(k, vanChuyen ? phi : tamTinh);
        return new CheckoutDto.Voucher(k.getMaKm(), k.getTenKm(), k.getLoai().name(), k.getPhanTramGiam(),
                k.getSoTienGiamToiDa(), k.getDonToiThieu(), k.getNgayBatDau(), k.getNgayKetThuc(),
                k.getDieuKienApDung(), lyDo == null, lyDo, giam);
    }
}
