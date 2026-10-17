package nhom10.phonenest.service;

import nhom10.phonenest.dto.DanhGiaDto;
import nhom10.phonenest.entity.DanhGia;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DanhGiaRepository;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.util.IdGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DanhGiaService {

    private final DanhGiaRepository danhGiaRepository;
    private final DonHangRepository donHangRepository;
    private final SanPhamRepository sanPhamRepository;
    private final KhachHangRepository khachHangRepository;

    public DanhGiaDto tao(String maKh, DanhGiaDto.Request r) {
        DanhGiaDto.Eligible e = kiemTra(maKh, r.maSanPham());
        if (!e.duocDanhGia()) {
            throw new AppException(HttpStatus.FORBIDDEN, e.lyDo());
        }
        DanhGia d = DanhGia.builder()
                .maDanhGia(IdGenerator.next("DG"))
                .maKh(maKh)
                .maSanPham(r.maSanPham())
                .diemSao(r.diemSao())
                .noiDungBinhLuan(r.noiDungBinhLuan())
                .hinhAnhThucTe(r.hinhAnhThucTe())
                .ngayDanhGia(LocalDate.now())
                .build();
        DanhGia luu = danhGiaRepository.save(d);
        capNhatDiem(r.maSanPham());
        return toDto(luu);
    }

    @Transactional(readOnly = true)
    public DanhGiaDto.Eligible kiemTra(String maKh, String maSanPham) {
        if (danhGiaRepository.existsByMaKhAndMaSanPham(maKh, maSanPham)) {
            return new DanhGiaDto.Eligible(false, "Bạn đã đánh giá sản phẩm này");
        }
        boolean daNhan = donHangRepository.findByMaKhOrderByNgayMuaDesc(maKh).stream()
                .anyMatch(d -> d.getTrangThai() == TrangThaiDonHang.DA_GIAO
                        && d.getChiTiet().stream().anyMatch(c -> c.getMaSanPham().equals(maSanPham)));
        return daNhan ? new DanhGiaDto.Eligible(true, "")
                : new DanhGiaDto.Eligible(false, "Chỉ khách đã nhận hàng mới được đánh giá sản phẩm");
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
        return new DanhGiaDto(d.getMaDanhGia(), d.getMaSanPham(),
                sanPhamRepository.findById(d.getMaSanPham()).map(SanPham::getTenMay).orElse(""),
                khachHangRepository.findById(d.getMaKh()).map(KhachHang::getHoTen).orElse("Ẩn danh"),
                d.getDiemSao(), d.getNoiDungBinhLuan(), d.getHinhAnhThucTe(), d.getNgayDanhGia());
    }
}
