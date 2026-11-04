package nhom10.phonenest.service;

import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.ThanhToanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MomoPaymentServiceTest {

    private static final String PARTNER_CODE = "MOMO_TEST";
    private static final String ACCESS_KEY = "ACCESS_KEY_TEST";
    private static final String SECRET_KEY = "SECRET_KEY_TEST";

    @Mock
    private DonHangRepository donHangRepository;

    @Mock
    private ThanhToanRepository thanhToanRepository;

    @Mock
    private ThongBaoService thongBaoService;

    @InjectMocks
    private MomoPaymentService momoPaymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(momoPaymentService, "partnerCode", PARTNER_CODE);
        ReflectionTestUtils.setField(momoPaymentService, "accessKey", ACCESS_KEY);
        ReflectionTestUtils.setField(momoPaymentService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(momoPaymentService, "endpoint", "https://test-payment.momo.vn/v2/gateway/api/create");
        ReflectionTestUtils.setField(momoPaymentService, "ipnUrl", "http://localhost:8080/api/payment/momo/ipn");
        ReflectionTestUtils.setField(momoPaymentService, "redirectUrl", "http://localhost:3000/orders");
    }

    private DonHang createMockOrder(String maDonHang, BigDecimal amount, TrangThaiDonHang trangThai) {
        DonHang order = new DonHang();
        order.setMaDonHang(maDonHang);
        order.setTongThanhToan(amount);
        order.setTrangThai(trangThai);
        KhachHang kh = new KhachHang();
        kh.setMaKh("KH001");
        kh.setHoTen("Nguyen Van A");
        order.setKhachHang(kh);
        return order;
    }

    private ThanhToan createMockPayment(String maThanhToan, String maGiaoDich, DonHang order, TrangThaiThanhToan trangThai) {
        ThanhToan payment = new ThanhToan();
        payment.setMaThanhToan(maThanhToan);
        payment.setMaGiaoDich(maGiaoDich);
        payment.setDonHang(order);
        payment.setPhuongThuc(PhuongThucTt.VI_DIEN_TU);
        payment.setSoTien(order.getTongThanhToan());
        payment.setTrangThai(trangThai);
        payment.setNgayTao(LocalDateTime.now().minusMinutes(5));
        return payment;
    }

    private Map<String, Object> createIpnPayload(String orderId, BigDecimal amount, String resultCode, String message, String transId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerCode", PARTNER_CODE);
        payload.put("orderId", orderId);
        payload.put("requestId", orderId);
        payload.put("amount", amount.toBigInteger().toString());
        payload.put("orderInfo", "Thanh toan don hang " + orderId);
        payload.put("orderType", "momo_wallet");
        payload.put("transId", transId != null ? transId : "202611040001");
        payload.put("resultCode", resultCode);
        payload.put("message", message);
        payload.put("payType", "qr");
        payload.put("responseTime", "1762250000000");
        payload.put("extraData", "");

        String raw = "accessKey=" + ACCESS_KEY
                + "&amount=" + payload.get("amount")
                + "&extraData=" + payload.get("extraData")
                + "&message=" + payload.get("message")
                + "&orderId=" + payload.get("orderId")
                + "&orderInfo=" + payload.get("orderInfo")
                + "&orderType=" + payload.get("orderType")
                + "&partnerCode=" + payload.get("partnerCode")
                + "&payType=" + payload.get("payType")
                + "&requestId=" + payload.get("requestId")
                + "&responseTime=" + payload.get("responseTime")
                + "&resultCode=" + payload.get("resultCode")
                + "&transId=" + payload.get("transId");

        payload.put("signature", hmacSha256(raw, SECRET_KEY));
        return payload;
    }

    private Map<String, Object> createIpnPayload(String orderId, BigDecimal amount, String resultCode, String message) {
        return createIpnPayload(orderId, amount, resultCode, message, "202611040001");
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder sb = new StringBuilder();
            for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // KỊCH BẢN 1: IPN THÀNH CÔNG TRONG KỊCH BẢN THÔNG THƯỜNG
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: IPN thành công - Cập nhật DA_THANH_TOAN, transId, giữ nguyên trạng thái đơn hàng và gửi thông báo cho khách")
    void scenario1_successfulIpnStandardFlow() {
        DonHang order = createMockOrder("DH001", new BigDecimal("5000000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan payment = createMockPayment("TT001", "GD001", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        when(thanhToanRepository.findByMaGiaoDich("GD001")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH001")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD001")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH001")).thenReturn(List.of(payment));

        Map<String, Object> payload = createIpnPayload("GD001", new BigDecimal("5000000"), "0", "Successful.", "MOMO_TRANS_001");
        momoPaymentService.processIpn(payload);

        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN);
        assertThat(payment.getMaGiaoDichMoMo()).isEqualTo("MOMO_TRANS_001");
        assertThat(payment.getNgayThanhToan()).isNotNull();
        // Không tự ý nhảy sang DA_XAC_NHAN để chờ admin duyệt
        assertThat(order.getTrangThai()).isEqualTo(TrangThaiDonHang.CHO_XAC_NHAN);

        verify(thanhToanRepository).save(payment);
        verify(thongBaoService).gui(eq("KH001"), contains("DH001"));
        verify(thongBaoService, never()).guiQuanTri(anyString());
    }

    // =========================================================================
    // KỊCH BẢN 2: IPN THẤT BẠI TRONG KỊCH BẢN THÔNG THƯỜNG
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: IPN thất bại - Cập nhật THAT_BAI, đơn hàng không bị đổi trạng thái sai")
    void scenario2_failedIpnStandardFlow() {
        DonHang order = createMockOrder("DH002", new BigDecimal("3500000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan payment = createMockPayment("TT002", "GD002", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        when(thanhToanRepository.findByMaGiaoDich("GD002")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH002")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD002")).thenReturn(Optional.of(payment));

        Map<String, Object> payload = createIpnPayload("GD002", new BigDecimal("3500000"), "1006", "Transaction denied by user.");
        momoPaymentService.processIpn(payload);

        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.THAT_BAI);
        assertThat(order.getTrangThai()).isEqualTo(TrangThaiDonHang.CHO_XAC_NHAN);

        verify(thanhToanRepository).save(payment);
        verify(thongBaoService, never()).gui(anyString(), anyString());
    }

    // =========================================================================
    // KỊCH BẢN 3: IPN THÀNH CÔNG ĐẾN SAU KHI ĐƠN HÀNG ĐÃ BỊ HỦY
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: IPN thành công cho đơn đã hủy - Đơn giữ DA_HUY, thanh toán CAN_HOAN_TIEN, báo admin")
    void scenario3_successfulIpnAfterOrderCancelled() {
        DonHang order = createMockOrder("DH003", new BigDecimal("8000000"), TrangThaiDonHang.DA_HUY);
        ThanhToan payment = createMockPayment("TT003", "GD003", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        when(thanhToanRepository.findByMaGiaoDich("GD003")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH003")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD003")).thenReturn(Optional.of(payment));

        Map<String, Object> payload = createIpnPayload("GD003", new BigDecimal("8000000"), "0", "Successful.", "MOMO_TRANS_CANCELLED");
        momoPaymentService.processIpn(payload);

        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.CAN_HOAN_TIEN);
        assertThat(payment.getMaGiaoDichMoMo()).isEqualTo("MOMO_TRANS_CANCELLED");
        assertThat(payment.getNgayThanhToan()).isNotNull();
        assertThat(order.getTrangThai()).isEqualTo(TrangThaiDonHang.DA_HUY);

        verify(thanhToanRepository).save(payment);
        verify(thongBaoService).guiQuanTri(contains("DH003"));
        verify(thongBaoService).gui(eq("KH001"), contains("DH003"));
    }

    // =========================================================================
    // KỊCH BẢN 4: IPN THÀNH CÔNG GỬI LẶP (IDEMPOTENCY)
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: IPN thành công lặp lại - Idempotent, không ghi trùng, không gửi thông báo lặp")
    void scenario4_duplicateSuccessfulIpnIdempotency() {
        DonHang order = createMockOrder("DH004", new BigDecimal("2000000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan payment = createMockPayment("TT004", "GD004", order, TrangThaiThanhToan.DA_THANH_TOAN);
        LocalDateTime initialPaidTime = LocalDateTime.now().minusMinutes(2);
        payment.setNgayThanhToan(initialPaidTime);

        when(thanhToanRepository.findByMaGiaoDich("GD004")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH004")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD004")).thenReturn(Optional.of(payment));

        Map<String, Object> payload = createIpnPayload("GD004", new BigDecimal("2000000"), "0", "Successful retry.");
        momoPaymentService.processIpn(payload);

        // Trạng thái giữ nguyên DA_THANH_TOAN
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN);
        assertThat(payment.getNgayThanhToan()).isEqualTo(initialPaidTime);

        // Không gọi save và không gửi thông báo trùng
        verify(thanhToanRepository, never()).save(any());
        verify(thongBaoService, never()).gui(anyString(), anyString());
        verify(thongBaoService, never()).guiQuanTri(anyString());
    }

    // =========================================================================
    // KỊCH BẢN 5: IPN THẤT BẠI ĐẾN SAU IPN THÀNH CÔNG
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: IPN thất bại đến muộn sau khi đã thanh toán thành công - Không được ghi đè")
    void scenario5_lateFailedIpnDoesNotOverwriteSuccess() {
        DonHang order = createMockOrder("DH005", new BigDecimal("4500000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan payment = createMockPayment("TT005", "GD005", order, TrangThaiThanhToan.DA_THANH_TOAN);
        payment.setNgayThanhToan(LocalDateTime.now().minusMinutes(10));

        when(thanhToanRepository.findByMaGiaoDich("GD005")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH005")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD005")).thenReturn(Optional.of(payment));

        Map<String, Object> payload = createIpnPayload("GD005", new BigDecimal("4500000"), "1006", "Late timeout failure.");
        momoPaymentService.processIpn(payload);

        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN);
        verify(thanhToanRepository, never()).save(any());
    }

    // =========================================================================
    // KỊCH BẢN 6: IPN CÓ CHỮ KÝ KHÔNG HỢP LỆ
    // =========================================================================
    @Test
    @DisplayName("Scenario 6: IPN có chữ ký không hợp lệ - Bị từ chối, không thay đổi dữ liệu")
    void scenario6_invalidSignatureRejected() {
        Map<String, Object> payload = createIpnPayload("GD006", new BigDecimal("1000000"), "0", "Successful.");
        payload.put("signature", "invalid_forged_signature_1234567890");

        assertThatThrownBy(() -> momoPaymentService.processIpn(payload))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Chữ ký MoMo không hợp lệ");

        verify(thanhToanRepository, never()).findByMaGiaoDich(anyString());
        verify(thanhToanRepository, never()).findByMaGiaoDichForUpdate(anyString());
        verify(thanhToanRepository, never()).save(any());
    }

    // =========================================================================
    // KỊCH BẢN 7: IPN SAI LỆCH SỐ TIỀN SO VỚI ĐƠN HÀNG
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: IPN sai lệch số tiền - Bị từ chối với 400 Bad Request")
    void scenario7_mismatchedAmountRejected() {
        DonHang order = createMockOrder("DH007", new BigDecimal("5000000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan payment = createMockPayment("TT007", "GD007", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        when(thanhToanRepository.findByMaGiaoDich("GD007")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH007")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD007")).thenReturn(Optional.of(payment));

        // IPN báo số tiền 100000 thay vì 5000000
        Map<String, Object> payload = createIpnPayload("GD007", new BigDecimal("100000"), "0", "Tampered amount.");

        assertThatThrownBy(() -> momoPaymentService.processIpn(payload))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Số tiền thanh toán MoMo không hợp lệ");

        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.CHO_THANH_TOAN);
        verify(thanhToanRepository, never()).save(payment);
    }

    // =========================================================================
    // KỊCH BẢN 8: IPN CHO ĐƠN HÀNG / GIAO DỊCH KHÔNG TỒN TẠI
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: IPN cho mã giao dịch không tồn tại - Trả về 404 NOT_FOUND an toàn")
    void scenario8_nonExistentTransaction() {
        when(thanhToanRepository.findByMaGiaoDich("GD_NON_EXISTENT")).thenReturn(Optional.empty());

        Map<String, Object> payload = createIpnPayload("GD_NON_EXISTENT", new BigDecimal("5000000"), "0", "Success.");

        assertThatThrownBy(() -> momoPaymentService.processIpn(payload))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Không tìm thấy giao dịch MoMo");
    }

    // =========================================================================
    // KỊCH BẢN 9: HAI REQUEST IPN ĐẾN ĐỒNG THỜI CHO CÙNG MỘT ĐƠN
    // =========================================================================
    @Test
    @DisplayName("Scenario 9: Hai request IPN đồng thời - Thực thi an toàn, kết thúc nhất quán ở DA_THANH_TOAN")
    void scenario9_concurrentIpnRequestsConsistent() throws Exception {
        DonHang order = createMockOrder("DH009", new BigDecimal("6000000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan payment = createMockPayment("TT009", "GD009", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        when(thanhToanRepository.findByMaGiaoDich("GD009")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH009")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD009")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH009")).thenReturn(List.of(payment));

        Map<String, Object> payload = createIpnPayload("GD009", new BigDecimal("6000000"), "0", "Successful.");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    momoPaymentService.processIpn(payload);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(errors).isEmpty();
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN);
    }

    // =========================================================================
    // KỊCH BẢN 10: LỖI XẢY RA TRONG QUÁ TRÌNH GỬI THÔNG BÁO
    // =========================================================================
    @Test
    @DisplayName("Scenario 10: Lỗi gửi thông báo - Không làm rollback giao dịch thanh toán hợp lệ")
    void scenario10_notificationFailureDoesNotRollbackPayment() {
        DonHang order = createMockOrder("DH010", new BigDecimal("1500000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan payment = createMockPayment("TT010", "GD010", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        when(thanhToanRepository.findByMaGiaoDich("GD010")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH010")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD010")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH010")).thenReturn(List.of(payment));

        // Giả lập lỗi gửi thông báo (ví dụ lỗi mạng, DB thông báo quá tải)
        doThrow(new RuntimeException("Notification service temporary failure"))
                .when(thongBaoService).gui(anyString(), anyString());

        Map<String, Object> payload = createIpnPayload("GD010", new BigDecimal("1500000"), "0", "Successful.");

        // Không ném ngoại lệ ra ngoài
        momoPaymentService.processIpn(payload);

        // Trạng thái thanh toán vẫn thành công và được lưu vào DB
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN);
        verify(thanhToanRepository).save(payment);
    }

    // =========================================================================
    // KỊCH BẢN 11: ĐƠN HÀNG ĐÃ THANH TOÁN THÀNH CÔNG / HỦY KHÔNG CHO TẠO LẠI THANH TOÁN
    // =========================================================================
    @Test
    @DisplayName("Scenario 11: Đơn hàng đã thanh toán hoặc đã hủy - Từ chối tạo yêu cầu thanh toán mới")
    void scenario11_preventCreatingPaymentForPaidOrCancelledOrder() {
        DonHang paidOrder = createMockOrder("DH011_PAID", new BigDecimal("2500000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan paidPayment = createMockPayment("TT011_PAID", "GD011_PAID", paidOrder, TrangThaiThanhToan.DA_THANH_TOAN);

        when(donHangRepository.findByIdForUpdate("DH011_PAID")).thenReturn(Optional.of(paidOrder));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH011_PAID"))
                .thenReturn(List.of(paidPayment));

        // Trường hợp 1: Đã thanh toán -> 409 Conflict
        assertThatThrownBy(() -> momoPaymentService.create("KH001", "DH011_PAID"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Đơn hàng đã được thanh toán");

        // Trường hợp 2: Đơn đã bị hủy -> 400 Bad Request
        DonHang cancelledOrder = createMockOrder("DH011_CANCELLED", new BigDecimal("2500000"), TrangThaiDonHang.DA_HUY);
        when(donHangRepository.findByIdForUpdate("DH011_CANCELLED")).thenReturn(Optional.of(cancelledOrder));

        assertThatThrownBy(() -> momoPaymentService.create("KH001", "DH011_CANCELLED"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Đơn hàng đã bị hủy");
    }

    // =========================================================================
    // KỊCH BẢN 12: KHÁCH HÀNG HỦY ĐƠN TRƯỚC, SAU ĐÓ MOMO GỬI IPN THÀNH CÔNG
    // =========================================================================
    @Test
    @DisplayName("Scenario 12: Khách hủy đơn trước khi MoMo gửi IPN - Trạng thái cuối cùng là DA_HUY + CAN_HOAN_TIEN nhất quán")
    void scenario12_cancelOrderBeforeIpnArrives() {
        DonHang order = createMockOrder("DH012", new BigDecimal("12000000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan payment = createMockPayment("TT012", "GD012", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        // Bước 1: Khách hàng hủy đơn (giả lập trạng thái chuyển thành DA_HUY)
        order.setTrangThai(TrangThaiDonHang.DA_HUY);

        // Bước 2: MoMo gửi IPN thành công muộn
        when(thanhToanRepository.findByMaGiaoDich("GD012")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH012")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD012")).thenReturn(Optional.of(payment));

        Map<String, Object> payload = createIpnPayload("GD012", new BigDecimal("12000000"), "0", "Late payment success.");
        momoPaymentService.processIpn(payload);

        // Bước 3: Kiểm tra trạng thái cuối cùng
        assertThat(order.getTrangThai())
                .as("Đơn hàng vẫn giữ nguyên trạng thái DA_HUY, không bị khôi phục sai")
                .isEqualTo(TrangThaiDonHang.DA_HUY);

        assertThat(payment.getTrangThai())
                .as("Thanh toán chuyển sang CAN_HOAN_TIEN để admin đối soát")
                .isEqualTo(TrangThaiThanhToan.CAN_HOAN_TIEN);

        verify(thongBaoService).guiQuanTri(contains("DH012"));
    }

    // =========================================================================
    // KỊCH BẢN 13: XÁC MINH THỨ TỰ KHÓA ĐƠN HÀNG TRƯỚC, THANH TOÁN SAU (CHỐNG DEADLOCK)
    // =========================================================================
    @Test
    @DisplayName("Scenario 13: Thứ tự khóa trong processIpn - Bắt buộc khóa DonHang trước rồi mới khóa ThanhToan")
    void scenario13_strictLockOrderDonHangThenThanhToan() {
        DonHang order = createMockOrder("DH013", new BigDecimal("9900000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan payment = createMockPayment("TT013", "GD013", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        when(thanhToanRepository.findByMaGiaoDich("GD013")).thenReturn(Optional.of(payment));
        when(donHangRepository.findByIdForUpdate("DH013")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD013")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH013")).thenReturn(List.of(payment));

        Map<String, Object> payload = createIpnPayload("GD013", new BigDecimal("9900000"), "0", "Success.");
        momoPaymentService.processIpn(payload);

        // Kiểm tra thứ tự gọi khóa nghiêm ngặt bằng InOrder
        InOrder inOrder = inOrder(donHangRepository, thanhToanRepository);
        inOrder.verify(donHangRepository).findByIdForUpdate("DH013");
        inOrder.verify(thanhToanRepository).findByMaGiaoDichForUpdate("GD013");
    }

    // =========================================================================
    // KỊCH BẢN 14A: IPN MUỘN TỪ LẦN THỬ A ĐẾN SAU KHI KHÁCH ĐÃ BẤM THANH TOÁN LẠI (LẦN B)
    // =========================================================================
    @Test
    @DisplayName("Scenario 14A: IPN thành công từ lần thử A đến muộn sau khi khách đã bắt đầu lần B - Không bị 404, ghi nhận đúng tiền đã thu")
    void scenario14A_lateIpnFromAttemptAAfterRetryInitiated() {
        DonHang order = createMockOrder("DH014A", new BigDecimal("5000000"), TrangThaiDonHang.CHO_XAC_NHAN);
        // Lần thử A (mã GD: GD_A)
        ThanhToan paymentA = createMockPayment("TT_A", "GD_A", order, TrangThaiThanhToan.CHO_THANH_TOAN);
        // Lần thử B (mã GD: GD_B) được tạo khi khách bấm thanh toán lại
        ThanhToan paymentB = createMockPayment("TT_B", "GD_B", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        // Cả 2 bản ghi cùng tồn tại trong DB, liên kết với đơn hàng
        when(thanhToanRepository.findByMaGiaoDich("GD_A")).thenReturn(Optional.of(paymentA));
        when(donHangRepository.findByIdForUpdate("DH014A")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD_A")).thenReturn(Optional.of(paymentA));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH014A")).thenReturn(List.of(paymentB, paymentA));

        // IPN của lần A gửi tới
        Map<String, Object> payloadA = createIpnPayload("GD_A", new BigDecimal("5000000"), "0", "Attempt A late success.", "TRANS_A_999");
        momoPaymentService.processIpn(payloadA);

        // KHÔNG BỊ 404! Ghi nhận thành công lần A
        assertThat(paymentA.getTrangThai()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN);
        assertThat(paymentA.getMaGiaoDichMoMo()).isEqualTo("TRANS_A_999");
        verify(thanhToanRepository).save(paymentA);
        verify(thongBaoService).gui(eq("KH001"), contains("DH014A"));
    }

    // =========================================================================
    // KỊCH BẢN 14B: PHÁT HIỆN THANH TOÁN TRÙNG KHI CẢ 2 LẦN A VÀ B CÙNG THÀNH CÔNG
    // =========================================================================
    @Test
    @DisplayName("Scenario 14B: Khách hàng bị trừ tiền 2 lần (cả A và B) - Phát hiện thanh toán trùng, đánh dấu CAN_HOAN_TIEN và báo Admin đối soát")
    void scenario14B_doublePaymentDetectedWhenBothAttemptsSucceed() {
        DonHang order = createMockOrder("DH014B", new BigDecimal("5000000"), TrangThaiDonHang.CHO_XAC_NHAN);
        // Lần B đã được xác nhận thanh toán thành công trước đó
        ThanhToan paymentB = createMockPayment("TT_B", "GD_B", order, TrangThaiThanhToan.DA_THANH_TOAN);
        paymentB.setMaGiaoDichMoMo("TRANS_B_888");
        // Lần A bây giờ mới nhận được IPN muộn từ MoMo
        ThanhToan paymentA = createMockPayment("TT_A", "GD_A", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        when(thanhToanRepository.findByMaGiaoDich("GD_A")).thenReturn(Optional.of(paymentA));
        when(donHangRepository.findByIdForUpdate("DH014B")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("GD_A")).thenReturn(Optional.of(paymentA));
        // DB trả về cả 2 bản ghi, trong đó TT_B đã DA_THANH_TOAN
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH014B")).thenReturn(List.of(paymentB, paymentA));

        // MoMo gửi IPN thành công cho lần A (khách đã bị trừ tiền 2 lần trên ví MoMo!)
        Map<String, Object> payloadA = createIpnPayload("GD_A", new BigDecimal("5000000"), "0", "Attempt A late success.", "TRANS_A_777");
        momoPaymentService.processIpn(payloadA);

        // BẮT BUỘC: Lần A phải được đánh dấu CAN_HOAN_TIEN (không được ghi đè hay mất tiền của khách!)
        assertThat(paymentA.getTrangThai()).isEqualTo(TrangThaiThanhToan.CAN_HOAN_TIEN);
        assertThat(paymentA.getMaGiaoDichMoMo()).isEqualTo("TRANS_A_777");
        verify(thanhToanRepository).save(paymentA);

        // Gửi thông báo khẩn cấp cho Quản trị viên và khách hàng để hoàn tiền thừa
        verify(thongBaoService).guiQuanTri(contains("CẢNH BÁO THANH TOÁN TRÙNG"));
        verify(thongBaoService).gui(eq("KH001"), contains("DH014B"));
    }

    // =========================================================================
    // KỊCH BẢN 14C: TÌM KIẾM TRẠNG THÁI ƯU TIÊN GIAO DỊCH ĐÃ THÀNH CÔNG NẾU CÓ NHIỀU LẦN THỬ
    // =========================================================================
    @Test
    @DisplayName("Scenario 14C: status() ưu tiên giao dịch đã thanh toán thành công khi có nhiều lần thử cùng tồn tại")
    void scenario14C_statusPrefersPaidAttemptOverPendingAttempt() {
        DonHang order = createMockOrder("DH014C", new BigDecimal("3000000"), TrangThaiDonHang.CHO_XAC_NHAN);
        ThanhToan oldPaidPayment = createMockPayment("TT_OLD", "GD_OLD", order, TrangThaiThanhToan.DA_THANH_TOAN);
        ThanhToan newPendingPayment = createMockPayment("TT_NEW", "GD_NEW", order, TrangThaiThanhToan.CHO_THANH_TOAN);

        when(donHangRepository.findById("DH014C")).thenReturn(Optional.of(order));
        // Lần thử mới TT_NEW được tạo sau nên đứng đầu danh sách theo ngày tạo DESC
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH014C"))
                .thenReturn(List.of(newPendingPayment, oldPaidPayment));

        nhom10.phonenest.dto.PaymentStatusDto statusDto = momoPaymentService.status("KH001", "DH014C");

        // Hệ thống nhận diện chính xác trạng thái đơn hàng là DA_THANH_TOAN theo lần thử cũ thành công
        assertThat(statusDto.status()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN.name());
        assertThat(statusDto.transactionId()).isEqualTo("GD_OLD");
    }
}
