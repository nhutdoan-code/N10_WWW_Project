package nhom10.phonenest.service;

import nhom10.phonenest.dto.DanhGiaDto;
import nhom10.phonenest.entity.DanhGia;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiDoiTra;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DanhGiaRepository;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.YeuCauDoiTraRepository;
import nhom10.phonenest.util.IdGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DanhGiaService {

    private static final int HAN_DANH_GIA_NGAY = 30;

    private final DanhGiaRepository danhGiaRepository;
    private final DonHangRepository donHangRepository;
    private final SanPhamRepository sanPhamRepository;
    private final KhachHangRepository khachHangRepository;
    private final YeuCauDoiTraRepository yeuCauDoiTraRepository;

    public DanhGiaDto tao(String maKh, DanhGiaDto.Request r) {
        DonHang don = donCuaToi(maKh, r.maDonHang());
        String lyDo = lyDoKhongDuoc(don, r.maSanPham());
        if (lyDo != null) {
            throw new AppException(HttpStatus.BAD_REQUEST, lyDo);
        }
        String noiDung = r.noiDungBinhLuan() == null ? "" : r.noiDungBinhLuan().trim();
        if (r.diemSao() <= 2 && noiDung.length() < 10) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Với đánh giá 1 - 2 sao, vui lòng cho biết lý do (ít nhất 10 ký tự)");
        }
        DanhGia d = DanhGia.builder()
                .maDanhGia(IdGenerator.next("DG"))
                .maKh(maKh)
                .maDonHang(don.getMaDonHang())
                .maSanPham(r.maSanPham())
                .diemSao(r.diemSao())
                .noiDungBinhLuan(noiDung.isEmpty() ? null : noiDung)
                .hinhAnhThucTe(r.hinhAnhThucTe() == null || r.hinhAnhThucTe().isBlank() ? null : r.hinhAnhThucTe().trim())
                .ngayDanhGia(LocalDate.now())
                .build();
        try {
            DanhGia luu = danhGiaRepository.saveAndFlush(d);
            capNhatDiem(r.maSanPham());
            return toDto(luu);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(HttpStatus.CONFLICT, "Bạn đã đánh giá sản phẩm này trong đơn hàng");
        }
    }

    @Transactional(readOnly = true)
    public List<DanhGiaDto.TrangThai> theoDonHang(String maKh, String maDonHang) {
        DonHang don = donCuaToi(maKh, maDonHang);
        return don.getChiTiet().stream().map(c -> {
            Optional<DanhGia> da = danhGiaRepository.findByMaDonHangAndMaSanPham(maDonHang, c.getMaSanPham());
            String lyDo = da.isPresent() ? "Bạn đã đánh giá sản phẩm này" : lyDoKhongDuoc(don, c.getMaSanPham());
            return new DanhGiaDto.TrangThai(c.getMaSanPham(), lyDo == null, lyDo == null ? "" : lyDo, da.map(this::toDto).orElse(null));
        }).toList();
    }

    private String lyDoKhongDuoc(DonHang don, String maSanPham) {
        if (don.getTrangThai() != TrangThaiDonHang.DA_GIAO) {
            return "Chỉ đánh giá được khi đơn hàng đã giao thành công";
        }
        if (don.getChiTiet().stream().noneMatch(c -> c.getMaSanPham().equals(maSanPham))) {
            return "Sản phẩm không có trong đơn hàng này";
        }
        if (yeuCauDoiTraRepository.findByMaDonHang(don.getMaDonHang())
                .filter(y -> y.getTrangThai() != TrangThaiDoiTra.TU_CHOI).isPresent()) {
            return "Đơn hàng đã có yêu cầu đổi trả / hoàn tiền nên không thể đánh giá";
        }
        LocalDateTime giao = don.getNgayGiao() != null ? don.getNgayGiao() : don.getNgayCapNhat();
        if (giao != null && LocalDateTime.now().isAfter(giao.plusDays(HAN_DANH_GIA_NGAY))) {
            return "Đã quá hạn đánh giá (" + HAN_DANH_GIA_NGAY + " ngày kể từ khi nhận hàng)";
        }
        if (danhGiaRepository.findByMaDonHangAndMaSanPham(don.getMaDonHang(), maSanPham).isPresent()) {
            return "Bạn đã đánh giá sản phẩm này";
        }
        return null;
    }

    private DonHang donCuaToi(String maKh, String maDonHang) {
        return donHangRepository.findById(maDonHang)
                .filter(d -> d.getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
    }

    @Transactional(readOnly = true)
    public List<DanhGiaDto> theoSanPham(String maSanPham) {
        return danhGiaRepository.findByMaSanPhamOrderByNgayDanhGiaDesc(maSanPham).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DanhGiaDto> cuaToi(String maKh) {
        return danhGiaRepository.findByMaKhOrderByNgayDanhGiaDesc(maKh).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DanhGiaDto> tatCa() {
        return danhGiaRepository.findAllByOrderByNgayDanhGiaDesc().stream().map(this::toDto).toList();
    }

    public void xoa(String ma) {
        DanhGia d = danhGiaRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá"));
        danhGiaRepository.delete(d);
        danhGiaRepository.flush();
        capNhatDiem(d.getMaSanPham());
    }

    private void capNhatDiem(String maSanPham) {
        Double tb = danhGiaRepository.trungBinh(maSanPham);
        BigDecimal diem = tb == null ? BigDecimal.ZERO : BigDecimal.valueOf(tb).setScale(1, RoundingMode.HALF_UP);
        sanPhamRepository.findById(maSanPham).ifPresent(s -> s.setDiemDanhGiaTb(diem));
    }

    private DanhGiaDto toDto(DanhGia d) {
        return new DanhGiaDto(d.getMaDanhGia(), d.getMaDonHang(), d.getMaSanPham(),
                sanPhamRepository.findById(d.getMaSanPham()).map(SanPham::getTenMay).orElse(""),
                khachHangRepository.findById(d.getMaKh()).map(KhachHang::getHoTen).orElse("Ẩn danh"),
                d.getDiemSao(), d.getNoiDungBinhLuan(), d.getHinhAnhThucTe(), d.getNgayDanhGia());
    }
}
