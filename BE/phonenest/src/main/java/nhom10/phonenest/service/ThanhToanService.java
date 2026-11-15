package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.CheckoutDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhom10.phonenest.util.DungLuongUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThanhToanService {

    private final GioHangRepository gioHangRepository;
    private final PhienBanSanPhamRepository phienBanRepository;
    private final DiaChiRepository diaChiRepository;
    private final DonHangRepository donHangRepository;
    private final KhuyenMaiRepository khuyenMaiRepository;
    private final KhuyenMaiService khuyenMaiService;
    private final PhiVanChuyenService phiVanChuyenService;
    private final DiaChiService diaChiService;

    public Map<String, Integer> mucMua(String maKh, String ma, Integer soLuong) {
        if (ma == null || ma.isBlank()) {
            GioHang g = gioHangRepository.findByKhachHangMaKh(maKh)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giỏ hàng"));
            if (g.getChiTiet().isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Giỏ hàng đang trống");
            }
            Map<String, Integer> m = new LinkedHashMap<>();
            g.getChiTiet().forEach(c -> m.put(c.getPhienBan().getMaPhienBan(), c.getSoLuong()));
            return m;
        }

        PhienBanSanPham p = resolve(ma);
        int sl = soLuong == null ? 1 : soLuong;
        if (p.getSanPham() == null || p.getSanPham().getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH
                || p.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH) {
            String ten = p.getSanPham() != null ? p.getSanPham().getTenMay() : "Sản phẩm";
            throw new AppException(HttpStatus.BAD_REQUEST, ten + " không còn kinh doanh");
        }
        PheDuyetBanHangPolicy.kiemTraDuocBan(p, null);
        if (sl < 1 || sl > p.getSoLuongTon()) {
            throw new AppException(HttpStatus.BAD_REQUEST, p.getSanPham().getTenMay() + " không đủ hàng");
        }
        return Map.of(p.getMaPhienBan(), sl);
    }

    public CheckoutDto xemTruoc(String maKh, String maDiaChi, String ma, Integer sl) {
        Map<String, Integer> muc = mucMua(maKh, ma, sl);
        DiaChi dc = diaChiRepository.findById(maDiaChi).filter(x -> x.getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Địa chỉ giao hàng không hợp lệ"));

        // Kiểm tra tính hợp lệ của mọi sản phẩm trong giỏ / checkout preview
        for (String pbId : muc.keySet()) {
            PhienBanSanPham p = phienBanRepository.findById(pbId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản sản phẩm"));
            if (p.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH
                    || (p.getSanPham() != null && p.getSanPham().getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH)) {
                String ten = p.getSanPham() != null ? p.getSanPham().getTenMay() : "Sản phẩm";
                throw new AppException(HttpStatus.BAD_REQUEST, ten + " không còn kinh doanh. Vui lòng loại bỏ khỏi giỏ hàng trước khi thanh toán.");
            }
            PheDuyetBanHangPolicy.kiemTraDuocBan(p, ". Vui lòng loại bỏ khỏi giỏ hàng trước khi thanh toán.");
        }

        List<CheckoutDto.Dong> items = muc.entrySet().stream().map(e -> {
            PhienBanSanPham p = phienBanRepository.findById(e.getKey()).orElseThrow();
            SanPham sp = p.getSanPham();
            return new CheckoutDto.Dong(p.getMaPhienBan(), sp.getTenMay() + " - " + p.getMauSac() + " " + DungLuongUtil.format(p.getBoNho()), SanPhamMapper.anhTheoMau(sp, p), e.getValue(), p.getGiaBan());
        }).toList();

        BigDecimal tam = items.stream().map(i -> i.donGia().multiply(BigDecimal.valueOf(i.soLuong()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        PhiVanChuyen pvc = phiVanChuyenService.theoDiaChi(dc.getMaTinh());
        BigDecimal phi = phiVanChuyenService.tinhPhi(pvc, tam);
        boolean moi = !donHangRepository.existsByKhachHangMaKhAndTrangThaiNot(maKh, TrangThaiDonHang.DA_HUY);
        // Chỉ liệt kê mã đang bật, đang trong thời gian hiệu lực và còn lượt; mã chưa đủ điều kiện
        // theo đơn (đơn tối thiểu, khách mới, giới hạn mỗi khách) vẫn hiện kèm lý do để khách biết.
        LocalDate homNay = LocalDate.now();
        List<CheckoutDto.Voucher> vs = khuyenMaiRepository.findAll().stream()
                .filter(k -> hienThiOThanhToan(k, homNay, moi))
                .map(k -> voucher(k, tam, phi, moi, maKh)).sorted(Comparator.comparing(CheckoutDto.Voucher::apDungDuoc).reversed().thenComparing(CheckoutDto.Voucher::soTienGiam, Comparator.reverseOrder())).toList();
        return new CheckoutDto(tam, phi, pvc.getMienPhiTu(), diaChiService.tenTinh(dc.getMaTinh()), pvc.getThoiGianGiao(), vs, items);
    }

    private PhienBanSanPham resolve(String ma) {
        return phienBanRepository.findById(ma).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản sản phẩm"));
    }

    /** Mã "Khách hàng mới" bị ẩn với khách đã có đơn: khách cũ không bao giờ đủ điều kiện dùng. */
    static boolean hienThiOThanhToan(KhuyenMai k, LocalDate homNay, boolean khachMoi) {
        return k.isTrangThaiHoatDong()
                && (!k.isChiKhachMoi() || khachMoi)
                && k.getNgayBatDau() != null && k.getNgayKetThuc() != null
                && !homNay.isBefore(k.getNgayBatDau()) && !homNay.isAfter(k.getNgayKetThuc())
                && (k.getSoLuong() == null || k.getDaDung() < k.getSoLuong());
    }

    private CheckoutDto.Voucher voucher(KhuyenMai k, BigDecimal tam, BigDecimal phi, boolean moi, String maKh) {
        boolean vc = k.getLoai() == LoaiKhuyenMai.VAN_CHUYEN;
        String ly = khuyenMaiService.lyDoKhongDung(k, tam, moi, maKh);
        if (ly == null && vc && phi.signum() == 0) ly = "Phí vận chuyển đã được miễn phí";
        BigDecimal g = khuyenMaiService.tinhGiam(k, vc ? phi : tam);
        return new CheckoutDto.Voucher(k.getMaKm(), k.getTenKm(), k.getLoai().name(), k.getPhanTramGiam(), k.getSoTienGiamToiDa(), k.getDonToiThieu(), k.getNgayBatDau(), k.getNgayKetThuc(), k.getDieuKienApDung(), k.isChiKhachMoi(), ly == null, ly, g);
    }
}
