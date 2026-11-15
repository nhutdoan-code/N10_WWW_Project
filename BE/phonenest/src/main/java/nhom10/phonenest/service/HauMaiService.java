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
            TrangThaiHauMai.CHO_TIEP_NHAN, TrangThaiHauMai.DANG_XU_LY);

    private final YeuCauHauMaiRepository yeuCauRepo;
    private final DonHangRepository donHangRepo;
    private final ThongBaoService thongBaoService;

    public HauMaiDto tao(String maKh, HauMaiDto.Request req) {
        // Khoá đơn hàng để hai yêu cầu cùng lúc không tạo trùng trong cùng giao dịch.
        DonHang don = donHangRepo.findByIdForUpdate(req.maDonHang())
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng của bạn"));
        String lyDo = req.lyDo().trim();
        if (lyDo.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng mô tả vấn đề");

        ChiTietDonHang dong = null;
        if (req.loai() == LoaiHauMai.BAO_HANH) {
            if (don.getTrangThai() != TrangThaiDonHang.DA_GIAO || don.getNgayGiao() == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ nhận bảo hành cho đơn đã giao có ngày giao xác định");
            }
            dong = don.getChiTiet().stream()
                    .filter(c -> c.getPhienBan().getMaPhienBan().equals(req.maPhienBan()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm không thuộc đơn hàng"));
            if (dong.getBaoHanhThang() <= 0 ||
                    LocalDateTime.now().isAfter(don.getNgayGiao().plusMonths(dong.getBaoHanhThang()))) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm đã hết thời hạn bảo hành");
            }
            if (yeuCauRepo.existsByDonHangMaDonHangAndChiTietDonHangMaChiTietAndLoaiAndTrangThaiIn(
                    don.getMaDonHang(), dong.getMaChiTiet(), LoaiHauMai.BAO_HANH, DANG_MO)) {
                throw new AppException(HttpStatus.CONFLICT, "Sản phẩm đã có yêu cầu bảo hành đang xử lý");
            }
        } else {
            // Không nhận khiếu nại khi đơn mới đặt, chưa qua giai đoạn giao hàng.
            // Khiếu nại trong vận chuyển, đã giao hoặc sau hủy vẫn được tiếp nhận.
            TrangThaiDonHang trangThai = don.getTrangThai();
            if (trangThai != TrangThaiDonHang.DANG_GIAO
                    && trangThai != TrangThaiDonHang.CHO_KHACH_XAC_NHAN
                    && trangThai != TrangThaiDonHang.DA_GIAO
                    && trangThai != TrangThaiDonHang.DA_HUY) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Đơn hàng chưa đến giai đoạn được gửi khiếu nại. Bạn có thể hủy đơn nếu không muốn tiếp tục mua.");
            }
            if (yeuCauRepo.existsByDonHangMaDonHangAndLoaiAndTrangThaiIn(
                    don.getMaDonHang(), LoaiHauMai.KHIEU_NAI, DANG_MO)) {
                throw new AppException(HttpStatus.CONFLICT, "Đơn hàng đã có khiếu nại đang xử lý");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        YeuCauHauMai yeuCau = new YeuCauHauMai();
        yeuCau.setMaYeuCau(IdGenerator.next("HM"));
        yeuCau.setDonHang(don);
        yeuCau.setChiTietDonHang(dong);
        yeuCau.setLoai(req.loai());
        yeuCau.setLyDo(lyDo);
        yeuCau.setHinhAnhMinhChung(req.hinhAnhMinhChung());
        yeuCau.setNgayTao(now);
        yeuCau.setNgayCapNhat(now);
        themLichSu(yeuCau, TrangThaiHauMai.CHO_TIEP_NHAN, "Khách hàng đã gửi yêu cầu", now);
        yeuCauRepo.save(yeuCau);
        thongBaoService.guiQuanTri("Có yêu cầu " + tenLoai(req.loai()) + " mới cho đơn " + don.getMaDonHang());
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
                .filter(x -> x.getDonHang().getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        return toDto(y);
    }

    @Transactional(readOnly = true)
    public List<HauMaiDto> tatCa() {
        return yeuCauRepo.findAllByOrderByNgayTaoDesc().stream().map(this::toDto).toList();
    }

    public HauMaiDto xuLy(String ma, HauMaiDto.XuLyRequest req) {
        YeuCauHauMai y = yeuCauRepo.findByIdForUpdate(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (!y.getTrangThai().coTheChuyenSang(req.trangThai())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể chuyển sang trạng thái này");
        }
        String ghiChu = req.ghiChuXuLy() == null ? null : req.ghiChuXuLy().trim();
        if (req.trangThai() == TrangThaiHauMai.TU_CHOI && (ghiChu == null || ghiChu.isEmpty())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Phải ghi rõ lý do từ chối");
        }
        LocalDateTime now = LocalDateTime.now();
        y.setTrangThai(req.trangThai());
        y.setGhiChuXuLy(ghiChu);
        y.setNgayCapNhat(now);
        themLichSu(y, req.trangThai(), ghiChu, now);
        thongBaoService.gui(y.getDonHang().getKhachHang().getMaKh(),
                "Yêu cầu " + tenLoai(y.getLoai()) + " " + y.getMaYeuCau() + ": " + req.trangThai().name());
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

    private HauMaiDto toDto(YeuCauHauMai y) {
        ChiTietDonHang dong = y.getChiTietDonHang();
        return new HauMaiDto(y.getMaYeuCau(), y.getDonHang().getMaDonHang(),
                dong == null ? null : dong.getPhienBan().getMaPhienBan(),
                dong == null ? null : dong.getTenSanPham() + " - " + dong.getTenPhienBan(),
                y.getDonHang().getKhachHang().getHoTen(), y.getDonHang().getSoDienThoaiNhan(), y.getLoai(), y.getLyDo(),
                y.getHinhAnhMinhChung(), y.getTrangThai(), y.getGhiChuXuLy(),
                y.getNgayTao(), y.getNgayCapNhat(),
                y.getLichSu().stream().map(l -> new HauMaiDto.LichSu(
                        l.getTrangThai(), l.getGhiChu(), l.getThoiGian())).toList());
    }
}
