package nhom10.phonenest.service;

import nhom10.phonenest.dto.DoiTraDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.YeuCauDoiTra;
import nhom10.phonenest.enums.TrangThaiDoiTra;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.YeuCauDoiTraRepository;
import nhom10.phonenest.util.IdGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DoiTraService {

    private static final int SO_NGAY_DOI_TRA = 7;

    private final YeuCauDoiTraRepository yeuCauDoiTraRepository;
    private final DonHangRepository donHangRepository;
    private final KhachHangRepository khachHangRepository;
    private final ThongBaoService thongBaoService;

    public DoiTraDto tao(String maKh, DoiTraDto.Request r) {
        DonHang d = donHangRepository.findById(r.maDonHang())
                .filter(x -> x.getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (d.getTrangThai() != TrangThaiDonHang.DA_GIAO) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ đơn hàng đã giao mới được yêu cầu đổi trả");
        }
        LocalDateTime moc = d.getNgayCapNhat() != null ? d.getNgayCapNhat() : d.getNgayMua();
        if (ChronoUnit.DAYS.between(moc, LocalDateTime.now()) > SO_NGAY_DOI_TRA) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đã quá " + SO_NGAY_DOI_TRA + " ngày kể từ khi nhận hàng, không thể đổi trả");
        }
        if (yeuCauDoiTraRepository.findByMaDonHang(d.getMaDonHang()).isPresent()) {
            throw new AppException(HttpStatus.CONFLICT, "Đơn hàng này đã có yêu cầu đổi trả");
        }
        YeuCauDoiTra y = new YeuCauDoiTra();
        y.setMaYeuCau(IdGenerator.next("YC"));
        y.setMaDonHang(d.getMaDonHang());
        y.setLyDo(r.lyDo().trim());
        y.setHinhAnhMinhChung(r.hinhAnhMinhChung());
        y.setNgayTao(LocalDate.now());
        y.setTrangThai(TrangThaiDoiTra.CHO_DUYET);
        yeuCauDoiTraRepository.save(y);
        thongBaoService.guiQuanTri("Có yêu cầu đổi trả mới cho đơn " + d.getMaDonHang());
        return toDto(y);
    }

    @Transactional(readOnly = true)
    public List<DoiTraDto> cuaToi(String maKh) {
        return donHangRepository.findByMaKhOrderByNgayMuaDesc(maKh).stream()
                .map(d -> yeuCauDoiTraRepository.findByMaDonHang(d.getMaDonHang()))
                .flatMap(java.util.Optional::stream)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DoiTraDto> tatCa() {
        return yeuCauDoiTraRepository.findAllByOrderByNgayTaoDesc().stream().map(this::toDto).toList();
    }

    public DoiTraDto capNhat(String ma, TrangThaiDoiTra moi) {
        YeuCauDoiTra y = yeuCauDoiTraRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đổi trả"));
        boolean hopLe = switch (y.getTrangThai()) {
            case CHO_DUYET -> moi == TrangThaiDoiTra.DA_DUYET || moi == TrangThaiDoiTra.TU_CHOI;
            case DA_DUYET -> moi == TrangThaiDoiTra.DA_HOAN_TIEN;
            default -> false;
        };
        if (!hopLe) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể chuyển yêu cầu sang trạng thái này");
        }
        y.setTrangThai(moi);
        donHangRepository.findById(y.getMaDonHang()).ifPresent(d ->
                thongBaoService.gui(d.getMaKh(), "Yêu cầu đổi trả đơn " + d.getMaDonHang() + ": " + nhan(moi)));
        return toDto(y);
    }

    private String nhan(TrangThaiDoiTra t) {
        return switch (t) {
            case CHO_DUYET -> "chờ duyệt";
            case DA_DUYET -> "đã được duyệt";
            case TU_CHOI -> "bị từ chối";
            case DA_HOAN_TIEN -> "đã hoàn tiền";
        };
    }

    private DoiTraDto toDto(YeuCauDoiTra y) {
        String ten = donHangRepository.findById(y.getMaDonHang())
                .flatMap(d -> khachHangRepository.findById(d.getMaKh()))
                .map(KhachHang::getHoTen).orElse("");
        return new DoiTraDto(y.getMaYeuCau(), y.getMaDonHang(), ten, y.getLyDo(), y.getHinhAnhMinhChung(),
                y.getNgayTao(), y.getTrangThai());
    }
}
