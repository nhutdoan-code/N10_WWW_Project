package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.HauMaiDto;
import nhom10.phonenest.entity.ChiTietDonHang;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.LichSuHauMai;
import nhom10.phonenest.entity.YeuCauHauMai;
import nhom10.phonenest.enums.LoaiHauMai;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiHauMai;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.YeuCauHauMaiRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class HauMaiService {
    private static final List<TrangThaiHauMai> DANG_MO = List.of(
            TrangThaiHauMai.CHO_TIEP_NHAN,
            TrangThaiHauMai.DANG_KIEM_TRA,
            TrangThaiHauMai.DANG_XAC_MINH,
            TrangThaiHauMai.DANG_XU_LY);

    private final YeuCauHauMaiRepository yeuCauRepo;
    private final DonHangRepository donHangRepo;
    private final ThongBaoService thongBaoService;
    private final nhom10.phonenest.repository.KhachHangRepository khachHangRepo;
    private final nhom10.phonenest.repository.TaiKhoanRepository taiKhoanRepo;

    public HauMaiDto tao(String maKh, HauMaiDto.Request req) {
        // Khoá đơn hàng để hai yêu cầu cùng lúc không tạo trùng trong cùng giao dịch.
        DonHang don = req.maDonHang() == null || req.maDonHang().isBlank() ? null : donHangRepo.findByIdForUpdate(req.maDonHang())
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng của bạn"));
        String lyDo = req.lyDo().trim();
        if (lyDo.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng mô tả vấn đề");

        ChiTietDonHang dong = null;
        if (req.loai() == LoaiHauMai.BAO_HANH) {
            if (don == null) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn đơn hàng bảo hành");
            nhom10.phonenest.util.AfterSalesPolicy.require(don, true);
            dong = don.getChiTiet().stream()
                    .filter(c -> c.getPhienBan().getMaPhienBan().equals(req.maPhienBan()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm không thuộc đơn hàng"));
            if (yeuCauRepo.existsByDonHangMaDonHangAndChiTietDonHangMaChiTietAndLoaiAndTrangThaiIn(
                    don.getMaDonHang(), dong.getMaChiTiet(), LoaiHauMai.BAO_HANH, DANG_MO)) {
                throw new AppException(HttpStatus.CONFLICT, "Sản phẩm đã có yêu cầu bảo hành đang xử lý");
            }
        } else if (don != null) {
            if (yeuCauRepo.existsByDonHangMaDonHangAndLoaiAndTrangThaiIn(
                    don.getMaDonHang(), LoaiHauMai.KHIEU_NAI, DANG_MO)) {
                throw new AppException(HttpStatus.CONFLICT, "Đơn hàng đã có khiếu nại đang xử lý");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        YeuCauHauMai yeuCau = new YeuCauHauMai();
        yeuCau.setMaYeuCau(IdGenerator.next("HM"));
        yeuCau.setDonHang(don);
        yeuCau.setKhachHang(don != null ? don.getKhachHang() : khachHangRepo.findById(maKh)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng")));
        yeuCau.setChiTietDonHang(dong);
        yeuCau.setLoai(req.loai());
        yeuCau.setLyDo(lyDo);
        yeuCau.setHinhAnhMinhChung(req.hinhAnhMinhChung());
        yeuCau.setNgayTao(now);
        yeuCau.setNgayCapNhat(now);
        themLichSu(yeuCau, TrangThaiHauMai.CHO_TIEP_NHAN, "Khách hàng đã gửi yêu cầu", now);
        yeuCauRepo.save(yeuCau);
        thongBaoService.guiQuanTri("Có yêu cầu " + tenLoai(req.loai()) + " mới" + (don == null ? " (không gắn đơn hàng)" : " cho đơn " + don.getMaDonHang()));
        return toDto(yeuCau);
    }

    @Transactional(readOnly = true)
    public List<HauMaiDto> cuaToi(String maKh) {
        return yeuCauRepo.findByDonHangKhachHangMaKhOrderByNgayTaoDesc(maKh)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public HauMaiDto chiTietCuaToi(String maKh, String ma) {
        YeuCauHauMai y = yeuCauRepo.findById(ma)
                .filter(x -> owner(x).getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        return toDto(y);
    }

    @Transactional(readOnly = true)
    public List<HauMaiDto> tatCa() {
        return yeuCauRepo.findAllByOrderByNgayTaoDesc().stream().map(y -> toDto(y, true)).toList();
    }

    public HauMaiDto xuLy(String ma, HauMaiDto.XuLyRequest req) {
        YeuCauHauMai y = yeuCauRepo.findByIdForUpdate(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (!y.getTrangThai().coTheChuyenSang(y.getLoai(), req.trangThai())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể chuyển sang trạng thái này");
        }
        String ghiChu = req.ghiChuXuLy() == null ? null : req.ghiChuXuLy().trim();
        if (ghiChu == null || ghiChu.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập nội dung cập nhật");
        }
        String ketQua = req.ketQuaXuLy() == null ? null : req.ketQuaXuLy().trim();
        if (req.trangThai() == TrangThaiHauMai.HOAN_TAT && (ketQua == null || ketQua.isEmpty())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng ghi rõ kết quả xử lý trước khi hoàn tất");
        }
        if (y.getLoai() == LoaiHauMai.BAO_HANH && req.trangThai() == TrangThaiHauMai.HOAN_TAT
                && !nhom10.phonenest.util.WarrantyDevicePolicy.validResolution(ghiChu)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chọn kết quả sửa chữa, thay linh kiện hoặc đổi máy tương đương và ghi rõ xử lý thực tế trước khi hoàn tất");
        }
        LocalDateTime now = LocalDateTime.now();
        y.setTrangThai(req.trangThai());
        y.setGhiChuXuLy(ghiChu);
        if (req.trangThai() == TrangThaiHauMai.HOAN_TAT || req.trangThai() == TrangThaiHauMai.TU_CHOI) {
            y.setKetQuaXuLy(ketQua);
        }
        y.setNgayCapNhat(now);
        themLichSu(y, req.trangThai(), ghiChu, now);
        thongBaoService.gui(owner(y).getMaKh(),
                "Yêu cầu " + tenLoai(y.getLoai()) + " " + y.getMaYeuCau() + ": "
                        + tenTrangThai(y.getLoai(), req.trangThai()),
                ThongBaoService.linkYeuCau(y.getMaYeuCau()));
        return toDto(y);
    }

    private void themLichSu(YeuCauHauMai y, TrangThaiHauMai trangThai, String ghiChu, LocalDateTime thoiGian) {
        LichSuHauMai lichSu = new LichSuHauMai();
        lichSu.setMaLichSu(IdGenerator.next("LSHM"));
        lichSu.setYeuCau(y);
        lichSu.setTrangThai(trangThai);
        lichSu.setGhiChu(ghiChu);
        lichSu.setThoiGian(thoiGian);
        y.getLichSu().add(lichSu);
    }

    @Transactional(readOnly = true)
    public String layGhiChuNoiBo(String ma) {
        return yeuCauRepo.findById(ma).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu")).getGhiChuNoiBo();
    }

    public void luuGhiChuNoiBo(String ma, String ghiChu) {
        if (ghiChu != null && ghiChu.length() > 1000) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ghi chú nội bộ tối đa 1000 ký tự");
        }
        YeuCauHauMai y = yeuCauRepo.findByIdForUpdate(ma).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        y.setGhiChuNoiBo(ghiChu == null ? "" : ghiChu.trim());
    }

    private String tenLoai(LoaiHauMai loai) {
        return loai == LoaiHauMai.BAO_HANH ? "bảo hành" : "khiếu nại";
    }

    private String tenTrangThai(LoaiHauMai loai, TrangThaiHauMai trangThai) {
        return switch (trangThai) {
            case CHO_TIEP_NHAN -> "đang chờ tiếp nhận";
            case DANG_KIEM_TRA -> "đang kiểm tra bảo hành";
            case DANG_XAC_MINH -> "đang xác minh khiếu nại";
            case DANG_XU_LY -> loai == LoaiHauMai.BAO_HANH
                    ? "đang sửa chữa" : "đang thực hiện hướng giải quyết";
            case HOAN_TAT -> "đã hoàn tất";
            case TU_CHOI -> "bị từ chối";
        };
    }

    private nhom10.phonenest.entity.KhachHang owner(YeuCauHauMai y) {
        return y.getKhachHang() != null ? y.getKhachHang() : y.getDonHang().getKhachHang();
    }

    private HauMaiDto toDto(YeuCauHauMai y) { return toDto(y, false); }
    private HauMaiDto toDto(YeuCauHauMai y, boolean admin) {
        ChiTietDonHang dong = y.getChiTietDonHang();
        String phone = y.getDonHang() == null ? null : y.getDonHang().getSoDienThoaiNhan();
        if (admin && (phone == null || phone.isBlank())) phone = taiKhoanRepo.findById(owner(y).getMaKh()).map(nhom10.phonenest.entity.TaiKhoan::getSoDienThoai).orElse(null);
        return new HauMaiDto(y.getMaYeuCau(), y.getDonHang() == null ? null : y.getDonHang().getMaDonHang(),
                dong == null ? null : dong.getPhienBan().getMaPhienBan(),
                dong == null ? null : dong.getTenSanPham() + " - " + dong.getTenPhienBan(),
                owner(y).getHoTen(), phone, y.getLoai(), y.getLyDo(),
                y.getHinhAnhMinhChung(), y.getTrangThai(), y.getGhiChuXuLy(), y.getKetQuaXuLy(),
                y.getNgayTao(), y.getNgayCapNhat(),
                y.getLichSu().stream().map(l -> new HauMaiDto.LichSu(
                        l.getTrangThai(), l.getGhiChu(), l.getThoiGian())).toList());
    }
}
