package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.*;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DonHangService {

    private static final int DIEM_MOI_VND = 10000;
    private static final String LY_DO_KHAC = "Lý do khác";
    private record PaymentConfirmation(
            BigDecimal amount,
            String paymentReference,
            String providerTransactionId,
            LocalDateTime vnPayCreateDate) {
    }

    private static final List<String> LY_DO_HUY = List.of(
            "Muốn thay đổi địa chỉ giao hàng",
            "Muốn nhập / thay đổi mã khuyến mãi",
            "Muốn thay đổi sản phẩm trong đơn (màu sắc, dung lượng, số lượng...)",
            "Thủ tục thanh toán quá rắc rối",
            "Tìm thấy giá tốt hơn ở nơi khác",
            "Đổi ý, không muốn mua nữa",
            LY_DO_KHAC
    );

    private final DonHangRepository donHangRepository;
    private final GioHangRepository gioHangRepository;
    private final PhienBanSanPhamRepository phienBanRepository;
    private final KhuyenMaiRepository khuyenMaiRepository;
    private final HuyDonHangRepository huyRepository;
    private final ThanhToanRepository thanhToanRepository;
    private final LichSuTrangThaiDonHangRepository lichSuRepository;
    private final BienDongKhoRepository bienDongRepository;
    private final KhuyenMaiService khuyenMaiService;
    private final DiaChiService diaChiService;
    private final PhiVanChuyenService phiVanChuyenService;
    private final ThanhToanService thanhToanService;
    private final KhachHangRepository khachHangRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final DiaChiRepository diaChiRepository;
    private final DonHangMapper mapper;
    private final GioHangService gioHangService;
    private final ThongBaoService thongBaoService;
    private final EmailService emailService;
    private final NhatKyHoatDongService nhatKyHoatDongService;

    @Autowired(required = false)
    private CheckoutIdempotencyRepository checkoutIdempotencyRepository;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private HanhViNguoiDungService hanhViNguoiDungService;

    public DonHangDto datHang(String maKh, DonHangDto.Checkout req) {
        return datHang(maKh, req, null);
    }

    public DonHangDto datHang(String maKh, DonHangDto.Checkout req, Long idempotencyRecordId) {
        if (req.phuongThucTt() != PhuongThucTt.COD) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Thanh toán online phải thành công trước khi tạo đơn hàng");
        }
        return datHangInternal(maKh, req, idempotencyRecordId, null, null);
    }

    public DonHangDto datHangDaThanhToan(
            String maKh,
            DonHangDto.Checkout req,
            Map<String, Integer> items,
            BigDecimal paidAmount,
            String paymentReference,
            String providerTransactionId,
            PhuongThucTt paymentMethod) {
        return datHangDaThanhToan(
                maKh, req, items, paidAmount, paymentReference, providerTransactionId, paymentMethod, null);
    }

    public DonHangDto datHangDaThanhToan(
            String maKh,
            DonHangDto.Checkout req,
            Map<String, Integer> items,
            BigDecimal paidAmount,
            String paymentReference,
            String providerTransactionId,
            PhuongThucTt paymentMethod,
            LocalDateTime vnPayCreateDate) {
        if (req.phuongThucTt() != paymentMethod
                || (paymentMethod != PhuongThucTt.VI_DIEN_TU
                && paymentMethod != PhuongThucTt.CHUYEN_KHOAN)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Phương thức thanh toán phiên không hợp lệ");
        }
        return datHangInternal(
                maKh,
                req,
                null,
                items,
                new PaymentConfirmation(paidAmount, paymentReference, providerTransactionId, vnPayCreateDate));
    }

    private DonHangDto datHangInternal(
            String maKh,
            DonHangDto.Checkout req,
            Long idempotencyRecordId,
            Map<String, Integer> fixedItems,
            PaymentConfirmation paymentConfirmation) {
        KhachHang kh = khachHangRepository.findById(maKh).orElseThrow();
        boolean muaNgay = req.maSanPham() != null && !req.maSanPham().isBlank();
        Map<String, Integer> muc = fixedItems != null
                ? new LinkedHashMap<>(fixedItems)
                : thanhToanService.mucMua(maKh, req.maSanPham(), req.soLuong());
        if (muc.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không có sản phẩm để tạo đơn hàng");
        }
        DiaChi dc = diaChiRepository.findById(req.maDiaChi()).filter(x -> x.getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Địa chỉ giao hàng không hợp lệ"));

        DonHang d = new DonHang();
        d.setMaDonHang(IdGenerator.next("DH"));
        d.setKhachHang(kh);
        d.setNgayMua(LocalDateTime.now());
        d.setNgayCapNhat(d.getNgayMua());
        d.setTrangThai(TrangThaiDonHang.CHO_XAC_NHAN);
        d.setTenNguoiNhan(dc.getTenNguoiNhan());
        d.setSoDienThoaiNhan(dc.getSoDienThoaiNhan());
        d.setDiaChiGiaoHang(diaChiService.diaChiDayDu(dc));

        // 1. Khóa bi quan các phiên bản sản phẩm theo thứ tự maPhienBan tăng dần để chống overselling và chống deadlock
        List<String> sortedVariantIds = muc.keySet().stream().sorted().toList();
        Map<String, PhienBanSanPham> lockedVariants = new LinkedHashMap<>();
        for (String pbId : sortedVariantIds) {
            PhienBanSanPham p = phienBanRepository.findByIdForUpdate(pbId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản: " + pbId));
            lockedVariants.put(pbId, p);
        }

        BigDecimal tam = BigDecimal.ZERO;
        for (var e : muc.entrySet()) {
            PhienBanSanPham p = lockedVariants.get(e.getKey());
            if (p.getSanPham() == null || p.getSanPham().getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH
                    || p.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH) {
                String ten = p.getSanPham() != null ? p.getSanPham().getTenMay() : "Sản phẩm";
                throw new AppException(HttpStatus.BAD_REQUEST, ten + " không còn kinh doanh");
            }
            PheDuyetBanHangPolicy.kiemTraDuocBan(p, null);
            if (e.getValue() > p.getSoLuongTon()) {
                throw new AppException(HttpStatus.BAD_REQUEST, p.getSanPham().getTenMay() + " không đủ hàng");
            }
            ChiTietDonHang c = new ChiTietDonHang();
            c.setMaChiTiet(IdGenerator.next("CTDH"));
            c.setDonHang(d);
            c.setPhienBan(p);
            c.setTenSanPham(p.getSanPham().getTenMay());
            c.setTenPhienBan(p.getRam() + "GB/" + nhom10.phonenest.util.DungLuongUtil.format(p.getBoNho()) + "/" + p.getMauSac());
            c.setSku(p.getSku());
            c.setHinhAnh(SanPhamMapper.anhTheoMau(p.getSanPham(), p));
            c.setSoLuong(e.getValue());
            c.setDonGia(p.getGiaBan());
            c.setBaoHanhThang(p.getSanPham().getThoiHanBaoHanhThang());
            d.getChiTiet().add(c);
            tam = tam.add(p.getGiaBan().multiply(BigDecimal.valueOf(e.getValue())));
        }
        d.setTamTinh(tam);

        KetQuaPhiVanChuyen vc = phiVanChuyenService.tinhChoDiaChi(dc, tam, req.phuongThucTt(), d.getNgayMua());
        BigDecimal phi = vc.tongPhi();
        d.setPhiVanChuyen(phi);
        d.setPhiKhaiGia(vc.phiKhaiGia());
        d.setPhuPhiCod(vc.phuPhiCod());
        d.setNgayGiaoDuKienTu(vc.ngayGiaoDuKienTu());
        d.setNgayGiaoDuKienDen(vc.ngayGiaoDuKienDen());

        boolean moi = !donHangRepository.existsByKhachHangMaKhAndTrangThaiNot(maKh, TrangThaiDonHang.DA_HUY);

        // Voucher cardinality & mutual exclusion
        if (coMa(req.maKm()) && coMa(req.maKmVanChuyen()) && req.maKm().trim().equalsIgnoreCase(req.maKmVanChuyen().trim())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể dùng cùng một mã khuyến mãi cho cả đơn hàng và vận chuyển");
        }

        // 2. Khóa bi quan các khuyến mãi theo thứ tự bảng chữ cái để chống overselling và chống deadlock
        List<String> sortedVoucherCodes = Stream.of(req.maKm(), req.maKmVanChuyen())
                .filter(this::coMa)
                .map(String::trim)
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        Map<String, KhuyenMai> lockedVouchers = new LinkedHashMap<>();
        for (String code : sortedVoucherCodes) {
            KhuyenMai k = khuyenMaiRepository.findByIdForUpdate(code)
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Mã khuyến mãi " + code + " không hợp lệ hoặc không đúng loại"));
            lockedVouchers.put(code, k);
            lockedVouchers.put(k.getMaKm(), k);
        }

        if (coMa(req.maKm())) {
            KhuyenMai k = dungVoucher(lockedVouchers, req.maKm(), LoaiKhuyenMai.DON_HANG, tam, moi, maKh);
            d.setKhuyenMai(k);
            d.setGiamGia(khuyenMaiService.tinhGiam(k, tam));
        }

        if (coMa(req.maKmVanChuyen())) {
            KhuyenMai k = dungVoucher(lockedVouchers, req.maKmVanChuyen(), LoaiKhuyenMai.VAN_CHUYEN, tam, moi, maKh);
            String lyDo = ThanhToanService.lyDoKhongGiamPhi(vc);
            if (lyDo != null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Không áp dụng được mã " + k.getMaKm() + ": " + lyDo);
            }
            d.setKhuyenMaiVanChuyen(k);
            // Chỉ giảm phí giao hàng theo vùng; phí khai giá và phụ phí COD không được giảm.
            d.setGiamPhiVanChuyen(khuyenMaiService.tinhGiam(k, vc.phiDuocGiam()));
        }

        BigDecimal giamGia = d.getGiamGia() != null ? d.getGiamGia() : BigDecimal.ZERO;
        BigDecimal giamShip = d.getGiamPhiVanChuyen() != null ? d.getGiamPhiVanChuyen() : BigDecimal.ZERO;
        BigDecimal tongThanhToan = tam.subtract(giamGia).add(phi).subtract(giamShip);
        if (tongThanhToan.compareTo(BigDecimal.ZERO) < 0) {
            tongThanhToan = BigDecimal.ZERO;
        }
        d.setTongThanhToan(tongThanhToan);
        if (paymentConfirmation != null && tongThanhToan.compareTo(paymentConfirmation.amount()) != 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Giá hoặc ưu đãi đã thay đổi sau khi thanh toán; giao dịch cần được hoàn tiền");
        }

        DonHang luu = donHangRepository.save(d);

        if (luu.getKhuyenMai() != null) {
            khuyenMaiService.ghiNhanSuDung(
                    luu.getKhuyenMai(),
                    maKh,
                    luu.getMaDonHang()
            );
        }

        if (luu.getKhuyenMaiVanChuyen() != null) {
            khuyenMaiService.ghiNhanSuDung(
                    luu.getKhuyenMaiVanChuyen(),
                    maKh,
                    luu.getMaDonHang()
            );
        }

        doiTon(luu, -1, LoaiBienDongKho.BAN_HANG, lockedVariants);
        taoThanhToan(luu, req.phuongThucTt(), paymentConfirmation);
        ghiTrangThai(
                luu,
                luu.getTrangThai(),
                paymentConfirmation != null
                        ? "Thanh toán thành công, đơn hàng chờ xác nhận"
                        : "Khách hàng đặt đơn"
        );

        if (!muaNgay) {
            if (paymentConfirmation == null) {
                gioHangRepository.findByKhachHangMaKh(maKh).ifPresent(g -> g.getChiTiet().clear());
            } else {
                xoaDaMuaKhoiGio(maKh, muc);
            }
        }

        ghiNhanHanhViMuaHang(luu);

        DonHangDto dto = mapper.toDto(luu);

        if (idempotencyRecordId != null && checkoutIdempotencyRepository != null) {
            String jsonPayload = null;
            if (objectMapper != null) {
                try {
                    jsonPayload = objectMapper.writeValueAsString(dto);
                } catch (Exception e) {
                    log.warn("Không thể serialize DonHangDto để lưu cache idempotency: {}", e.getMessage());
                }
            }
            checkoutIdempotencyRepository.markSuccessInCurrentTx(
                    idempotencyRecordId,
                    luu.getMaDonHang(),
                    jsonPayload,
                    LocalDateTime.now(),
                    IdempotencyStatus.SUCCESS,
                    IdempotencyStatus.IN_PROGRESS
            );
        }
        guiThongBaoSauCommit(
                maKh, luu.getMaDonHang(), dto.tongTien(), req.phuongThucTt(), paymentConfirmation != null);
        return dto;
    }

    public List<DonHangDto> lichSu(String maKh) {
        return donHangRepository.findByKhachHangMaKhOrderByNgayMuaDesc(maKh).stream().map(mapper::toDto).toList();
    }

    public DonHangDto chiTiet(String maKh, String ma) {
        return mapper.toDto(donCuaToi(maKh, ma));
    }

    public List<DonHangDto> tatCa() {
        return donHangRepository.findAllVisibleToAdmin().stream().map(mapper::toDto).toList();
    }

    public DonHangDto chiTietQuanTri(String ma) {
        return mapper.toDto(tim(ma));
    }

    public void xacNhanSauThanhToan(DonHang donHang) {
        if (donHang.getTrangThai() != TrangThaiDonHang.CHO_THANH_TOAN) {
            return;
        }
        donHang.setTrangThai(TrangThaiDonHang.CHO_XAC_NHAN);
        donHang.setNgayCapNhat(LocalDateTime.now());
        donHangRepository.save(donHang);
        ghiTrangThai(donHang, TrangThaiDonHang.CHO_XAC_NHAN, "Thanh toán thành công, chờ xác nhận");
    }

    /**
     * 2B.7E: don bi huy nhung da co giao dich thanh toan thanh cong (MoMo) -> danh dau CAN_HOAN_TIEN va
     * bao Admin, khong de sot hoan tien. Chay khi da giu khoa dong DonHang (moi cap nhat thanh toan deu khoa
     * DonHang truoc) nen khong tranh chap voi IPN.
     */
    private void danhDauHoanTienKhiHuy(DonHang d) {
        for (ThanhToan t : thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(d.getMaDonHang())) {
            if (t.getTrangThai() == TrangThaiThanhToan.DA_THANH_TOAN) {
                t.setTrangThai(TrangThaiThanhToan.CAN_HOAN_TIEN);
                thanhToanRepository.save(t);
                thongBaoService.guiQuanTri("Đơn hàng " + d.getMaDonHang() + " đã bị hủy sau khi khách thanh toán ("
                        + t.getSoTien() + " VND, mã GD: " + t.getMaGiaoDich() + "). Cần hoàn tiền cho khách hàng.");
            }
        }
    }

    public DonHangDto huy(String maKh, String ma, DonHangDto.HuyRequest req) {
        DonHang d = timKhoa(ma);
        if (!d.getKhachHang().getMaKh().equals(maKh)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng");
        }
        if (d.getTrangThai() == TrangThaiDonHang.DA_HUY) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng đã được hủy trước đó");
        }
        if (d.getTrangThai() != TrangThaiDonHang.CHO_THANH_TOAN
                && d.getTrangThai() != TrangThaiDonHang.CHO_XAC_NHAN
                && d.getTrangThai() != TrangThaiDonHang.DA_XAC_NHAN
                && d.getTrangThai() != TrangThaiDonHang.DANG_CHUAN_BI) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng đã bàn giao vận chuyển, không thể hủy");
        }
        kiemTraLyDoHuy(req);
        return hoanTatHuy(d, req, maKh, false);
    }

    public DonHangDto huyQuanTri(String ma, DonHangDto.HuyRequest req) {
        kiemTraLyDoHuy(req);
        DonHang d = timKhoa(ma);
        if (!d.getTrangThai().coTheChuyenSang(TrangThaiDonHang.DA_HUY)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Không thể hủy đơn ở trạng thái " + d.getTrangThai());
        }
        return hoanTatHuy(d, req, SecurityUtil.currentUserId(), true);
    }

    private void kiemTraLyDoHuy(DonHangDto.HuyRequest req) {
        if (!LY_DO_HUY.contains(req.lyDo())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Lý do hủy đơn không hợp lệ");
        }
        if (LY_DO_KHAC.equals(req.lyDo()) && (req.ghiChu() == null || req.ghiChu().isBlank())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do hủy khác");
        }
    }

    private DonHangDto hoanTatHuy(DonHang d, DonHangDto.HuyRequest req, String nguoiHuy, boolean quanTri) {
        TrangThaiDonHang truoc = d.getTrangThai();
        doiTon(d, 1, LoaiBienDongKho.HOAN_HUY_DON);
        List<KhuyenMai> vouchersToRefund = Stream.of(d.getKhuyenMai(), d.getKhuyenMaiVanChuyen())
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(KhuyenMai::getMaKm, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (KhuyenMai km : vouchersToRefund) {
            khuyenMaiService.hoanLuot(km, d.getMaDonHang());
        }
        danhDauHoanTienKhiHuy(d);

        LocalDateTime now = LocalDateTime.now();
        d.setTrangThai(TrangThaiDonHang.DA_HUY);
        d.setNgayCapNhat(now);
        HuyDonHang h = new HuyDonHang();
        h.setMaDonHang(d.getMaDonHang());
        h.setLyDo(req.lyDo());
        h.setGhiChu(req.ghiChu());
        h.setNgayHuy(now);
        h.setNguoiHuy(nguoiHuy);
        huyRepository.save(h);

        String ghiChuTrangThai = quanTri ? "Quản trị hủy đơn: " + req.lyDo() : req.lyDo();
        if (nguoiHuy == null) {
            ghiTrangThai(d, TrangThaiDonHang.DA_HUY, ghiChuTrangThai);
        } else {
            ghiTrangThai(d, TrangThaiDonHang.DA_HUY, ghiChuTrangThai, nguoiHuy);
        }
        if (quanTri) {
            auditTrangThai(d.getMaDonHang(), truoc, TrangThaiDonHang.DA_HUY);
            thongBaoService.gui(d.getKhachHang().getMaKh(),
                    "Đơn hàng " + d.getMaDonHang() + " đã bị hủy. Lý do: " + req.lyDo(),
                    ThongBaoService.linkDonHang(d.getMaDonHang()));
        }
        return mapper.toDto(d);
    }

    public DonHangDto capNhatTrangThai(String ma, TrangThaiDonHang moi) {
        DonHang d = timKhoa(ma);
        TrangThaiDonHang truoc = d.getTrangThai();

        if (truoc == TrangThaiDonHang.DA_GIAO
                || truoc == TrangThaiDonHang.DA_HUY) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Đơn hàng đã kết thúc"
            );
        }

        if (truoc == TrangThaiDonHang.CHO_KHACH_XAC_NHAN) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Đơn đã giao, đang chờ khách hàng xác nhận nhận hàng"
            );
        }

        if (moi == TrangThaiDonHang.DA_GIAO) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ khách hàng mới xác nhận hoàn thành đơn hàng"
            );
        }

        if (moi == TrangThaiDonHang.CHO_KHACH_XAC_NHAN) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng dùng chức năng Xác nhận đã giao hàng"
            );
        }

        // Giữ state-machine validation của branch Doan
        if (!truoc.coTheChuyenSang(moi)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Không thể chuyển trạng thái đơn hàng từ "
                            + truoc + " sang " + moi
            );
        }

        if (moi == TrangThaiDonHang.DA_HUY) {
            doiTon(d, 1, LoaiBienDongKho.HOAN_HUY_DON);
            List<KhuyenMai> vouchersToRefund = Stream.of(d.getKhuyenMai(), d.getKhuyenMaiVanChuyen())
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(KhuyenMai::getMaKm, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            for (KhuyenMai km : vouchersToRefund) {
                khuyenMaiService.hoanLuot(km, d.getMaDonHang());
            }
            danhDauHoanTienKhiHuy(d);
            HuyDonHang huy = new HuyDonHang();
            huy.setMaDonHang(ma);
            huy.setLyDo("Quản trị hủy đơn");
            huy.setGhiChu("Hủy qua thao tác cập nhật trạng thái");
            huy.setNgayHuy(LocalDateTime.now());
            huy.setNguoiHuy(SecurityUtil.currentUserIdOrNull());
            huyRepository.save(huy);
        }

        d.setTrangThai(moi);
        d.setNgayCapNhat(LocalDateTime.now());

        ghiTrangThai(
                d,
                moi,
                "Quản trị cập nhật trạng thái",
                SecurityUtil.currentUserId()
        );

        auditTrangThai(ma, truoc, moi);

        thongBaoService.gui(
                d.getKhachHang().getMaKh(),
                "Đơn hàng " + ma + " chuyển sang trạng thái: " + moi.name(),
                ThongBaoService.linkDonHang(ma)
        );

        return mapper.toDto(d);
    }
    public DonHangDto danhDauDaGiao(String ma) {
        DonHang d = timKhoa(ma);

        if (d.getTrangThai() != TrangThaiDonHang.DANG_GIAO) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Chỉ đơn đang giao mới được xác nhận đã giao hàng"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        d.setTrangThai(TrangThaiDonHang.CHO_KHACH_XAC_NHAN);
        d.setNgayGiao(now);
        d.setNgayCapNhat(now);

        ghiTrangThai(
                d,
                TrangThaiDonHang.CHO_KHACH_XAC_NHAN,
                "Quản trị xác nhận đã giao hàng, chờ khách xác nhận",
                SecurityUtil.currentUserId()
        );

        auditTrangThai(
                ma,
                TrangThaiDonHang.DANG_GIAO,
                TrangThaiDonHang.CHO_KHACH_XAC_NHAN
        );

        thongBaoService.gui(
                d.getKhachHang().getMaKh(),
                "Đơn hàng " + ma
                        + " đã được giao. Vui lòng xác nhận khi bạn đã nhận được hàng.",
                ThongBaoService.linkDonHang(ma)
        );

        return mapper.toDto(d);
    }

    public DonHangDto khachXacNhanDaNhan(String maKh, String ma) {
        DonHang d = timKhoa(ma);

        if (!d.getKhachHang().getMaKh().equals(maKh)) {
            throw new AppException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy đơn hàng"
            );
        }

        if (d.getTrangThai() != TrangThaiDonHang.CHO_KHACH_XAC_NHAN) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng không ở trạng thái chờ xác nhận nhận hàng hoặc đã được xác nhận trước đó"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        d.setTrangThai(TrangThaiDonHang.DA_GIAO);
        d.setNgayKhachXacNhan(now);
        d.setNguoiXacNhan(maKh);
        d.setNgayCapNhat(now);

        congDiem(d);

        ghiTrangThai(
                d,
                TrangThaiDonHang.DA_GIAO,
                "Khách hàng xác nhận đã nhận hàng",
                maKh
        );

        auditTrangThai(
                ma,
                TrangThaiDonHang.CHO_KHACH_XAC_NHAN,
                TrangThaiDonHang.DA_GIAO
        );

        thongBaoService.gui(
                maKh,
                "Đơn hàng " + ma
                        + " đã hoàn thành. Cảm ơn bạn đã mua sắm tại PhoneNest!",
                ThongBaoService.linkDonHang(ma)
        );

        return mapper.toDto(d);
    }
    private void ghiTrangThai(
            DonHang d,
            TrangThaiDonHang t,
            String gc,
            String maTk
    ) {
        LichSuTrangThaiDonHang l = new LichSuTrangThaiDonHang();

        l.setMaLichSu(IdGenerator.next("LSTT"));
        l.setDonHang(d);
        l.setTrangThai(t);
        l.setThoiGian(LocalDateTime.now());
        l.setGhiChu(gc);

        taiKhoanRepository.findById(maTk)
                .ifPresent(l::setNguoiCapNhat);

        lichSuRepository.save(l);
    }

    private DonHang timKhoa(String ma) {
        return donHangRepository.findByIdForUpdate(ma)
                .orElseThrow(() ->
                        new AppException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy đơn hàng"
                        )
                );
    }

    private void auditTrangThai(
            String ma,
            TrangThaiDonHang truoc,
            TrangThaiDonHang sau
    ) {
        Map<String, Object> duLieuTruoc = new LinkedHashMap<>();
        duLieuTruoc.put("trangThai", truoc.name());

        Map<String, Object> duLieuSau = new LinkedHashMap<>();
        duLieuSau.put("trangThai", sau.name());

        nhatKyHoatDongService.ghi(
                HanhDongAudit.CHANGE_ORDER_STATUS,
                LoaiDoiTuongAudit.ORDER,
                ma,
                duLieuTruoc,
                duLieuSau
        );
    }

    public GioHangDto muaLai(String maKh, String ma) {
        List<GioHangDto.Request> ds = donCuaToi(maKh, ma).getChiTiet().stream()
                .map(c -> new GioHangDto.Request(c.getPhienBan().getMaPhienBan(), c.getSoLuong())).toList();
        return gioHangService.sync(maKh, ds);
    }

    public DonHangDto suaDong(String ma, String id, int sl) {
        DonHang d = timKhoa(ma);
        if (d.getTrangThai() != TrangThaiDonHang.CHO_XAC_NHAN) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ sửa khi chờ xác nhận");
        }
        Map<String, Object> duLieuTruoc = snapshotDong(d);
        ChiTietDonHang c = d.getChiTiet().stream()
                .filter(x -> x.getPhienBan().getMaPhienBan().equals(id))
                .findFirst().orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Phiên bản không có trong đơn"));
        int delta = sl - c.getSoLuong();
        if (sl <= 0) {
            if (d.getChiTiet().size() == 1) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Đơn phải còn ít nhất một sản phẩm");
            }
            PhienBanSanPham pb = phienBanRepository.findByIdForUpdate(c.getPhienBan().getMaPhienBan())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Phiên bản không tồn tại"));
            capTon(pb, c.getSoLuong(), d, LoaiBienDongKho.DIEU_CHINH);
            d.getChiTiet().remove(c);
        } else {
            PhienBanSanPham pb = phienBanRepository.findByIdForUpdate(c.getPhienBan().getMaPhienBan())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Phiên bản không tồn tại"));
            if (delta > 0 && !PheDuyetBanHangPolicy.duocPhepBan(pb)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Không thể tăng số lượng do phiên bản chưa được phê duyệt bán hàng");
            }
            if (delta > pb.getSoLuongTon()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Không đủ tồn kho");
            }
            capTon(pb, -delta, d, LoaiBienDongKho.DIEU_CHINH);
            c.setSoLuong(sl);
        }
        recalc(d);
        nhatKyHoatDongService.ghi(HanhDongAudit.UPDATE_ORDER_ITEM, LoaiDoiTuongAudit.ORDER, ma, duLieuTruoc, snapshotDong(d));
        return mapper.toDto(d);
    }

    private Map<String, Object> snapshotDong(DonHang d) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        List<Map<String, Object>> ds = new ArrayList<>();
        for (ChiTietDonHang c : d.getChiTiet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("maChiTiet", c.getMaChiTiet());
            m.put("maPhienBan", c.getPhienBan().getMaPhienBan());
            m.put("soLuong", c.getSoLuong());
            m.put("donGia", c.getDonGia());
            ds.add(m);
        }
        duLieu.put("items", ds);
        return duLieu;
    }

    private void recalc(DonHang d) {
        BigDecimal tam = d.getChiTiet().stream()
                .map(c -> c.getDonGia().multiply(BigDecimal.valueOf(c.getSoLuong())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        d.setTamTinh(tam);
        if (d.getKhuyenMai() != null) {
            d.setGiamGia(khuyenMaiService.tinhGiam(d.getKhuyenMai(), tam));
        }
        BigDecimal giamGia = d.getGiamGia() != null ? d.getGiamGia() : BigDecimal.ZERO;
        BigDecimal giamShip = d.getGiamPhiVanChuyen() != null ? d.getGiamPhiVanChuyen() : BigDecimal.ZERO;
        BigDecimal tong = tam.subtract(giamGia).add(d.getPhiVanChuyen()).subtract(giamShip);
        if (tong.compareTo(BigDecimal.ZERO) < 0) {
            tong = BigDecimal.ZERO;
        }
        d.setTongThanhToan(tong);
        d.setNgayCapNhat(LocalDateTime.now());
    }

    private void taoThanhToan(DonHang d, PhuongThucTt pt, PaymentConfirmation paymentConfirmation) {
        ThanhToan t = new ThanhToan();
        t.setMaThanhToan(IdGenerator.next("TT"));
        t.setDonHang(d);
        t.setPhuongThuc(pt);
        t.setSoTien(d.getTongThanhToan());
        t.setTrangThai(paymentConfirmation == null
                ? TrangThaiThanhToan.CHO_THANH_TOAN
                : TrangThaiThanhToan.DA_THANH_TOAN);
        t.setNgayTao(LocalDateTime.now());
        if (paymentConfirmation != null) {
            t.setMaGiaoDich(paymentConfirmation.paymentReference());
            t.setNgayThanhToan(LocalDateTime.now());
            if (pt == PhuongThucTt.VI_DIEN_TU) {
                t.setMaGiaoDichMoMo(paymentConfirmation.providerTransactionId());
            } else {
                t.setMaGiaoDichNganHang(paymentConfirmation.providerTransactionId());
                t.setNgayTaoGiaoDichVnPay(paymentConfirmation.vnPayCreateDate());
            }
        }
        thanhToanRepository.save(t);
    }

    private void xoaDaMuaKhoiGio(String maKh, Map<String, Integer> items) {
        gioHangRepository.findByKhachHangMaKh(maKh).ifPresent(gioHang -> {
            Iterator<ChiTietGioHang> iterator = gioHang.getChiTiet().iterator();
            while (iterator.hasNext()) {
                ChiTietGioHang line = iterator.next();
                int purchased = items.getOrDefault(line.getPhienBan().getMaPhienBan(), 0);
                if (purchased <= 0) {
                    continue;
                }
                if (line.getSoLuong() <= purchased) {
                    iterator.remove();
                } else {
                    line.setSoLuong(line.getSoLuong() - purchased);
                }
            }
            gioHang.setNgayCapNhat(LocalDateTime.now());
        });
    }

    private void doiTon(DonHang d, int dau, LoaiBienDongKho loai) {
        doiTon(d, dau, loai, null);
    }

    private void doiTon(DonHang d, int dau, LoaiBienDongKho loai, Map<String, PhienBanSanPham> prelockedVariants) {
        Map<String, Integer> deltaMap = new TreeMap<>();
        for (ChiTietDonHang c : d.getChiTiet()) {
            String pbId = c.getPhienBan().getMaPhienBan();
            deltaMap.put(pbId, deltaMap.getOrDefault(pbId, 0) + (dau * c.getSoLuong()));
        }
        for (Map.Entry<String, Integer> entry : deltaMap.entrySet()) {
            PhienBanSanPham p = (prelockedVariants != null) ? prelockedVariants.get(entry.getKey()) : null;
            if (p == null) {
                p = phienBanRepository.findByIdForUpdate(entry.getKey())
                        .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản: " + entry.getKey()));
            }
            capTon(p, entry.getValue(), d, loai);
        }
    }

    private void capTon(PhienBanSanPham p, int delta, DonHang d, LoaiBienDongKho loai) {
        int truoc = p.getSoLuongTon();
        long sauLong = (long) truoc + delta;
        if (sauLong < 0) {
            throw new AppException(HttpStatus.CONFLICT, "Tồn kho không đủ để thực hiện thao tác");
        }
        if (sauLong > KhoService.MAX_TOTAL_STOCK) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Tồn kho vượt quá giới hạn tối đa cho phép");
        }
        int sau = (int) sauLong;
        p.setSoLuongTon(sau);

        BienDongKho b = new BienDongKho();
        b.setMaBienDong(IdGenerator.next("BDK"));
        b.setPhienBan(p);
        b.setLoaiBienDong(loai);
        b.setSoLuong(Math.abs(delta));
        b.setTonTruoc(truoc);
        b.setTonSau(sau);
        b.setDonHang(d);
        b.setThoiGian(LocalDateTime.now());
        bienDongRepository.save(b);
    }

    private void guiThongBaoSauCommit(
            String maKh, String maDonHang, BigDecimal tongTien,
            PhuongThucTt phuongThuc, boolean paymentConfirmed) {
        boolean choThanhToan = !paymentConfirmed
                && (phuongThuc == PhuongThucTt.VI_DIEN_TU || phuongThuc == PhuongThucTt.CHUYEN_KHOAN);
        String notification = paymentConfirmed
                ? "Thanh toán thành công, đơn hàng " + maDonHang + " đang chờ xác nhận"
                : choThanhToan
                ? "Đơn hàng " + maDonHang + " đang chờ thanh toán. Đơn sẽ được tiếp nhận sau khi thanh toán thành công."
                : "Đặt hàng thành công, đơn " + maDonHang + " đang chờ xác nhận";
        String emailSubject = paymentConfirmed || choThanhToan
                ? "Thanh toán đơn hàng " + maDonHang
                : "Xác nhận đơn hàng " + maDonHang;
        String emailBody = paymentConfirmed
                ? "PhoneNest đã nhận thanh toán thành công cho đơn hàng. Tổng thanh toán: "
                : choThanhToan
                ? "Vui lòng thanh toán đơn hàng tại PhoneNest. Đơn hàng chỉ được tạo sau khi cổng thanh toán xác nhận thành công. Tổng thanh toán: "
                : "Cảm ơn bạn đã đặt hàng tại PhoneNest. Tổng thanh toán: ";
        Runnable notificationTask = () -> {
            thongBaoService.gui(maKh, notification,
                    ThongBaoService.linkDonHang(maDonHang));
            taiKhoanRepository.findById(maKh).ifPresent(tk -> emailService.gui(
                    tk.getEmail(),
                    emailSubject,
                    emailBody + (tongTien != null ? tongTien.toBigInteger() : "0") + " VND."
            ));
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        notificationTask.run();
                    } catch (Exception e) {
                        // Email/notification failure must not impact committed order
                    }
                }
            });
        } else {
            notificationTask.run();
        }
    }

    private void ghiTrangThai(DonHang d, TrangThaiDonHang t, String gc) {
        LichSuTrangThaiDonHang l = new LichSuTrangThaiDonHang();
        l.setMaLichSu(IdGenerator.next("LSTT"));
        l.setDonHang(d);
        l.setTrangThai(t);
        l.setThoiGian(LocalDateTime.now());
        l.setGhiChu(gc);
        lichSuRepository.save(l);
    }

    private void congDiem(DonHang d) {
        KhachHang k = d.getKhachHang();
        BigDecimal thanhToanHang = d.getTamTinh().subtract(d.getGiamGia() != null ? d.getGiamGia() : BigDecimal.ZERO);
        if (thanhToanHang.compareTo(BigDecimal.ZERO) > 0) {
            k.setDiemTichLuy(k.getDiemTichLuy() + thanhToanHang.intValue() / DIEM_MOI_VND);
            k.setHangThanhVien(HangThanhVien.of(k.getDiemTichLuy()));
        }
    }

    private DonHang tim(String ma) {
        return donHangRepository.findById(ma).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
    }

    private DonHang donCuaToi(String maKh, String ma) {
        return donHangRepository.findById(ma).filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
    }

    private boolean coMa(String x) {
        return x != null && !x.isBlank();
    }

    private KhuyenMai dungVoucher(
            Map<String, KhuyenMai> lockedVouchers,
            String ma,
            LoaiKhuyenMai loai,
            BigDecimal tam,
            boolean moi,
            String maKh) {

        String clean = ma != null ? ma.trim().toUpperCase(Locale.ROOT) : "";
        KhuyenMai k = (lockedVouchers != null) ? lockedVouchers.get(clean) : null;
        if (k == null && lockedVouchers != null && ma != null) {
            k = lockedVouchers.get(ma.trim());
        }
        if (k == null) {
            k = khuyenMaiRepository.findByIdForUpdate(clean)
                    .orElseThrow(() -> new AppException(
                            HttpStatus.BAD_REQUEST,
                            "Mã khuyến mãi " + ma + " không hợp lệ hoặc không đúng loại"
                    ));
        }

        if (k.getLoai() != loai) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Mã khuyến mãi " + ma + " không hợp lệ hoặc không đúng loại"
            );
        }

        if (!k.isTrangThaiHoatDong()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Mã khuyến mãi " + ma + " đã ngừng hoạt động"
            );
        }

        String ly = khuyenMaiService.lyDoKhongDung(
                k,
                tam,
                moi,
                maKh
        );

        if (ly != null) {
            String errorCode = (ly.contains("hết lượt") || ly.contains("giới hạn")) ? "VOUCHER_USAGE_LIMIT_REACHED" : null;
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    k.getMaKm() + ": " + ly,
                    errorCode
            );
        }

        return k;
    }

    private void ghiNhanHanhViMuaHang(DonHang d) {
        if (hanhViNguoiDungService == null || d == null || d.getKhachHang() == null || d.getChiTiet() == null) {
            return;
        }
        String maKh = d.getKhachHang().getMaKh();
        String maDonHang = d.getMaDonHang();

        record ItemData(String maSp, String maPb, BigDecimal donGia) {}
        List<ItemData> items = new ArrayList<>();
        for (ChiTietDonHang c : d.getChiTiet()) {
            String maSp = (c.getPhienBan() != null && c.getPhienBan().getSanPham() != null)
                    ? c.getPhienBan().getSanPham().getMaSanPham()
                    : null;
            String maPb = c.getPhienBan() != null ? c.getPhienBan().getMaPhienBan() : null;
            items.add(new ItemData(maSp, maPb, c.getDonGia()));
        }

        Runnable trackingTask = () -> {
            for (ItemData item : items) {
                try {
                    hanhViNguoiDungService.ghiNhanPurchase(maKh, item.maSp(), item.maPb(), maDonHang, item.donGia());
                } catch (Exception e) {
                    log.warn("Không thể ghi nhận hành vi PURCHASE cho đơn hàng {}: {}", maDonHang, e.getMessage());
                }
            }
        };

        // Ghi tracking sau khi transaction đặt hàng đã commit thành công:
        // 1. Nếu đơn hàng rollback, afterCommit không chạy => không bao giờ lưu PURCHASE rác.
        // 2. Lỗi tracking xảy ra sau commit => không bao giờ làm rollback đơn hàng.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        trackingTask.run();
                    } catch (Exception e) {
                        log.warn("Lỗi khi chạy tracking task sau commit cho đơn hàng {}: {}", maDonHang, e.getMessage());
                    }
                }
            });
        } else {
            trackingTask.run();
        }
    }
}
