package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.DoiTraDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class DoiTraService {
    private static final int HAN_DOI_TRA_NGAY = 7;

    private final YeuCauDoiTraRepository repo;
    private final DonHangRepository donRepo;
    private final ThongBaoService thongBao;
    private final NhatKyHoatDongService nhatKyHoatDongService;
    private final KhoService khoService;

    public DoiTraDto tao(String maKh, DoiTraDto.Request r) {
        DonHang don = donRepo.findByIdForUpdate(r.maDonHang())
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        if (don.getTrangThai() != TrangThaiDonHang.DA_GIAO) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ đơn đã giao mới được đổi trả");
        }
        LocalDateTime ngayGiao = don.getNgayGiao() != null ? don.getNgayGiao() : don.getNgayCapNhat();
        if (ngayGiao == null || LocalDateTime.now().isAfter(ngayGiao.plusDays(HAN_DOI_TRA_NGAY))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đã quá " + HAN_DOI_TRA_NGAY + " ngày đổi trả");
        }
        String lyDo = r.lyDo().trim();
        if (lyDo.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do đổi trả");

        // Payload cũ không có maPhienBan: giữ tương thích, yêu cầu toàn bộ đơn hàng.
        List<ChiTietDonHang> mucYeuCau;
        if (r.maPhienBan() == null || r.maPhienBan().isBlank()) {
            mucYeuCau = don.getChiTiet();
        } else {
            mucYeuCau = don.getChiTiet().stream()
                    .filter(c -> c.getPhienBan().getMaPhienBan().equals(r.maPhienBan()))
                    .toList();
            if (mucYeuCau.isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm không thuộc đơn hàng");
            }
        }
        if (mucYeuCau.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng không có sản phẩm");
        if (r.soLuong() != null && mucYeuCau.size() != 1) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn một sản phẩm khi nhập số lượng");
        }
        List<YeuCauDoiTra> daCo = repo.findByDonHangMaDonHangOrderByNgayTaoDesc(don.getMaDonHang());
        for (ChiTietDonHang c : mucYeuCau) {
            boolean trung = daCo.stream().filter(y -> y.getTrangThai() != TrangThaiDoiTra.TU_CHOI)
                    .flatMap(y -> y.getChiTiet().stream())
                    .anyMatch(ct -> ct.getChiTietDonHang().getMaChiTiet().equals(c.getMaChiTiet()));
            if (trung) {
                throw new AppException(HttpStatus.CONFLICT, "Sản phẩm đã có yêu cầu đổi trả; vui lòng theo dõi yêu cầu cũ");
            }
        }

        YeuCauDoiTra yeuCau = new YeuCauDoiTra();
        yeuCau.setMaYeuCau(IdGenerator.next("YC"));
        yeuCau.setDonHang(don);
        yeuCau.setLyDoChung(lyDo);
        yeuCau.setNgayTao(LocalDateTime.now());
        yeuCau.setTrangThai(TrangThaiDoiTra.CHO_DUYET);
        LoaiYeuCauDoiTra loai = r.loaiYeuCau() == null
                ? LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN : r.loaiYeuCau();

        for (ChiTietDonHang c : mucYeuCau) {
            int soLuong = r.soLuong() == null ? c.getSoLuong() : r.soLuong();
            if (soLuong <= 0 || soLuong > c.getSoLuong()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng đổi trả không hợp lệ");
            }
            ChiTietDoiTra ct = new ChiTietDoiTra();
            ct.setMaChiTietDoiTra(IdGenerator.next("CTDT"));
            ct.setYeuCau(yeuCau);
            ct.setChiTietDonHang(c);
            ct.setSoLuong(soLuong);
            ct.setLoaiYeuCau(loai);
            ct.setLyDo(lyDo);
            ct.setHinhAnhMinhChung(r.hinhAnhMinhChung());
            yeuCau.getChiTiet().add(ct);
        }
        repo.save(yeuCau);
        thongBao.guiQuanTri("Có yêu cầu đổi trả mới cho đơn " + don.getMaDonHang());
        return dto(yeuCau);
    }

    @Transactional(readOnly = true)
    public List<DoiTraDto> cuaToi(String maKh) {
        return donRepo.findByKhachHangMaKhOrderByNgayMuaDesc(maKh).stream()
                .flatMap(d -> repo.findByDonHangMaDonHangOrderByNgayTaoDesc(d.getMaDonHang()).stream())
                .sorted(Comparator.comparing(YeuCauDoiTra::getNgayTao).reversed())
                .map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<DoiTraDto> tatCa() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing(YeuCauDoiTra::getNgayTao).reversed())
                .map(this::dto).toList();
    }

    public DoiTraDto capNhat(String ma, TrangThaiDoiTra moi) {
        return capNhat(ma, moi, null);
    }

    public DoiTraDto capNhat(String ma, TrangThaiDoiTra moi, String ghiChuXuLy) {
        YeuCauDoiTra yeuCau = repo.findByIdForUpdate(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        boolean hopLe = switch (yeuCau.getTrangThai()) {
            case CHO_DUYET -> moi == TrangThaiDoiTra.DA_DUYET || moi == TrangThaiDoiTra.TU_CHOI;
            case DA_DUYET -> moi == TrangThaiDoiTra.DANG_XU_LY || moi == TrangThaiDoiTra.HOAN_TAT;
            case DANG_XU_LY -> moi == TrangThaiDoiTra.HOAN_TAT;
            default -> false;
        };
        if (!hopLe) throw new AppException(HttpStatus.BAD_REQUEST, "Không thể chuyển trạng thái này");

        String ghiChu = ghiChuXuLy == null ? null : ghiChuXuLy.trim();
        if (ghiChu != null && ghiChu.length() > 500) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ghi chú tối đa 500 ký tự");
        }
        if (moi == TrangThaiDoiTra.TU_CHOI && (ghiChu == null || ghiChu.isEmpty())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối");
        }
        Map<String, Object> truoc = snapshot(yeuCau);
        if (moi == TrangThaiDoiTra.HOAN_TAT) {
            boolean hasReturn = yeuCau.getChiTiet().stream()
                    .anyMatch(ct -> ct.getLoaiYeuCau() == LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN);
            if (yeuCau.getChiTiet().stream().anyMatch(ct -> !ct.isDaNhapKho())) {
                throw new AppException(HttpStatus.CONFLICT,
                        "Cần xác nhận nhận và nhập kho sản phẩm trước khi hoàn tất yêu cầu");
            }
            if (hasReturn && yeuCau.getNgayHoanTien() == null) {
                throw new AppException(HttpStatus.CONFLICT,
                        "Cần ghi nhận giao dịch hoàn tiền trước khi hoàn tất yêu cầu");
            }
        }
        yeuCau.setTrangThai(moi);
        yeuCau.setNgayCapNhat(java.time.LocalDateTime.now());
        if (ghiChu != null && !ghiChu.isEmpty()) yeuCau.setGhiChuXuLy(ghiChu);
        thongBao.gui(yeuCau.getDonHang().getKhachHang().getMaKh(),
                "Yêu cầu đổi trả đơn " + yeuCau.getDonHang().getMaDonHang() + ": " + moi.name());
        nhatKyHoatDongService.ghi(HanhDongAudit.CHANGE_RETURN_STATUS,
                LoaiDoiTuongAudit.RETURN, ma, truoc, snapshot(yeuCau));
        return dto(yeuCau);
    }

    @Transactional(readOnly = true)
    public String layGhiChuNoiBo(String ma) {
        return repo.findById(ma).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                "Không tìm thấy yêu cầu")).getGhiChuNoiBo();
    }

    public void luuGhiChuNoiBo(String ma, String ghiChu) {
        if (ghiChu != null && ghiChu.length() > 1000) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ghi chú nội bộ tối đa 1000 ký tự");
        }
        YeuCauDoiTra y = repo.findByIdForUpdate(ma).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        y.setGhiChuNoiBo(ghiChu == null ? "" : ghiChu.trim());
    }

    public DoiTraDto xacNhanNhapKho(String ma, String adminId) {
        YeuCauDoiTra y = repo.findByIdForUpdate(ma).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (y.getTrangThai() != TrangThaiDoiTra.DA_DUYET && y.getTrangThai() != TrangThaiDoiTra.DANG_XU_LY) {
            throw new AppException(HttpStatus.CONFLICT, "Chỉ nhập kho cho yêu cầu đã duyệt và đang xử lý");
        }
        if (y.getChiTiet().isEmpty() || y.getChiTiet().stream().anyMatch(ChiTietDoiTra::isDaNhapKho)) {
            throw new AppException(HttpStatus.CONFLICT, "Yêu cầu đã được nhập kho hoặc không có sản phẩm");
        }
        LocalDateTime now = LocalDateTime.now();
        for (ChiTietDoiTra ct : y.getChiTiet()) {
            PhienBanSanPham variant = ct.getChiTietDonHang().getPhienBan();
            khoService.thayDoiTonDoiTra(variant, ct.getSoLuong(), y, adminId,
                    "Nhận hàng đổi trả " + y.getMaYeuCau());
            ct.setDaNhapKho(true);
            ct.setNgayNhapKho(now);
        }
        return dto(y);
    }

    public DoiTraDto ghiNhanHoanTien(String ma, DoiTraDto.XacNhanHoanTienRequest request) {
        YeuCauDoiTra y = repo.findByIdForUpdate(ma).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (y.getTrangThai() != TrangThaiDoiTra.DANG_XU_LY) {
            throw new AppException(HttpStatus.CONFLICT, "Chỉ ghi nhận hoàn tiền khi yêu cầu đang xử lý");
        }
        if (y.getNgayHoanTien() != null) {
            throw new AppException(HttpStatus.CONFLICT, "Yêu cầu này đã được ghi nhận hoàn tiền");
        }
        if (y.getChiTiet().isEmpty() || y.getChiTiet().stream()
                .anyMatch(ct -> ct.getLoaiYeuCau() != LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Yêu cầu không phải trả hàng hoàn tiền");
        }
        if (y.getChiTiet().stream().anyMatch(ct -> !ct.isDaNhapKho())) {
            throw new AppException(HttpStatus.CONFLICT, "Xác nhận đã nhận và nhập kho sản phẩm trước khi ghi nhận hoàn tiền");
        }
        String method = request.hinhThuc().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MOMO", "CHUYEN_KHOAN", "TIEN_MAT", "KHAC").contains(method)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Hình thức hoàn tiền không hợp lệ");
        }
        tinhSoTienHoanDuKien(y);
        if (tongTienHoan(y).signum() <= 0) {
            throw new AppException(HttpStatus.CONFLICT, "Số tiền hoàn dự kiến phải lớn hơn 0");
        }
        Map<String, Object> truoc = snapshot(y);
        y.setHinhThucHoanTien(method);
        y.setMaGiaoDichHoanTien(request.maGiaoDich().trim());
        y.setNgayHoanTien(LocalDateTime.now());
        nhatKyHoatDongService.ghi(HanhDongAudit.CHANGE_RETURN_STATUS,
                LoaiDoiTuongAudit.RETURN, ma, truoc, snapshot(y));
        return dto(y);
    }

    // Chỉ tính số tiền dự kiến được hoàn, KHÔNG thực hiện chuyển tiền qua cổng thanh toán.
    private void tinhSoTienHoanDuKien(YeuCauDoiTra y) {
        DonHang don = y.getDonHang();
        BigDecimal tam = don.getTamTinh() == null ? BigDecimal.ZERO : don.getTamTinh();
        BigDecimal giam = don.getGiamGia() == null ? BigDecimal.ZERO : don.getGiamGia();
        for (ChiTietDoiTra ct : y.getChiTiet()) {
            if (ct.getLoaiYeuCau() != LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN) continue;
            BigDecimal giaTri = ct.getChiTietDonHang().getDonGia()
                    .multiply(BigDecimal.valueOf(ct.getSoLuong()));
            BigDecimal phanBo = tam.signum() == 0 ? BigDecimal.ZERO
                    : giam.multiply(giaTri).divide(tam, 0, RoundingMode.FLOOR);
            ct.setSoTienHoan(giaTri.subtract(phanBo).max(BigDecimal.ZERO));
        }
    }

    private BigDecimal tongTienHoan(YeuCauDoiTra y) {
        return y.getChiTiet().stream().map(ChiTietDoiTra::getSoTienHoan)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> snapshot(YeuCauDoiTra y) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("trangThai", y.getTrangThai().name());
        duLieu.put("hinhThucHoanTien", y.getHinhThucHoanTien());
        duLieu.put("maGiaoDichHoanTien", y.getMaGiaoDichHoanTien());
        return duLieu;
    }

    private DoiTraDto dto(YeuCauDoiTra y) {
        ChiTietDoiTra ct = y.getChiTiet().isEmpty() ? null : y.getChiTiet().get(0);
        String img = y.getChiTiet().stream().map(ChiTietDoiTra::getHinhAnhMinhChung)
                .filter(Objects::nonNull).findFirst().orElse(null);
        String tenSanPham = ct == null ? null : ct.getChiTietDonHang().getTenSanPham()
                + " - " + ct.getChiTietDonHang().getTenPhienBan();
        int soLuong = y.getChiTiet().stream().mapToInt(ChiTietDoiTra::getSoLuong).sum();
        List<DoiTraDto.ChiTietDto> items = y.getChiTiet().stream().map(item ->
                new DoiTraDto.ChiTietDto(item.getChiTietDonHang().getPhienBan().getMaPhienBan(),
                        item.getChiTietDonHang().getTenSanPham() + " - " + item.getChiTietDonHang().getTenPhienBan(),
                        item.getSoLuong(), item.getLoaiYeuCau(), item.isDaNhapKho(), item.getNgayNhapKho(),
                        item.getSoTienHoan())).toList();
        return new DoiTraDto(y.getMaYeuCau(), y.getDonHang().getMaDonHang(),
                y.getDonHang().getKhachHang().getHoTen(), y.getDonHang().getSoDienThoaiNhan(), y.getLyDoChung(), img,
                y.getNgayTao().toLocalDate(), y.getNgayCapNhat(), y.getTrangThai(),
                ct == null ? null : ct.getLoaiYeuCau(), tenSanPham,
                soLuong, y.getGhiChuXuLy(), items,
                y.getChiTiet().stream().anyMatch(item -> item.getLoaiYeuCau() == LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN)
                        ? (tongTienHoan(y).signum() > 0 ? tongTienHoan(y) : tinhTongHoanDuKien(y)) : null,
                y.getHinhThucHoanTien(), y.getMaGiaoDichHoanTien(), y.getNgayHoanTien(),
                !items.isEmpty() && items.stream().allMatch(DoiTraDto.ChiTietDto::daNhapKho));
    }

    private BigDecimal tinhTongHoanDuKien(YeuCauDoiTra y) {
        DonHang don = y.getDonHang();
        BigDecimal tam = don.getTamTinh() == null ? BigDecimal.ZERO : don.getTamTinh();
        BigDecimal giam = don.getGiamGia() == null ? BigDecimal.ZERO : don.getGiamGia();
        return y.getChiTiet().stream().filter(item -> item.getLoaiYeuCau() == LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN)
                .map(item -> {
                    BigDecimal giaTri = item.getChiTietDonHang().getDonGia().multiply(BigDecimal.valueOf(item.getSoLuong()));
                    BigDecimal phanBo = tam.signum() == 0 ? BigDecimal.ZERO : giam.multiply(giaTri).divide(tam, 0, RoundingMode.FLOOR);
                    return giaTri.subtract(phanBo).max(BigDecimal.ZERO);
                }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
