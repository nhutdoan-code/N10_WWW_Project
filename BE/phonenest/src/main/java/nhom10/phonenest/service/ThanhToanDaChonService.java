package nhom10.phonenest.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nhom10.phonenest.dto.CheckoutDto;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.ChiTietGioHang;
import nhom10.phonenest.entity.GioHang;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.enums.IdempotencyStatus;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.repository.CheckoutIdempotencyRepository;
import nhom10.phonenest.repository.GioHangRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Thanh toán một phần giỏ hàng (khách tick chọn sản phẩm ở trang giỏ hàng).
 * <p>
 * Không viết lại luồng đặt hàng: trong CÙNG một transaction, tạm bỏ các dòng không được chọn khỏi giỏ,
 * gọi luồng đặt hàng hiện có (idempotency, khóa tồn kho, voucher, phê duyệt bán giữ nguyên),
 * rồi trả các dòng chưa chọn về giỏ. Lỗi ở bất kỳ bước nào sẽ rollback toàn bộ nên giỏ không bị mất dòng.
 * Xem trước (preview) dùng cùng cách nhưng luôn rollback, không ghi gì vào CSDL.
 */
@Service
public class ThanhToanDaChonService {

    private final GioHangRepository gioHangRepository;
    private final CheckoutIdempotencyService checkoutIdempotencyService;
    private final ThanhToanService thanhToanService;
    private final CheckoutIdempotencyRepository checkoutIdempotencyRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ThanhToanDaChonService(GioHangRepository gioHangRepository,
                                  CheckoutIdempotencyService checkoutIdempotencyService,
                                  ThanhToanService thanhToanService,
                                  CheckoutIdempotencyRepository checkoutIdempotencyRepository) {
        this.gioHangRepository = gioHangRepository;
        this.checkoutIdempotencyService = checkoutIdempotencyService;
        this.thanhToanService = thanhToanService;
        this.checkoutIdempotencyRepository = checkoutIdempotencyRepository;
    }

    private record DongGiuLai(String maPhienBan, int soLuong) {
    }

    @Transactional
    public DonHangDto datHang(String maKh, String idempotencyKey, DonHangDto.Checkout req, List<String> chon) {
        if (req.maSanPham() != null && !req.maSanPham().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không dùng chọn sản phẩm cùng với Mua ngay");
        }
        // Gửi lại cùng Idempotency-Key sau khi đã đặt thành công: trả lại đơn cũ, không đụng tới giỏ hàng.
        if (idempotencyKey != null && !idempotencyKey.isBlank()
                && checkoutIdempotencyRepository.findByUserIdAndIdempotencyKey(maKh, idempotencyKey.trim())
                .map(r -> r.getStatus() == IdempotencyStatus.SUCCESS).orElse(false)) {
            return checkoutIdempotencyService.executeIdempotentCheckout(maKh, idempotencyKey, req);
        }
        List<DongGiuLai> giuLai = tachDongKhongChon(maKh, chon);
        DonHangDto don = checkoutIdempotencyService.executeIdempotentCheckout(maKh, idempotencyKey, req);
        traLaiGio(maKh, giuLai);
        return don;
    }

    @Transactional
    public CheckoutDto xemTruoc(String maKh, String maDiaChi, List<String> chon) {
        return xemTruoc(maKh, maDiaChi, chon, null);
    }

    @Transactional
    public CheckoutDto xemTruoc(String maKh, String maDiaChi, List<String> chon, PhuongThucTt phuongThuc) {
        tachDongKhongChon(maKh, chon);
        CheckoutDto dto = thanhToanService.xemTruoc(maKh, maDiaChi, null, null, phuongThuc);
        // Chỉ xem trước: hủy mọi thay đổi tạm thời trên giỏ.
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return dto;
    }

    /** Bỏ tạm các dòng không được chọn khỏi giỏ (đã ghi xuống CSDL trong transaction hiện tại). */
    private List<DongGiuLai> tachDongKhongChon(String maKh, List<String> chon) {
        Set<String> daChon = new LinkedHashSet<>();
        if (chon != null) {
            chon.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(daChon::add);
        }
        if (daChon.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất một sản phẩm để thanh toán");
        }
        GioHang g = gioHangRepository.findByKhachHangMaKh(maKh)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giỏ hàng"));

        Set<String> trongGio = new LinkedHashSet<>();
        g.getChiTiet().forEach(c -> trongGio.add(c.getPhienBan().getMaPhienBan()));
        if (!trongGio.containsAll(daChon)) {
            throw new AppException(HttpStatus.CONFLICT, "Giỏ hàng đã thay đổi. Vui lòng tải lại giỏ hàng và chọn lại sản phẩm.");
        }

        List<ChiTietGioHang> boRa = new ArrayList<>();
        List<DongGiuLai> giuLai = new ArrayList<>();
        for (ChiTietGioHang c : g.getChiTiet()) {
            if (!daChon.contains(c.getPhienBan().getMaPhienBan())) {
                boRa.add(c);
                giuLai.add(new DongGiuLai(c.getPhienBan().getMaPhienBan(), c.getSoLuong()));
            }
        }
        if (!boRa.isEmpty()) {
            g.getChiTiet().removeAll(boRa);
            entityManager.flush();
        }
        return giuLai;
    }

    /** Thêm lại các dòng chưa chọn vào giỏ sau khi đặt hàng xong. */
    private void traLaiGio(String maKh, List<DongGiuLai> giuLai) {
        if (giuLai.isEmpty()) return;
        GioHang g = gioHangRepository.findByKhachHangMaKh(maKh)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giỏ hàng"));
        for (DongGiuLai d : giuLai) {
            // Lấy lại phiên bản đang được quản lý trong phiên hiện tại (luồng đặt hàng có thể làm mới/tách thực thể).
            PhienBanSanPham pb = entityManager.find(PhienBanSanPham.class, d.maPhienBan());
            if (pb == null) continue;
            ChiTietGioHang c = new ChiTietGioHang();
            c.setId(new ChiTietGioHang.Id(g.getMaGioHang(), pb.getMaPhienBan()));
            c.setGioHang(g);
            c.setPhienBan(pb);
            c.setSoLuong(d.soLuong());
            g.getChiTiet().add(c);
        }
        g.setNgayCapNhat(LocalDateTime.now());
    }
}
